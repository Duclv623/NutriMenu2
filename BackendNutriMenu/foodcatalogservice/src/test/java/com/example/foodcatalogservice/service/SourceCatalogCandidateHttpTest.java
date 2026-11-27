package com.example.foodcatalogservice.service;

import com.example.foodcatalogservice.controller.*;
import com.example.foodcatalogservice.dto.DishSolverDto;
import com.example.foodcatalogservice.entity.*;
import com.example.foodcatalogservice.entity.enums.*;
import com.example.foodcatalogservice.dao.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Actual source import -> real HTTP binding/service; repository double applies every JPQL candidate predicate. */
class SourceCatalogCandidateHttpTest {
    final Map<String,Dish> imported=new LinkedHashMap<>();
    final Map<String,Ingredient> ingredients=new HashMap<>();
    final ObjectMapper mapper=new ObjectMapper();
    final AtomicLong ids=new AtomicLong(100);
    DishRepository dishes;
    MockMvc mvc;
    Set<MedicalTag> queriedTags;

    @BeforeEach void setup() throws Exception {
        dishes=mock(DishRepository.class);
        var ingredientRepository=mock(IngredientRepository.class);
        when(ingredientRepository.findByVfcCode(any())).thenAnswer(c -> Optional.ofNullable(ingredients.get(c.getArgument(0))));
        when(ingredientRepository.save(any())).thenAnswer(c -> { Ingredient i=c.getArgument(0); if(i.getId()==null)i.setId(ids.incrementAndGet()); ingredients.put(i.getVfcCode(),i); return i; });
        when(dishes.findByCode(any())).thenAnswer(c -> Optional.ofNullable(imported.get(c.getArgument(0))));
        when(dishes.save(any())).thenAnswer(c -> save(c.getArgument(0)));
        when(dishes.saveAndFlush(any())).thenAnswer(c -> save(c.getArgument(0)));
        try(var stream=getClass().getResourceAsStream("/catalog/nin-bulk-20261126/nin-source-import.json")) {
            var fixture=mapper.readValue(stream,SourceCatalogImportService.CatalogFile.class);
            assertEquals(1023,new SourceCatalogImportService(dishes,ingredientRepository).importCatalog(fixture));
        }
        // A legacy row must never appear even when general mode permits HIGH_GI foods.
        imported.put("QA_UNRELATED",Dish.builder().id(999L).code("QA_UNRELATED").sourceRecordId("legacy")
                .sourceUrl(CatalogSourcePolicy.SOURCE_URL).nutritionQualityJson("{}").estimatedGi(BigDecimal.ZERO)
                .dishRole(DishRole.MAIN_STARCH).mealSlots(EnumSet.allOf(MealSlot.class)).isActive(true).build());
        when(dishes.findCandidatesForSolver(anySet(),any(),any(),anySet(),anyBoolean(),anySet(),anySet(),anyBoolean())).thenAnswer(c -> {
            Set<String> identities=c.getArgument(0); Long user=c.getArgument(1); BigDecimal maxGi=c.getArgument(2);
            Set<MealSlot> slots=c.getArgument(3); boolean exclude=c.getArgument(4); Set<Long> excludedIds=c.getArgument(5);
            queriedTags=Set.copyOf(c.getArgument(6)); boolean applyGi=c.getArgument(7);
            // Mirrors DishRepository.findCandidatesForSolver rather than returning all imported rows.
            return imported.values().stream().filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                    .filter(d -> identities.contains(d.getCode()+":"+d.getSourceRecordId()))
                    .filter(d -> d.getSourceRecordId()!=null && CatalogSourcePolicy.SOURCE_URL.equals(d.getSourceUrl()))
                    .filter(d -> d.getNutritionQualityJson()!=null)
                    .filter(d -> d.getCreatedByUserId()==null || Objects.equals(d.getCreatedByUserId(),user))
                    .filter(d -> !applyGi || d.getEstimatedGi()!=null && d.getEstimatedGi().compareTo(maxGi)<=0)
                    .filter(d -> !Collections.disjoint(d.getMealSlots(),slots))
                    .filter(d -> !exclude || !excludedIds.contains(d.getId()))
                    .filter(d -> Collections.disjoint(d.getMedicalTags(),queriedTags))
                    .sorted(Comparator.comparing(Dish::getPopularityScore).reversed().thenComparing(Dish::getGlycemicLoad,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Dish::getCode)).toList();
        });
        var catalog=new FoodCatalogService(dishes,null,null,null,mock(UserFoodPreferenceRepository.class),null);
        mvc=MockMvcBuilders.standaloneSetup(new InternalSolverCatalogController(catalog),new AdminCatalogController(catalog,mock(IngredientService.class))).build();
    }
    private Dish save(Dish dish) { if(dish.getId()==null)dish.setId(ids.incrementAndGet()); dish.syncScaledValuesForCpSat(); imported.put(dish.getCode(),dish); return dish; }
    private DishSolverDto[] candidates(String json) throws Exception {
        var response=mvc.perform(post("/api/v1/internal/dishes/candidates").contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
        assertEquals(200,response.getStatus(),response.getContentAsString());
        return mapper.readValue(response.getContentAsString(),DishSolverDto[].class);
    }
    @Test void explicitEmptyTagsWithGi100ReturnsAllFortySourceDishesThroughHttp() throws Exception {
        var result=candidates("{\"planningMode\":\"GENERAL_REFERENCE\",\"maxGiAllowed\":100,\"excludedMedicalTags\":[],\"excludedRecentDishIds\":[],\"maxCandidatesPerRole\":10000}");
        assertTrue(result.length>500,"Bulk general candidate pool");
        assertEquals(imported.values().stream().filter(CatalogSourcePolicy::isCurrentSource).filter(d -> Boolean.TRUE.equals(d.getSolverEligible())).count(),result.length);
        assertTrue(Arrays.stream(result).anyMatch(d -> d.getFiberX10()==null && d.getGlX10()==null && d.getCholesterolX10()==null));
        assertEquals(Set.of(),queriedTags);
        assertEquals(EnumSet.allOf(DishRole.class),new HashSet<>(Arrays.stream(result).map(DishSolverDto::getRole).toList()));
        for(var dto:result) {
            Dish original=imported.get(dto.getCode()); assertNotNull(original); assertTrue(CatalogSourcePolicy.isCurrentSource(original));
            assertEquals(original.getDishRole(),dto.getRole()); assertEquals(original.getMealSlots(),dto.getValidMealSlots());
            assertEquals(original.getCaloriesScaledX10().longValue(),dto.getCaloriesX10());
            assertEquals(original.getNetCarbScaledX10().longValue(),dto.getNetCarbX10());
            assertEquals(original.getProteinScaledX10().longValue(),dto.getProteinX10());
            assertEquals(original.getFatScaledX10().longValue(),dto.getFatX10());
            assertEquals(original.getFiberScaledX10(),dto.getFiberX10());
            assertEquals(original.getGlScaledX10(),dto.getGlX10());
            assertEquals(original.getSodiumScaledX10().longValue(),dto.getSodiumX10());
            assertEquals(original.getCholesterolScaledX10(),dto.getCholesterolX10());
            assertFalse(dto.getRawIngredientsPerServing().isEmpty());
        }
    }
    @Test void numericMedicalGi50StillRestrictsExplicitEmptyTags() throws Exception {
        var result=candidates("{\"maxGiAllowed\":50,\"excludedMedicalTags\":[]}");
        assertEquals(0,result.length,"Bulk has no complete clinical GI/fiber/chol evidence"); assertEquals(Set.of(),queriedTags);
        assertTrue(Arrays.stream(result).allMatch(d -> imported.get(d.getCode()).getEstimatedGi().compareTo(new BigDecimal("50"))<=0));
    }
    @Test void omittedHttpFieldsKeepLegacySafetyDefaults() throws Exception {
        var result=candidates("{}");
        assertEquals(0,result.length); assertEquals(Set.of(MedicalTag.HIGH_GI),queriedTags);
        var request=mapper.readValue("{}",com.example.foodcatalogservice.dto.request.CandidateFilterRequest.class);
        assertEquals(new BigDecimal("50.0"),request.getMaxGiAllowed());
        assertEquals(25,request.getMaxCandidatesPerRole());
        assertEquals(EnumSet.allOf(MealSlot.class),request.getRequiredMealSlots());
    }
    @Test void explicitNullTagsKeepLegacySafetyDefaultAtGi100() throws Exception {
        assertEquals(0,candidates("{\"maxGiAllowed\":100,\"excludedMedicalTags\":null}").length);
        assertEquals(Set.of(MedicalTag.HIGH_GI),queriedTags);
    }
    @Test void coverageDiagnosticUsesTheSameExplicitEmptyTagSemantics() throws Exception {
        var response=mvc.perform(post("/api/v1/admin/diagnostics/solver-coverage").contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxGiAllowed\":100,\"excludedMedicalTags\":[]}")).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        assertEquals(0,mapper.readTree(response.getContentAsString()).get("totalEligibleDishes").asInt(),"Clinical diagnostic keeps its strict nutrition policy");
        assertEquals(Set.of(),queriedTags);
    }
    @Test void readinessReportsPackagedExpectedCountAndLiveFilteredCounts() throws Exception {
        var response=mvc.perform(get("/api/v1/internal/dishes/readiness")).andReturn().getResponse();
        assertEquals(200,response.getStatus()); var readiness=mapper.readTree(response.getContentAsString());
        assertEquals(1023,readiness.get("expectedSourceDishCount").asInt());
        assertTrue(readiness.get("generalEligibleDishCount").asInt()>500);
        assertEquals(0,readiness.get("medicalEligibleDishCount").asInt());
        assertFalse(readiness.get("datasetId").asString().isBlank());
        int remaining=0;
        for(Dish dish:imported.values()) if(CatalogSourcePolicy.isCurrentSource(dish)) dish.setIsActive(++remaining<=2);
        response=mvc.perform(get("/api/v1/internal/dishes/readiness")).andReturn().getResponse();
        readiness=mapper.readTree(response.getContentAsString());
        assertEquals(1023,readiness.get("expectedSourceDishCount").asInt());
        assertEquals(2,readiness.get("loadedSourceDishCount").asInt());
    }
    @Test void allergyFilterDoesNotTreatUnverifiedIngredientsAsSafe() throws Exception {
        var result=candidates("{\"planningMode\":\"GENERAL_REFERENCE\",\"maxGiAllowed\":100,\"excludedMedicalTags\":[\"ALLERGEN_EGG\",\"ALLERGEN_SOY\"],\"maxCandidatesPerRole\":10000}");
        assertTrue(result.length>20); assertTrue(result.length<100);
        for(var dto:result) {
            Dish dish=imported.get(dto.getCode());
            assertEquals("ABSENT_CONFIRMED",CatalogNutritionQuality.allergenStatus(dish,MedicalTag.ALLERGEN_EGG));
            assertEquals("ABSENT_CONFIRMED",CatalogNutritionQuality.allergenStatus(dish,MedicalTag.ALLERGEN_SOY));
        }
        assertEquals(Set.of(MedicalTag.ALLERGEN_EGG,MedicalTag.ALLERGEN_SOY),queriedTags);
        System.out.println("BULK_HTTP_EGG_SOY candidates="+result.length);
    }
    @Test void productionDefaultCandidateCapUsesStableSourceOrder() throws Exception {
        var result=candidates("{\"planningMode\":\"GENERAL_REFERENCE\",\"maxGiAllowed\":100,\"excludedMedicalTags\":[]}");
        assertEquals(138,result.length);
        for(DishRole role:DishRole.values()) {
            var actual=Arrays.stream(result).filter(d -> d.getRole()==role).map(DishSolverDto::getCode).toList();
            assertTrue(actual.size()<=25);
            var expected=imported.values().stream().filter(CatalogSourcePolicy::isCurrentSource).filter(d -> d.getDishRole()==role && Boolean.TRUE.equals(d.getSolverEligible()))
                    .sorted(Comparator.comparing(Dish::getPopularityScore).reversed().thenComparing(Dish::getGlycemicLoad,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Dish::getCode)).limit(25).map(Dish::getCode).toList();
            assertEquals(expected,actual);
        }
        System.out.println("BULK_HTTP_PRODUCTION candidates="+result.length);
    }
}
