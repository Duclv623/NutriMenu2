package com.example.foodcatalogservice.service;

import com.example.foodcatalogservice.entity.Dish;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Current source identities only, enforced even when startup import is disabled. */
public final class CatalogSourcePolicy {
    private static final SourceCatalogImportService.CatalogFile SOURCE = load();
    private static final Map<String, String> RECORDS = SOURCE.dishes().stream().collect(
            Collectors.toUnmodifiableMap(SourceCatalogImportService.SourceDish::code,
                    SourceCatalogImportService.SourceDish::sourceRecordId));
    private static final Set<String> INGREDIENTS = SOURCE.ingredients().stream()
            .map(SourceCatalogImportService.SourceIngredient::code).collect(Collectors.toUnmodifiableSet());
    public static final String SOURCE_URL = "https://viendinhduong.vn/vi/cong-cu-va-tien-ich/gia-tri-dinh-duong-mon-an";

    private CatalogSourcePolicy() {}
    public static String datasetId() { return SOURCE.datasetId(); }
    public static Set<String> dishCodes() { return RECORDS.keySet(); }
    public static Set<String> dishIdentities() {
        return RECORDS.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.toUnmodifiableSet());
    }
    public static Set<String> ingredientCodes() { return INGREDIENTS; }
    public static boolean isCurrentSource(Dish dish) {
        return dish != null && dish.getCode() != null && RECORDS.containsKey(dish.getCode())
                && RECORDS.get(dish.getCode()).equals(dish.getSourceRecordId())
                && SOURCE_URL.equals(dish.getSourceUrl()) && dish.getCreatedByUserId() == null
                && dish.getNutritionQualityJson() != null && !dish.getNutritionQualityJson().isBlank();
    }
    private static SourceCatalogImportService.CatalogFile load() {
        try (var stream = CatalogSourcePolicy.class.getResourceAsStream("/catalog/nin-bulk-20261126/nin-source-import.json")) {
            if (stream == null) throw new IllegalStateException("Current NIN source catalog is missing");
            return new ObjectMapper().readValue(stream, SourceCatalogImportService.CatalogFile.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
}
