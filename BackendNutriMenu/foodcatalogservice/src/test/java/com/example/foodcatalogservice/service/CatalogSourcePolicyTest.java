package com.example.foodcatalogservice.service;

import com.example.foodcatalogservice.config.CatalogDataSeederConfig;
import com.example.foodcatalogservice.config.CatalogRetirementConfig;
import com.example.foodcatalogservice.config.SourceCatalogSeederConfig;
import com.example.foodcatalogservice.dto.request.CandidateFilterRequest;
import com.example.foodcatalogservice.dto.request.CreateDishRequest;
import com.example.foodcatalogservice.entity.Dish;
import com.example.foodcatalogservice.entity.Ingredient;
import com.example.foodcatalogservice.entity.enums.DishRole;
import com.example.foodcatalogservice.exception.BusinessRuleException;
import com.example.foodcatalogservice.exception.ResourceNotFoundException;
import com.example.foodcatalogservice.dao.repository.DishRepository;
import com.example.foodcatalogservice.dao.repository.IngredientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.Query;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class CatalogSourcePolicyTest {
    @Test void mixedExistingDatabaseRetiresLegacyWithoutRemovingHistoricalIds() throws Exception {
        Map<String, Dish> saved = new LinkedHashMap<>();
        Map<String, Ingredient> ingredients = new HashMap<>();
        AtomicLong ids = new AtomicLong(100);
        Dish old = new Dish();
        old.setCaloriesKcal(new java.math.BigDecimal("100")); old.setId(7L); old.setCode("VI_QA_COM_GAO_LUT"); old.setNameVi("Old QA");
        old.setDishRole(DishRole.SNACK); old.setPopularityScore(9999); old.syncScaledValuesForCpSat();
        saved.put(old.getCode(), old);
        DishRepository dishes = repository(DishRepository.class, (method, args) -> switch (method) {
            case "findByCode" -> Optional.ofNullable(saved.get(args[0]));
            case "findById" -> saved.values().stream().filter(d -> d.getId().equals(args[0])).findFirst();
            case "findAll", "findCandidatesForSolver", "findSimilarAlternatives" -> new ArrayList<>(saved.values());
            case "save", "saveAndFlush" -> {
                Dish dish = (Dish) args[0];
                if (dish.getId() == null) dish.setId(ids.incrementAndGet());
                dish.syncScaledValuesForCpSat(); saved.put(dish.getCode(), dish); yield dish;
            }
            default -> throw new AssertionError("Unexpected repository mutation: " + method);
        });
        IngredientRepository ingredientRepository = repository(IngredientRepository.class, (method, args) -> {
            if (method.equals("findByVfcCode")) return Optional.ofNullable(ingredients.get(args[0]));
            if (!method.equals("save")) throw new AssertionError(method);
            Ingredient ingredient = (Ingredient) args[0];
            if (ingredient.getId() == null) ingredient.setId(ids.incrementAndGet());
            ingredients.put(ingredient.getVfcCode(), ingredient); return ingredient;
        });
        try (var stream = getClass().getResourceAsStream("/catalog/nin-bulk-20261126/nin-source-import.json")) {
            var fixture = new ObjectMapper().readValue(stream, SourceCatalogImportService.CatalogFile.class);
            var importer = new SourceCatalogImportService(dishes, ingredientRepository);
            assertEquals(1023, importer.importCatalog(fixture));
            FoodCatalogService service = new FoodCatalogService(dishes, null, null, null, null, null);
            CandidateFilterRequest request = new CandidateFilterRequest(); request.setPlanningMode(com.example.foodcatalogservice.entity.enums.PlanningMode.GENERAL_REFERENCE); request.setMaxCandidatesPerRole(1);
            // Old active/high-popularity data is blocked even before retirement runs.
            var candidates = service.getCandidateDishesForSolver(request);
            assertEquals(DishRole.values().length, candidates.size());
            assertTrue(candidates.stream().allMatch(d -> CatalogSourcePolicy.dishCodes().contains(d.getCode())));
            assertThrows(ResourceNotFoundException.class, () -> service.getSingleSolverDto(7L, null, true));
            var retirement = new CatalogRetirementService(dishes);
            assertEquals(1, retirement.retireNonSourceChoices());
            assertEquals(0, retirement.retireNonSourceChoices());
            assertEquals(1024, saved.size()); assertEquals(7L, old.getId());
            assertFalse(old.getIsActive()); assertFalse(old.getSolverEligible());
            assertEquals(7L, service.getDishDetail(7L).getId());
            assertEquals(7L, service.getSingleSolverDto(7L, null, false).getDishId());
            assertEquals(1023, saved.values().stream().filter(d -> Boolean.TRUE.equals(d.getIsActive())).count());
            Map<String, Long> existingIds = new HashMap<>(); saved.forEach((code, dish) -> existingIds.put(code, dish.getId()));
            importer.importCatalog(fixture); retirement.retireNonSourceChoices();
            saved.forEach((code, dish) -> assertEquals(existingIds.get(code), dish.getId()));
            assertThrows(BusinessRuleException.class, () -> service.createDish(new CreateDishRequest(), 9L));
            Dish source = saved.values().stream().filter(CatalogSourcePolicy::isCurrentSource).findFirst().orElseThrow();
            assertTrue(CatalogSourcePolicy.isCurrentSource(source));
            source.setSourceRecordId("forged-source");
            assertFalse(CatalogSourcePolicy.isCurrentSource(source));
            assertTrue(service.getCandidateDishesForSolver(request).stream().noneMatch(d -> d.getDishId().equals(source.getId())));
        }
    }

    @Test void sourceScopeAppliesBeforePaginationAndRetirementIgnoresSeedDisabledSetting() {
        assertNull(CatalogDataSeederConfig.class.getAnnotation(Configuration.class));
        assertEquals(0, CatalogDataSeederConfig.class.getDeclaredMethods().length);
        assertNull(CatalogRetirementConfig.class.getAnnotation(ConditionalOnProperty.class));
        assertNotNull(SourceCatalogSeederConfig.class.getAnnotation(ConditionalOnProperty.class));
        assertEquals(1023, CatalogSourcePolicy.dishCodes().size());
        for (var method : DishRepository.class.getDeclaredMethods()) {
            Query query = method.getAnnotation(Query.class);
            if (query != null) {
                assertTrue(query.value().contains("CONCAT(CONCAT(d.code, ':'), d.sourceRecordId) IN :sourceIdentities"));
                assertTrue(query.value().indexOf("CONCAT(CONCAT(d.code, ':'), d.sourceRecordId) IN :sourceIdentities") < query.value().indexOf("ORDER BY"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> calls.apply(method.getName(), args));
    }
}
