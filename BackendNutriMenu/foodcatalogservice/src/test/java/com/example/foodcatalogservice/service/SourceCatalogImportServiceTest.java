package com.example.foodcatalogservice.service;

import com.example.foodcatalogservice.dto.response.DishDetailResponse;
import com.example.foodcatalogservice.entity.*;
import com.example.foodcatalogservice.entity.enums.*;
import com.example.foodcatalogservice.exception.BusinessRuleException;
import com.example.foodcatalogservice.dao.repository.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class SourceCatalogImportServiceTest {
    private static SourceCatalogImportService.CatalogFile fixture() throws Exception {
        try (var stream = SourceCatalogImportServiceTest.class.getResourceAsStream("/catalog/nin-real-20261123/nin-source-import.json")) {
            return new ObjectMapper().readValue(stream, SourceCatalogImportService.CatalogFile.class);
        }
    }

    @Test void actualFortySourceFixturePreservesUnknownsImagesAndIdsAcrossImports() throws Exception {
        Map<String, Dish> saved = new HashMap<>();
        Map<String, Ingredient> identities = new HashMap<>();
        AtomicLong nextId = new AtomicLong(100);
        IngredientRepository ingredientRepository = repository(IngredientRepository.class, (method, args) -> {
            if (method.equals("findByVfcCode")) return Optional.ofNullable(identities.get(args[0]));
            if (!method.equals("save")) throw new UnsupportedOperationException(method);
            Ingredient ingredient = (Ingredient) args[0]; if (ingredient.getId() == null) ingredient.setId(nextId.incrementAndGet());
            identities.put(ingredient.getVfcCode(), ingredient); return ingredient;
        });
        DishRepository dishRepository = repository(DishRepository.class, (method, args) -> {
            if (method.equals("findByCode")) return Optional.ofNullable(saved.get(args[0]));
            if (!method.equals("save") && !method.equals("saveAndFlush")) throw new UnsupportedOperationException(method);
            Dish dish = (Dish) args[0]; if (dish.getId() == null) dish.setId(nextId.incrementAndGet());
            dish.syncScaledValuesForCpSat(); saved.put(dish.getCode(), dish); return dish;
        });
        var service = new SourceCatalogImportService(dishRepository, ingredientRepository);
        assertEquals(40, service.importCatalog(fixture()));
        assertEquals(40, saved.size());
        assertEquals(40, saved.values().stream().filter(d -> Boolean.TRUE.equals(d.getIsActive())).count());
        assertEquals(0, saved.values().stream().filter(d -> d.getCaloriesKcal() == null || d.getProteinG() == null || d.getFatG() == null || d.getTotalCarbG() == null).count());
        assertEquals(40, saved.values().stream().filter(CatalogNutritionQuality::baseEligible).count());
        assertTrue(saved.values().stream().allMatch(d -> d.getImageUrl().startsWith("https://viendinhduong.vn/")));
        assertTrue(identities.values().stream().allMatch(i -> i.getCaloriesPer100g() == null && i.getGiValue() == null && i.getNetCarbPer100g() == null));
        Dish unknown = saved.get("NIN_REAL_VPF_000009");
        assertTrue(unknown.getEstimatedGi().compareTo(new BigDecimal("50")) > 0); assertNotNull(unknown.getGlScaledX10()); assertNull(unknown.getStandardServingCookedG());
        assertTrue(unknown.getIngredients().stream().allMatch(i -> i.getRawWeightG() != null));
        assertTrue(unknown.getNutritionQualityJson().contains("Muối trong dưa cải bẹ*"));
        assertNotNull(DishDetailResponse.fromEntity(unknown, true).getTrafficLightBadge());
        Dish orange = saved.get("NIN_REAL_VPF_000398");
        assertEquals(8, orange.getMaxServingScaledX2());
        Dish sushi = saved.get("NIN_REAL_VPF_000014");
        assertEquals(2, sushi.getMinServingScaledX2());
        assertEquals(24, sushi.getMaxServingScaledX2());
        assertEquals(2, sushi.getStepServingScaledX2());
        assertEquals(0, sushi.getCaloriesKcal().compareTo(new BigDecimal("42")));
        assertEquals(0, orange.getNetCarbG().compareTo(new BigDecimal("8.2"))); // Never subtract unspecified Glucid fibre.
        assertEquals(0, orange.getGlycemicLoad().compareTo(new BigDecimal("3.53")));
        assertEquals(0, orange.getStandardServingCookedG().compareTo(new BigDecimal("130")));
        assertEquals("SOURCE_RAW_IMAGE_AMOUNT_NOT_COOKED", orange.getServingWeightBasis());
        assertNull(orange.getPurineMg());
        assertFalse(CatalogNutritionQuality.eligibleFor(orange, Set.of(MedicalTag.HIGH_PURINE)));
        assertFalse(CatalogNutritionQuality.eligibleFor(orange, Set.of(MedicalTag.HIGH_SATURATED_FAT)));
        assertTrue(CatalogNutritionQuality.eligibleFor(orange, Set.of(MedicalTag.HIGH_POTASSIUM)));
        unknown.setSolverEligible(false); // Explicitly exercise the runtime eligibility guard.
        DishRepository publicQueries = repository(DishRepository.class, (method, args) -> {
            if (method.equals("findById")) return saved.values().stream().filter(d -> d.getId().equals(args[0])).findFirst();
            if (method.equals("findCandidatesForSolver")) return List.of(unknown, orange);
            throw new UnsupportedOperationException(method);
        });
        FoodCatalogService catalog = new FoodCatalogService(publicQueries, null, null, null, null, null);
        var scaled = catalog.calculateDynamicServing(orange.getId(), new BigDecimal("2"));
        assertNull(scaled.getAdjustedIngredients().getFirst().getCookedWeightG());
        assertEquals(0, scaled.getAdjustedCookedWeightG().compareTo(new BigDecimal("260.0")));
        var sushiPieces = catalog.calculateDynamicServing(sushi.getId(), new BigDecimal("12"));
        assertEquals(0, sushiPieces.getCaloriesKcal().compareTo(new BigDecimal("504")));
        assertNull(sushiPieces.getAdjustedCookedWeightG());
        assertThrows(BusinessRuleException.class, () -> catalog.calculateDynamicServing(sushi.getId(), new BigDecimal("12.5")));
        var request = new com.example.foodcatalogservice.dto.request.CandidateFilterRequest();
        request.setMaxCandidatesPerRole(1);
        var candidates = catalog.getCandidateDishesForSolver(request);
        assertEquals(0, candidates.size(),"Old40 retained for history, not current bulk choices");
        orange.setPortionEvidenceComplete(false);
        assertFalse(CatalogNutritionQuality.baseEligible(orange));
        orange.setPortionEvidenceComplete(true);
        Map<String, Long> ids = new HashMap<>(); saved.forEach((code, dish) -> ids.put(code, dish.getId()));
        service.importCatalog(fixture());
        saved.forEach((code, dish) -> assertEquals(ids.get(code), dish.getId()));
    }

    @Test void sourceIngredientCannotBeRecalculatedOrSimulatedAsZeroNutrition() {
        NutritionCalculationEngine engine = new NutritionCalculationEngine(null);
        Dish source = new Dish(); source.setSourceRecordId("source");
        assertThrows(BusinessRuleException.class, () -> engine.computeNutritionAndMedicalTags(source, List.of()));
        Ingredient identity = new Ingredient(); identity.setCaloriesPer100g(null);
        DishIngredient item = DishIngredient.builder().ingredient(identity).rawWeightG(BigDecimal.TEN).build();
        assertThrows(BusinessRuleException.class, () -> engine.computeNutritionAndMedicalTags(new Dish(), List.of(item)));
    }

    @Test void incompleteSourceCannotClaimSafetyAndUnknownWeightsStayNull() throws Exception {
        Dish incomplete = new Dish(); incomplete.setId(1L); incomplete.setNameVi("Nguồn thiếu dữ liệu"); incomplete.setSolverEligible(false);
        DishRepository repository = repository(DishRepository.class, (method, args) -> Optional.of(incomplete));
        FoodCatalogService service = new FoodCatalogService(repository, null, null, null, null, null);
        assertFalse(service.checkPersonalizedSafety(1L, new com.example.foodcatalogservice.dto.request.SafetyCheckRequest()).isSafeForPatient());
        assertThrows(BusinessRuleException.class, () -> service.calculateDynamicServing(1L, BigDecimal.ONE));
    }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> calls.apply(method.getName(), args));
    }
}
