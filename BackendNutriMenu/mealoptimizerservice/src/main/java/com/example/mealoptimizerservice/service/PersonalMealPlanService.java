package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.client.FoodCatalogIntegrationClient;
import com.example.mealoptimizerservice.client.MealAccountClient;
import com.example.mealoptimizerservice.client.MealAccountClient.*;
import com.example.mealoptimizerservice.dto.request.*;
import com.example.mealoptimizerservice.dto.response.*;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.mapper.MealPlanMapper;
import com.example.mealoptimizerservice.dao.repository.MealPlanRepository;
import com.example.mealoptimizerservice.exception.OptimizerBusinessException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.util.stream.Collectors;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
@Transactional
public class PersonalMealPlanService {
    private final MealAccountClient accounts;
    private final FoodCatalogIntegrationClient catalog;
    private final MealOptimizerService optimizer;
    private final CheckedMealChangeService changes;
    private final MealPlanRepository plans;
    private final MealPlanMapper mapper;
    private final JdbcTemplate jdbc;
    private final Validator validator;
    private static final Map<String, String> ALLERGIES = Map.of("Hải sản", "ALLERGEN_SEAFOOD", "Trứng", "ALLERGEN_EGG",
            "Sữa", "ALLERGEN_DAIRY", "Đậu nành", "ALLERGEN_SOY", "Đậu phộng", "ALLERGEN_PEANUT");


    // Serializes concurrent public plan writes, including creation before a plan row exists.
    MealAccount accountForUpdate(String token) {
        var account = accounts.account(token);
        jdbc.query("select pg_advisory_xact_lock(?)", rs -> {}, account.serviceUserId());
        return account;
    }

    @Transactional(readOnly = true)
    public List<PersonalMealPlanResponseDTO> list(String token) {
        var account = accounts.account(token);
        return plans.findByUserIdOrderByPlanDateDesc(account.serviceUserId(), Pageable.unpaged())
                .map(plan -> mapper.toResponse(account, MealPlanResponse.fromEntity(plan, null, List.of()))).getContent();
    }

    public PersonalMealPlanResponseDTO generate(String token, GenerateMealPlanRequestDTO request) {
        var account = accountForUpdate(token);
        var profile = account.profile();
        eligible(profile, request.profileVersion());
        if (!request.snacks() || request.maxMinutes() != 0 || request.excludeRepeat())
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Hiện chỉ hỗ trợ bốn bữa và bộ lọc mặc định");
        boolean replacing = request.replacePlanId() != null;
        if (replacing && (!request.replacementConfirmed() || request.baseVersion() == null))
            throw new OptimizerBusinessException(BAD_REQUEST, "Tạo lại cần xác nhận và phiên bản thực đơn.");
        if (!replacing && (request.replacementConfirmed() || request.baseVersion() != null))
            throw new OptimizerBusinessException(BAD_REQUEST, "Thiếu thực đơn cần tạo lại.");
        Long previousProfileVersion = null;
        if (replacing) {
            var previous = mapper.toResponse(account, optimizer.getMealPlanDetail(account.serviceUserId(), request.replacePlanId()));
            version(previous, request.baseVersion());
            if (!previous.date().equals(request.date().toString()))
                throw new OptimizerBusinessException(CONFLICT, "Thực đơn cần tạo lại không cùng ngày.");
            previousProfileVersion = previous.profileVersion();
        } else if (plans.findByUserIdAndPlanDate(account.serviceUserId(), request.date()).isPresent()) {
            throw new OptimizerBusinessException(CONFLICT, "Đã có thực đơn ngày này. Chọn Tạo lại và xác nhận thay thế.");
        }
        var input = generationInput(token, profile, request, previousProfileVersion);
        var result = optimizer.generateOptimalDailyMealPlan(account.serviceUserId(), input);
        if (result.getSolverStatus() != SolverRunStatus.OPTIMAL && result.getSolverStatus() != SolverRunStatus.FEASIBLE)
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Solver chưa tìm được thực đơn sử dụng được");
        eligible(accounts.account(token).profile(), request.profileVersion());
        return mapper.toResponse(account, result);
    }

    /** One optimizer transaction: either all seven plans are saved, or none are. */
    public List<PersonalMealPlanResponseDTO> generateWeekly(String token, GenerateMealPlanRequestDTO request) {
        var account = accountForUpdate(token);
        eligible(account.profile(), request.profileVersion());
        if (!request.snacks() || request.maxMinutes() != 0 || request.excludeRepeat())
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Hiện chỉ hỗ trợ bốn bữa và bộ lọc mặc định");
        if (request.replacePlanId() != null || request.baseVersion() != null || request.replacementConfirmed())
            throw new OptimizerBusinessException(BAD_REQUEST, "Sinh 7 ngày chỉ tạo ngày trống; tạo lại từng ngày nếu cần thay thế.");
        var occupied = plans.findByUserIdAndPlanDateBetweenOrderByPlanDateAsc(account.serviceUserId(),request.date(),request.date().plusDays(6));
        if (!occupied.isEmpty()) {
            String dates = occupied.stream().map(p -> p.getPlanDate().toString()).collect(Collectors.joining(", "));
            throw new OptimizerBusinessException(CONFLICT, "Khoảng 7 ngày đã có thực đơn vào: " + dates + ". Chọn khoảng trống hoặc tạo lại từng ngày.");
        }
        var input = generationInput(token, account.profile(), request, null);
        var results = optimizer.generateWeeklyMealPlans(account.serviceUserId(), GenerateWeeklyMealRequest.builder()
                .startDate(request.date()).dailyTargetTemplate(input).build());
        if (results.size() != 7 || results.stream().anyMatch(p -> p.getSolverStatus() != SolverRunStatus.OPTIMAL && p.getSolverStatus() != SolverRunStatus.FEASIBLE))
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Chưa tạo được đủ 7 ngày phù hợp. Không lưu một phần thực đơn.");
        eligible(accounts.account(token).profile(), request.profileVersion());
        return results.stream().map(p -> mapper.toResponse(account,p)).toList();
    }

    private GenerateDailyMealRequest generationInput(String token, Profile profile, GenerateMealPlanRequestDTO request, Long previousProfileVersion) {
        boolean replacing = request.replacePlanId() != null;
        double target = request.target();
        var input = GenerateDailyMealRequest.builder().planDate(request.date()).sourceProfileVersion(profile.version())
                .planningMode(request.planningMode()).replacePlanId(request.replacePlanId())
                .expectedVersion(replacing ? request.baseVersion() - 1 : null)
                .replacementConfirmed(request.replacementConfirmed()).previousProfileVersion(previousProfileVersion)
                .targetCaloriesKcal(BigDecimal.valueOf(target)).targetNetCarbG(round(target * .55 / 4))
                .targetProteinG(round(target * .175 / 4)).targetFatG(round(target * .275 / 9))
                .patientContraindications(allergyTags(profile)).excludedDishIds(exclusions(token, profile)).build();
        var errors = validator.validate(input);
        if (!errors.isEmpty()) throw new OptimizerBusinessException(BAD_REQUEST, errors.iterator().next().getMessage());
        return input;
    }

    public PersonalMealPlanResponseDTO change(String token, long id, SwapMealRequestDTO request, boolean preview) {
        var account = accountForUpdate(token);
        var plan = mapper.toResponse(account, optimizer.getMealPlanDetail(account.serviceUserId(), id));
        version(plan, request.baseVersion());
        eligible(account.profile(), request.profileVersion());
        if (plan.profileVersion() != request.profileVersion())
            throw new OptimizerBusinessException(CONFLICT, "Hồ sơ đã thay đổi từ lúc tạo thực đơn");
        double scaled = request.servingMultiplier() * 2;
        if (scaled != Math.rint(scaled)) throw new OptimizerBusinessException(BAD_REQUEST, "Khẩu phần phải theo bước nửa suất");
        if (!preview && (request.previewSignature() == null || request.previewSignature().isBlank()))
            throw new OptimizerBusinessException(BAD_REQUEST, "Cần xem trước phương án trước khi xác nhận");
        var input = CheckedMealChangeRequest.builder().expectedVersion(request.baseVersion() - 1)
                .itemId(numericId(request.itemId())).newDishId(numericId(request.dishId())).newServingScaledX2((int) scaled)
                .contraindications(allergyTags(account.profile())).excludedDishIds(exclusions(token, account.profile()))
                .previewSignature(request.previewSignature()).build();
        var result = preview ? changes.preview(account.serviceUserId(), id, input) : changes.apply(account.serviceUserId(), id, input);
        if (!result.isValidationPassed()) throw new OptimizerBusinessException(BAD_GATEWAY, "Thay đổi chưa được xác nhận");
        eligible(accounts.account(token).profile(), request.profileVersion());
        return mapper.toResponse(account, result);
    }

    public PersonalMealPlanResponseDTO lock(String token, long id, long item, LockMealRequestDTO request) {
        var account = accountForUpdate(token);
        var plan = optimizer.getMealPlanDetail(account.serviceUserId(), id);
        version(mapper.toResponse(account, plan), request.baseVersion());
        return mapper.toResponse(account, optimizer.toggleMealItemLock(account.serviceUserId(), id, item, request.locked()));
    }

    public void delete(String token, long id) {
        optimizer.deleteMealPlan(accountForUpdate(token).serviceUserId(), id);
    }

    public PersonalMealPlanResponseDTO complete(String token, MealLogRequestDTO request) {
        String[] ids = request.key().split(":", -1);
        if (ids.length != 2) throw new OptimizerBusinessException(BAD_REQUEST, "Mã thực đơn/món không hợp lệ");
        var account = accountForUpdate(token);
        optimizer.updateMealItemStatus(account.serviceUserId(), numericId(ids[0]), numericId(ids[1]),
                request.completed() ? MealItemStatus.COMPLETED : MealItemStatus.PLANNED);
        return mapper.toResponse(account, optimizer.getMealPlanDetail(account.serviceUserId(), numericId(ids[0])));
    }

    @Transactional(readOnly = true)
    public JsonNode snapshot(String token, Long id) {
        var account = accounts.account(token);
        if (id == null) {
            id = plans.findByUserIdAndPlanDate(account.serviceUserId(), LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                    .orElseThrow(() -> new OptimizerBusinessException(NOT_FOUND, "Chưa có thực đơn hôm nay")).getId();
        }
        var plan = mapper.toResponse(account, optimizer.getMealPlanDetail(account.serviceUserId(), id));
        if (plan.items().isEmpty() || !Set.of("OPTIMAL", "FEASIBLE").contains(plan.solverStatus()))
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Thực đơn chưa có kết quả sử dụng được");
        Map<String, JsonNode> dishes = new HashMap<>();
        plan.items().forEach(item -> dishes.computeIfAbsent(item.dishId(), key -> catalog.food(token, key)));
        return mapper.snapshot(account.profile(), plan, dishes);
    }

    private static long numericId(String text) {
        try {
            long id = Long.parseLong(text);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new OptimizerBusinessException(BAD_REQUEST, "Mã tài nguyên không hợp lệ");
    }

    void eligible(Profile profile, long version) {
        if (profile.version() != version) throw new OptimizerBusinessException(CONFLICT, "Hồ sơ đã thay đổi; hãy tải lại");
        if (!profile.confirmed() || profile.age() < 18 || profile.pregnant() || profile.specialDiet())
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Cần hồ sơ đã xác nhận và thuộc phạm vi hỗ trợ");
    }

    private void version(PersonalMealPlanResponseDTO plan, long expected) {
        if (plan.version() != expected) throw new OptimizerBusinessException(CONFLICT, "Thực đơn đã thay đổi; hãy tải lại");
    }

    Set<MedicalTag> allergyTags(Profile profile) {
        Set<MedicalTag> tags = new HashSet<>();
        for (String allergy : profile.allergies()) {
            String tag = ALLERGIES.getOrDefault(allergy, allergy);
            if (!ALLERGIES.containsValue(tag)) throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Chưa ánh xạ được dị ứng: " + allergy);
            tags.add(MedicalTag.valueOf(tag));
        }
        return tags;
    }

    Set<Long> exclusions(String token, Profile profile) {
        Set<Long> result = new LinkedHashSet<>();
        if (profile.excludedFoods().isEmpty()) return result;
        var dishes = catalog.foods(token);
        for (String restriction : profile.excludedFoods()) {
            boolean unaccented = restriction.chars().allMatch(c -> c < 128);
            Set<String> words = words(restriction, unaccented);
            boolean matched = false;
            for (JsonNode dish : dishes) {
                List<String> names = new ArrayList<>(); names.add(dish.path("nameVi").asText());
                dish.path("recipeBreakdown").forEach(i -> names.add(i.path("ingredientNameVi").asText()));
                if (!words.isEmpty() && names.stream().anyMatch(name -> words(name, unaccented).containsAll(words))) {
                    result.add(dish.path("id").asLong()); matched = true;
                }
            }
            if (!matched) throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Chưa nhận diện được mục không ăn: " + restriction);
        }
        dishes.forEach(d -> { if (d.path("recipeBreakdown").isEmpty()) result.add(d.path("id").asLong()); });
        return result;
    }

    private Set<String> words(String value, boolean unaccented) {
        String text = value.toLowerCase(Locale.ROOT);
        if (unaccented) text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd');
        return Arrays.stream(text.split("[^\\p{L}]+")).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private BigDecimal round(double value) { return BigDecimal.valueOf(Math.round(value * 10), 1); }
}
