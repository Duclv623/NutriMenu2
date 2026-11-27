package com.example.foodcatalogservice.service;

import com.example.foodcatalogservice.entity.*;
import com.example.foodcatalogservice.entity.enums.*;
import com.example.foodcatalogservice.dao.repository.DishRepository;
import com.example.foodcatalogservice.dao.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Idempotent source-serving import. It never runs the recipe calculation engine. */
@Service
@RequiredArgsConstructor
public class SourceCatalogImportService {
    private final DishRepository dishes;
    private final IngredientRepository ingredients;

    public record CatalogFile(int schemaVersion, String datasetId, List<SourceIngredient> ingredients, List<SourceDish> dishes) {}
    public record SourceIngredient(String code, String nameVi, IngredientGroup group) {}
    public record SourceRecipe(String ingredientCode, BigDecimal rawWeightG, boolean mainIngredient, String sourceAmountText) {}
    public record Nutrition(BigDecimal caloriesKcal, BigDecimal totalCarbG, BigDecimal fiberG, BigDecimal netCarbG,
                            BigDecimal proteinG, BigDecimal fatG, BigDecimal saturatedFatG, BigDecimal cholesterolMg,
                            BigDecimal sodiumMg, BigDecimal potassiumMg, BigDecimal purineMg, BigDecimal estimatedGi,
                            BigDecimal glycemicLoad) {}
    public record SourceDish(String code, String nameVi, String imageUrl, String sourceRecordId, String sourceUrl,
                             String nutritionBasis, String qualityJson, CookingMethod cookingMethod, DishRole dishRole,
                             Set<MealSlot> mealSlots, String householdUnitLabel, boolean solverEligible,
                             boolean allergenEvidenceComplete, boolean portionEvidenceComplete,
                             Integer minServingScaledX2, Integer maxServingScaledX2, Integer stepServingScaledX2,
                             BigDecimal sourceRawPortionWeightG, Nutrition nutrition, List<SourceRecipe> recipeItems) {}

    @Transactional
    public int importCatalog(CatalogFile file) {
        if (file.schemaVersion() != 1 || file.dishes() == null || file.ingredients() == null)
            throw new IllegalArgumentException("Unsupported source catalog schema");
        Map<String, Ingredient> identities = new HashMap<>();
        for (SourceIngredient row : file.ingredients()) {
            requireCode(row.code());
            Ingredient ingredient = ingredients.findByVfcCode(row.code()).orElseGet(Ingredient::new);
            ingredient.setVfcCode(row.code()); ingredient.setNameVi(row.nameVi()); ingredient.setIngredientGroup(row.group());
            // Identity rows carry source recipe text, not fabricated per100g data.
            ingredient.setEdiblePortionPct(null); ingredient.setCaloriesPer100g(null); ingredient.setTotalCarbG(null);
            ingredient.setFiberG(null); ingredient.setProteinG(null); ingredient.setFatG(null);
            ingredient.setSaturatedFatG(null); ingredient.setCholesterolMg(null); ingredient.setSodiumMg(null);
            ingredient.setPotassiumMg(null); ingredient.setPurineMg(null); ingredient.setGiValue(null);
            identities.put(row.code(), ingredients.save(ingredient));
        }
        for (SourceDish row : file.dishes()) {
            requireCode(row.code());
            if (row.sourceRecordId() == null || row.imageUrl() == null || row.qualityJson() == null || row.nutrition() == null)
                throw new IllegalArgumentException("Source identity, image and nutrition evidence required");
            Dish dish = dishes.findByCode(row.code()).orElseGet(Dish::new);
            if (unchanged(dish,row)) continue;
            // Flush orphan deletions before recreating rows with the same unique ingredient keys.
            if (dish.getId() != null && !dish.getIngredients().isEmpty()) {
                dish.getIngredients().clear(); dishes.saveAndFlush(dish);
            }
            dish.setCode(row.code()); dish.setNameVi(row.nameVi()); dish.setImageUrl(row.imageUrl());
            dish.setSourceRecordId(row.sourceRecordId()); dish.setSourceUrl(row.sourceUrl());
            dish.setNutritionBasis(row.nutritionBasis()); dish.setNutritionQualityJson(row.qualityJson());
            dish.setCookingMethod(row.cookingMethod()); dish.setDishRole(row.dishRole());
            dish.setMealSlots(new HashSet<>(row.mealSlots())); dish.setHouseholdUnitLabel(row.householdUnitLabel());
            int min = row.minServingScaledX2() == null ? 2 : row.minServingScaledX2();
            int max = row.maxServingScaledX2() == null ? 2 : row.maxServingScaledX2();
            int step = row.stepServingScaledX2() == null ? 2 : row.stepServingScaledX2();
            if (min <= 0 || step <= 0 || max < min || (max - min) % step != 0)
                throw new IllegalArgumentException("Invalid source portion multipliers: " + row.code());
            dish.setMinServingScaledX2(min); dish.setMaxServingScaledX2(max); dish.setStepServingScaledX2(step);
            Nutrition n = row.nutrition();
            // Browsing preserves unknown values; solver eligibility remains independent.
            dish.setIsActive(true);
            dish.setPopularityScore(250); dish.setCreatedByUserId(null);
            dish.setSolverEligible(row.solverEligible()); dish.setAllergenEvidenceComplete(row.allergenEvidenceComplete());
            dish.setPortionEvidenceComplete(row.portionEvidenceComplete());
            // For uncooked single-fruit portions only, expose the explicitly named
            // source raw amount, never a cooked yield or a sum of recipe inputs.
            boolean rawPortion = row.cookingMethod() == CookingMethod.RAW && row.recipeItems().size() == 1
                    && row.sourceRawPortionWeightG() != null && row.sourceRawPortionWeightG().signum() > 0
                    && row.recipeItems().getFirst().rawWeightG() != null
                    && row.sourceRawPortionWeightG().compareTo(row.recipeItems().getFirst().rawWeightG()) == 0;
            dish.setStandardServingCookedG(rawPortion ? row.sourceRawPortionWeightG() : null);
            dish.setServingWeightBasis(rawPortion ? "SOURCE_RAW_IMAGE_AMOUNT_NOT_COOKED" : "UNKNOWN_COOKED_WEIGHT");
            dish.setDiabeticCookingTip("Nguồn NIN; khẩu phần theo ảnh. Một số giá trị là ước tính có ghi nguồn; dữ liệu thiếu giữ chưa biết. Món chưa đủ dữ liệu không dùng để sinh thực đơn.");
            dish.setCaloriesKcal(n.caloriesKcal()); dish.setTotalCarbG(n.totalCarbG()); dish.setFiberG(n.fiberG());
            dish.setNetCarbG(n.netCarbG()); dish.setProteinG(n.proteinG()); dish.setFatG(n.fatG());
            dish.setSaturatedFatG(n.saturatedFatG()); dish.setCholesterolMg(n.cholesterolMg()); dish.setSodiumMg(n.sodiumMg());
            dish.setPotassiumMg(n.potassiumMg()); dish.setPurineMg(n.purineMg()); dish.setEstimatedGi(n.estimatedGi());
            dish.setGiLevel(GiLevel.fromValue(n.estimatedGi())); dish.setGlycemicLoad(n.glycemicLoad());
            dish.setMedicalTags(sourceTags(n));
            for (MedicalTag tag:MedicalTag.values()) if(tag.name().startsWith("ALLERGEN_") && "PRESENT".equals(CatalogNutritionQuality.allergenStatus(dish,tag))) dish.getMedicalTags().add(tag);
            dish.setLockManualTags(true);
            IngredientGroup primary = IngredientGroup.MIXED;
            for (SourceRecipe item : row.recipeItems()) {
                Ingredient identity = identities.get(item.ingredientCode());
                if (identity == null) throw new IllegalArgumentException("Unknown source ingredient " + item.ingredientCode());
                if (item.mainIngredient()) primary = identity.getIngredientGroup();
                dish.getIngredients().add(DishIngredient.builder().dish(dish).ingredient(identity)
                        .rawWeightG(item.rawWeightG()).appliedYieldFactor(null).cookedWeightG(null)
                        .isMainIngredient(item.mainIngredient()).sourceAmountText(item.sourceAmountText()).build());
            }
            dish.setPrimaryIngredientGroup(primary);
            // A fixture cannot force eligibility when required numeric evidence is absent.
            if (!CatalogNutritionQuality.generalEligible(dish)) dish.setSolverEligible(false);
            dishes.save(dish);
        }
        return file.dishes().size();
    }

    private static boolean equalNumber(BigDecimal a,BigDecimal b) { return a==null ? b==null : b!=null && a.compareTo(b)==0; }
    private static boolean unchanged(Dish d,SourceDish row) {
        if (d.getId()==null || !Boolean.TRUE.equals(d.getIsActive()) || !Objects.equals(d.getSolverEligible(),row.solverEligible())
                || !Objects.equals(d.getSourceRecordId(),row.sourceRecordId()) || !Objects.equals(d.getSourceUrl(),row.sourceUrl())
                || !Objects.equals(d.getNutritionQualityJson(),row.qualityJson()) || !Objects.equals(d.getNutritionBasis(),row.nutritionBasis())
                || !Objects.equals(d.getImageUrl(),row.imageUrl()) || !Objects.equals(d.getNameVi(),row.nameVi())
                || d.getDishRole()!=row.dishRole() || d.getCookingMethod()!=row.cookingMethod() || !Objects.equals(d.getMealSlots(),row.mealSlots())
                || !Objects.equals(d.getHouseholdUnitLabel(),row.householdUnitLabel())
                || !Objects.equals(d.getMinServingScaledX2(),row.minServingScaledX2()) || !Objects.equals(d.getMaxServingScaledX2(),row.maxServingScaledX2())
                || !Objects.equals(d.getStepServingScaledX2(),row.stepServingScaledX2()) || d.getIngredients().size()!=row.recipeItems().size()) return false;
        Nutrition n=row.nutrition();
        BigDecimal[] actual={d.getCaloriesKcal(),d.getTotalCarbG(),d.getFiberG(),d.getNetCarbG(),d.getProteinG(),d.getFatG(),d.getSodiumMg(),d.getEstimatedGi(),d.getGlycemicLoad(),d.getCholesterolMg(),d.getSaturatedFatG(),d.getPotassiumMg(),d.getPurineMg()};
        BigDecimal[] expected={n.caloriesKcal(),n.totalCarbG(),n.fiberG(),n.netCarbG(),n.proteinG(),n.fatG(),n.sodiumMg(),n.estimatedGi(),n.glycemicLoad(),n.cholesterolMg(),n.saturatedFatG(),n.potassiumMg(),n.purineMg()};
        for(int i=0;i<actual.length;i++) if(!equalNumber(actual[i],expected[i])) return false;
        return row.recipeItems().stream().allMatch(r -> d.getIngredients().stream().anyMatch(i ->
                Objects.equals(i.getIngredient().getVfcCode(),r.ingredientCode()) && equalNumber(i.getRawWeightG(),r.rawWeightG())
                && Objects.equals(i.getSourceAmountText(),r.sourceAmountText()) && Objects.equals(i.getIsMainIngredient(),r.mainIngredient())));
    }

    private static void requireCode(String code) {
        if (code == null || !code.matches("NIN_(REAL|BULK)_[A-Za-z0-9_]+")) throw new IllegalArgumentException("Dedicated source code required");
    }

    private static Set<MedicalTag> sourceTags(Nutrition n) {
        Set<MedicalTag> tags = new HashSet<>();
        if (above(n.estimatedGi(), "69.99")) tags.add(MedicalTag.HIGH_GI);
        if (above(n.glycemicLoad(), "19.99")) tags.add(MedicalTag.HIGH_GL);
        if (above(n.sodiumMg(), "600")) tags.add(MedicalTag.HIGH_SODIUM);
        if (above(n.saturatedFatG(), "5") || above(n.cholesterolMg(), "100")) tags.add(MedicalTag.HIGH_SATURATED_FAT);
        if (above(n.purineMg(), "150")) tags.add(MedicalTag.HIGH_PURINE);
        if (above(n.potassiumMg(), "400")) tags.add(MedicalTag.HIGH_POTASSIUM);
        return tags;
    }
    private static boolean above(BigDecimal value, String limit) { return value != null && value.compareTo(new BigDecimal(limit)) > 0; }
}
