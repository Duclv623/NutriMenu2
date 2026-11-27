package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.client.MealAccountClient.MealAccount;
import com.example.mealoptimizerservice.dao.repository.MealPlanRepository;
import com.example.mealoptimizerservice.dto.request.*;
import com.example.mealoptimizerservice.dto.response.*;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.exception.OptimizerBusinessException;
import com.example.mealoptimizerservice.mapper.MealPlanMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.springframework.http.HttpStatus.*;
import com.example.mealoptimizerservice.entity.enums.SolverRunStatus;

@Service
@Transactional
@RequiredArgsConstructor
public class MealPlanToolsService {
    private final PersonalMealPlanService personal;
    private final MealOptimizerService optimizer;
    private final CheckedMealChangeService changes;
    private final MealPlanRepository plans;
    private final MealPlanMapper mapper;
    private final DiabeticClinicalTargetCalculator calculator;
    private final Validator validator;

    private MealAccount account(String token, long version) {
        var account = personal.accountForUpdate(token);
        personal.eligible(account.profile(), version);
        return account;
    }

    private void validate(Object request) {
        var errors = validator.validate(request);
        if (!errors.isEmpty()) throw new OptimizerBusinessException(BAD_REQUEST, errors.iterator().next().getMessage());
    }

    private void requireAvailableDates(long user, LocalDate start, int days) {
        for (int day = 0; day < days; day++) {
            if (plans.findByUserIdAndPlanDate(user, start.plusDays(day)).isPresent())
                throw new OptimizerBusinessException(CONFLICT, "Ngày đã có thực đơn: " + start.plusDays(day));
        }
    }

    private void configure(String token, MealAccount account, GenerateDailyMealRequest daily) {
        var profile = account.profile();
        daily.setSourceProfileVersion(profile.version());
        var tags = personal.allergyTags(profile);
        if (daily.getPatientContraindications() != null) tags.addAll(daily.getPatientContraindications());
        daily.setPatientContraindications(tags);
        var excluded = personal.exclusions(token, profile);
        if (daily.getExcludedDishIds() != null) excluded.addAll(daily.getExcludedDishIds());
        daily.setExcludedDishIds(excluded);
        validate(daily);
    }

    public MealPlanResponse daily(String token, long version, GenerateDailyMealRequest daily) {
        var account = account(token, version);
        configure(token, account, daily);
        requireAvailableDates(account.serviceUserId(), daily.getPlanDate(), 1);
        var result = optimizer.generateOptimalDailyMealPlan(account.serviceUserId(), daily);
        requireSaved(result);
        account(token, version);
        return result;
    }

    public List<MealPlanResponse> weekly(String token, long version, GenerateWeeklyMealRequest weekly) {
        validate(weekly);
        var account = account(token, version);
        var daily = weekly.getDailyTargetTemplate();
        daily.setPlanDate(weekly.getStartDate());
        configure(token, account, daily);
        requireAvailableDates(account.serviceUserId(), weekly.getStartDate(), 7);
        var results = optimizer.generateWeeklyMealPlans(account.serviceUserId(), weekly);
        if (results.size() != 7) throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Chưa tạo đủ bảy ngày; chưa lưu tuần này.");
        for (int day = 0; day < 7; day++) {
            requireSaved(results.get(day));
            if (!weekly.getStartDate().plusDays(day).equals(results.get(day).getPlanDate()))
                throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Ngày thực đơn trả về không khớp; chưa lưu tuần này.");
        }
        account(token, version);
        return results;
    }

    public Map<String,Object> fromProfile(String token, long version, PatientProfileMealRequest input) {
        var account = account(token, version);
        var profile = account.profile();
        if (!Set.of("male","female").contains(profile.gender()))
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Cần giới tính nam hoặc nữ để tính mục tiêu.");
        input.setGender(Gender.valueOf(profile.gender().toUpperCase(Locale.ROOT)));
        input.setHeightMeters(profile.height().movePointLeft(2));
        input.setActualWeightKg(profile.weight());
        input.setActivityLevel(switch (profile.activityLevel()) {
            case "sedentary", "light" -> PhysicalActivityLevel.LIGHT;
            case "moderate" -> PhysicalActivityLevel.MODERATE;
            case "active", "very_active" -> PhysicalActivityLevel.HEAVY;
            default -> throw new OptimizerBusinessException(BAD_REQUEST, "Mức vận động không hợp lệ.");
        });
        validate(input);
        var targets = calculator.calculateTargets(input);
        var daily = calculator.toDailyMealRequest(input, targets);
        configure(token, account, daily);
        requireAvailableDates(account.serviceUserId(), daily.getPlanDate(), 1);
        return Map.of("computedClinicalTargets", targets, "optimalMealPlan", optimizer.generateOptimalDailyMealPlan(account.serviceUserId(), daily));
    }

    public InfeasibilityDiagnosisResponse feasibility(String token, long version, GenerateDailyMealRequest daily) {
        var account = account(token, version);
        configure(token, account, daily);
        return optimizer.explainInfeasibility(account.serviceUserId(), daily);
    }

    private void requireSaved(MealPlanResponse result) {
        if (result == null || result.getPlanId() == null ||
                (result.getSolverStatus() != SolverRunStatus.FEASIBLE && result.getSolverStatus() != SolverRunStatus.OPTIMAL))
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Chưa tìm được phương án phù hợp; chưa lưu thay đổi.");
    }

    private MealEditPolicy policy(String token, MealAccount account, long planId, long baseVersion) {
        var plan = mapper.toResponse(account, optimizer.getMealPlanDetail(account.serviceUserId(), planId));
        if (plan.version() != baseVersion || plan.profileVersion() != account.profile().version())
            throw new OptimizerBusinessException(CONFLICT, "Hồ sơ hoặc thực đơn đã thay đổi.");
        return new MealEditPolicy(baseVersion - 1, personal.exclusions(token, account.profile()), personal.allergyTags(account.profile()));
    }

    public MealPlanResponse replace(String token, long version, long baseVersion, long id, long item, ManualReplaceItemRequest input) {
        var account = account(token, version);
        input.setPolicy(policy(token, account, id, baseVersion));
        return changes.manualReplace(account.serviceUserId(), id, item, input);
    }

    public MealPlanResponse swap(String token, long version, long baseVersion, long id, SwapSingleDishRequest input) {
        var account = account(token, version);
        input.setPolicy(policy(token, account, id, baseVersion));
        return optimizer.swapSingleDishInPlan(account.serviceUserId(), id, input);
    }

    public MealPlanResponse rebalance(String token, long version, long baseVersion, long id, RebalanceRemainingMealsRequest input) {
        var account = account(token, version);
        input.setPolicy(policy(token, account, id, baseVersion));
        return optimizer.rebalanceRemainingMeals(account.serviceUserId(), id, input);
    }
}
