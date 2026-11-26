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
    private MealAccount accountForUpdate(String token) {
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
        if (plans.findByUserIdAndPlanDate(account.serviceUserId(), request.date()).isPresent())
            throw new OptimizerBusinessException(CONFLICT, "Đã có thực đơn ngày này");
        double target = request.target();
        var input = GenerateDailyMealRequest.builder().planDate(request.date()).sourceProfileVersion(profile.version())
                .targetCaloriesKcal(BigDecimal.valueOf(target)).targetNetCarbG(round(target * .55 / 4))
                .targetProteinG(round(target * .175 / 4)).targetFatG(round(target * .275 / 9))
                .patientContraindications(allergyTags(profile)).excludedDishIds(exclusions(token, profile)).build();
        var errors = validator.validate(input);
        if (!errors.isEmpty()) throw new OptimizerBusinessException(BAD_REQUEST, errors.iterator().next().getMessage());
        var result = optimizer.generateOptimalDailyMealPlan(account.serviceUserId(), input);
        if (result.getSolverStatus() != SolverRunStatus.OPTIMAL && result.getSolverStatus() != SolverRunStatus.FEASIBLE)
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Solver chưa tìm được thực đơn sử dụng được");
        eligible(accounts.account(token).profile(), request.profileVersion());
        return mapper.toResponse(account, result);
    }

    public PersonalMealPlanResponseDTO change(String token, long id, SwapMealRequestDTO request, boolean preview) {
        var account = accountForUpdate(token);
        var plan = mapper.toResponse(account, optimizer.getMealPlanDetail(account.serviceUserId(), id));
        version(plan, request.baseVersion());
        eligible(account.profile(), request.profileVersion());
        if (plan.profileVersion() != request.profileVersion())
            throw new OptimizerBusinessException(CONFLICT, "Hồ sơ đã thay đổi từ lúc tạo thực đơn");
        double scaled = request.servings() * 2;
        if (scaled != Math.rint(scaled)) throw new OptimizerBusinessException(BAD_REQUEST, "Khẩu phần phải theo bước nửa suất");
        if (!preview && (request.previewSignature() == null || request.previewSignature().isBlank()))
            throw new OptimizerBusinessException(BAD_REQUEST, "Cần xem trước phương án trước khi xác nhận");
        var input = CheckedMealChangeRequest.builder().expectedVersion(request.baseVersion() - 1)
                .itemId(numericId(request.itemId())).newDishId(numericId(request.foodId())).newServingScaledX2((int) scaled)
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

    public void complete(String token, MealLogRequestDTO request) {
        String[] ids = request.key().split(":", -1);
        if (ids.length != 2) throw new OptimizerBusinessException(BAD_REQUEST, "Mã thực đơn/món không hợp lệ");
        optimizer.updateMealItemStatus(accountForUpdate(token).serviceUserId(), numericId(ids[0]), numericId(ids[1]),
                request.completed() ? MealItemStatus.COMPLETED : MealItemStatus.PLANNED);
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
        plan.items().forEach(item -> dishes.computeIfAbsent(item.foodId(), key -> catalog.food(token, key)));
        return mapper.snapshot(account.profile(), plan, dishes);
    }

    private static long numericId(String text) {
        try {
            long id = Long.parseLong(text);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new OptimizerBusinessException(BAD_REQUEST, "Mã tài nguyên không hợp lệ");
    }

    private void eligible(Profile profile, long version) {
        if (profile.version() != version) throw new OptimizerBusinessException(CONFLICT, "Hồ sơ đã thay đổi; hãy tải lại");
        if (!profile.confirmed() || profile.age() < 18 || profile.pregnant() || profile.specialDiet())
            throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Cần hồ sơ đã xác nhận và thuộc phạm vi hỗ trợ");
    }

    private void version(PersonalMealPlanResponseDTO plan, long expected) {
        if (plan.version() != expected) throw new OptimizerBusinessException(CONFLICT, "Thực đơn đã thay đổi; hãy tải lại");
    }

    private Set<MedicalTag> allergyTags(Profile profile) {
        Set<MedicalTag> tags = new HashSet<>();
        for (String allergy : profile.allergies()) {
            String tag = ALLERGIES.getOrDefault(allergy, allergy);
            if (!ALLERGIES.containsValue(tag)) throw new OptimizerBusinessException(UNPROCESSABLE_CONTENT, "Chưa ánh xạ được dị ứng: " + allergy);
            tags.add(MedicalTag.valueOf(tag));
        }
        return tags;
    }

    private Set<Long> exclusions(String token, Profile profile) {
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
