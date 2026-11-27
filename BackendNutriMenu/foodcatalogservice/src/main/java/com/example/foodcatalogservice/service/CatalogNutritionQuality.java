package com.example.foodcatalogservice.service;

import com.example.foodcatalogservice.entity.Dish;
import com.example.foodcatalogservice.entity.enums.MedicalTag;
import com.example.foodcatalogservice.entity.enums.PlanningMode;
import tools.jackson.databind.ObjectMapper;
import com.example.foodcatalogservice.exception.BusinessRuleException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;

/** Quality checks before incomplete source values can reach solver arithmetic. */
public final class CatalogNutritionQuality {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private CatalogNutritionQuality() {}

    public static boolean baseEligible(Dish d) {
        return !Boolean.FALSE.equals(d.getSolverEligible()) && completeNutrition(d);
    }

    /** Historical reads still require numeric evidence but do not reactivate a retired row. */
    public static boolean completeCoreNutrition(Dish d) {
        if (Arrays.stream(new BigDecimal[]{d.getCaloriesKcal(),d.getNetCarbG(),d.getProteinG(),d.getFatG(),d.getSodiumMg()})
                .anyMatch(v -> v==null || v.signum()<0) || d.getCaloriesKcal().signum()==0) return false;
        return d.getSourceRecordId()==null || (Boolean.TRUE.equals(d.getPortionEvidenceComplete())
                && d.getNutritionQualityJson()!=null && !d.getNutritionQualityJson().isBlank()
                && d.getNutritionBasis()!=null && !d.getIngredients().isEmpty()
                && d.getIngredients().stream().noneMatch(i -> i.getRawWeightG()==null || i.getRawWeightG().signum()<=0));
    }
    public static boolean generalEligible(Dish d) {
        if (Boolean.FALSE.equals(d.getSolverEligible()) || !completeCoreNutrition(d)) return false;
        try { return completeNutrition(d) || MAPPER.readTree(d.getNutritionQualityJson()).path("generalEligible").asBoolean(false); }
        catch(Exception error) { return false; }
    }
    public static String allergenStatus(Dish d, MedicalTag allergen) {
        try {
            var evidence=MAPPER.readTree(d.getNutritionQualityJson()).path("perAllergenEvidence");
            if (!evidence.isMissingNode()) return evidence.path(allergen.name()).path("status").asString("UNKNOWN");
        } catch(Exception ignored) { return "UNKNOWN"; }
        return Boolean.TRUE.equals(d.getAllergenEvidenceComplete()) && !d.getMedicalTags().contains(allergen) ? "ABSENT_CONFIRMED" : "UNKNOWN";
    }

    public static boolean completeNutrition(Dish d) {
        if (Arrays.stream(new BigDecimal[]{d.getCaloriesKcal(), d.getNetCarbG(), d.getFiberG(),
                d.getProteinG(), d.getFatG(), d.getEstimatedGi(), d.getGlycemicLoad(),
                d.getSodiumMg(), d.getCholesterolMg()}).anyMatch(v -> v == null || v.signum() < 0)) return false;
        if (d.getEstimatedGi().compareTo(new BigDecimal("100")) > 0) return false;
        if (d.getSourceRecordId() != null && (!Boolean.TRUE.equals(d.getPortionEvidenceComplete())
                || d.getNutritionQualityJson() == null || d.getNutritionQualityJson().isBlank()
                || d.getNutritionBasis() == null || d.getIngredients().isEmpty() || d.getIngredients().stream()
                .anyMatch(i -> i.getRawWeightG() == null || i.getRawWeightG().signum() <= 0))) return false;
        return true;
    }

    public static boolean eligibleFor(Dish d, Set<MedicalTag> excluded) { return eligibleFor(d,excluded,PlanningMode.MEDICAL); }
    public static boolean eligibleFor(Dish d, Set<MedicalTag> excluded, PlanningMode mode) {
        if (mode==PlanningMode.GENERAL_REFERENCE ? !generalEligible(d) : !baseEligible(d)) return false;
        if (excluded == null) return true;
        if (excluded.contains(MedicalTag.HIGH_PURINE) && d.getPurineMg() == null) return false;
        if (excluded.contains(MedicalTag.HIGH_POTASSIUM) && d.getPotassiumMg() == null) return false;
        if (excluded.contains(MedicalTag.HIGH_SATURATED_FAT) && d.getSaturatedFatG() == null) return false;
        return excluded.stream().filter(t -> t.name().startsWith("ALLERGEN_"))
                .allMatch(t -> "ABSENT_CONFIRMED".equals(allergenStatus(d,t)));
    }

    public static void requireCompleteSolverNutrition(Dish d) {
        if (!baseEligible(d)) throw new BusinessRuleException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Món nguồn chưa đủ dữ liệu khẩu phần/GI/dinh dưỡng để tính hoặc đưa vào solver. Xem thông tin chất lượng dữ liệu.");
    }
}
