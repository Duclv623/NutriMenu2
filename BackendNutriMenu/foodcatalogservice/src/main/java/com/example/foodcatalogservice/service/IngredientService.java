package com.example.foodcatalogservice.service;

import com.example.foodcatalogservice.dto.request.UpsertIngredientRequest;
import com.example.foodcatalogservice.dto.request.UpsertYieldFactorRequest;
import com.example.foodcatalogservice.dto.response.BulkImportReportResponse;
import com.example.foodcatalogservice.dto.response.CascadeRecalculationResponse;
import com.example.foodcatalogservice.entity.Dish;
import com.example.foodcatalogservice.entity.Ingredient;
import com.example.foodcatalogservice.entity.YieldRetentionFactor;
import com.example.foodcatalogservice.entity.enums.GiLevel;
import com.example.foodcatalogservice.entity.enums.IngredientGroup;
import com.example.foodcatalogservice.exception.BusinessRuleException;
import com.example.foodcatalogservice.exception.ResourceNotFoundException;
import com.example.foodcatalogservice.dao.repository.DishIngredientRepository;
import com.example.foodcatalogservice.dao.repository.DishRepository;
import com.example.foodcatalogservice.dao.repository.IngredientRepository;
import com.example.foodcatalogservice.dao.repository.YieldFactorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IngredientService {
    private final IngredientRepository ingredientRepository;
    private final DishIngredientRepository dishIngredientRepository;
    private final DishRepository dishRepository;
    private final YieldFactorRepository yieldFactorRepository;
    private final NutritionCalculationEngine calculationEngine;

    @Transactional
    public Ingredient createIngredient(UpsertIngredientRequest req) {
        if (ingredientRepository.findByVfcCode(req.getVfcCode()).isPresent()) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Mã VFC đã tồn tại: " + req.getVfcCode());
        }
        Ingredient ing = new Ingredient();
        applyIngredientFields(ing, req);
        return ingredientRepository.save(ing);
    }

    @Transactional
    public Ingredient updateIngredientAndCascade(Long id, UpsertIngredientRequest req) {
        Ingredient ing = ingredientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nguyên liệu ID: " + id));
        applyIngredientFields(ing, req);
        Ingredient saved = ingredientRepository.save(ing);
        recalculateDishesContainingIngredient(id);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Ingredient> searchIngredients(IngredientGroup group, GiLevel giLevel, String keyword, int page, int size) {
        String normalizedKw = VietnameseTextUtils.removeAccents(keyword);
        return ingredientRepository.searchIngredients(group, giLevel, normalizedKw, PageRequest.of(page, size));
    }

    @Transactional
    public void deleteIngredientSafely(Long id) {
        if (dishIngredientRepository.existsByIngredientId(id)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT,
                    "Không thể xóa nguyên liệu này vì đang nằm trong công thức của một hoặc nhiều món ăn!");
        }
        ingredientRepository.deleteById(id);
    }

    @Transactional
    public BulkImportReportResponse bulkImportIngredients(List<UpsertIngredientRequest> items) {
        int inserted = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            UpsertIngredientRequest req = items.get(i);
            try {
                Optional<Ingredient> existing = ingredientRepository.findByVfcCode(req.getVfcCode());
                if (existing.isPresent()) {
                    Ingredient ing = existing.get();
                    applyIngredientFields(ing, req);
                    ingredientRepository.save(ing);
                    updated++;
                } else {
                    Ingredient ing = new Ingredient();
                    applyIngredientFields(ing, req);
                    ingredientRepository.save(ing);
                    inserted++;
                }
            } catch (Exception e) {
                errors.add("Dòng " + (i + 1) + " (" + req.getVfcCode() + "): " + e.getMessage());
            }
        }
        return BulkImportReportResponse.builder()
                .totalProcessed(items.size())
                .insertedCount(inserted)
                .updatedCount(updated)
                .errors(errors)
                .build();
    }

    @Transactional
    public YieldRetentionFactor upsertYieldFactor(UpsertYieldFactorRequest req) {
        YieldRetentionFactor yrf = YieldRetentionFactor.builder()
                .ingredientId(req.getIngredientId())
                .ingredientGroup(req.getIngredientGroup())
                .cookingMethod(req.getCookingMethod())
                .yieldFactor(req.getYieldFactor())
                .oilAbsorptionPct(req.getOilAbsorptionPct())
                .build();
        return yieldFactorRepository.save(yrf);
    }

    @Transactional(readOnly = true)
    public List<YieldRetentionFactor> getAllYieldFactors() {
        return yieldFactorRepository.findAll();
    }

    @Transactional
    public CascadeRecalculationResponse recalculateDishesContainingIngredient(Long ingredientId) {
        Ingredient ing = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nguyên liệu ID: " + ingredientId));

        List<Long> dishIds = dishIngredientRepository.findDishIdsByIngredientId(ingredientId);
        List<Dish> affectedDishes = dishRepository.findAllById(dishIds);
        List<String> updatedCodes = new ArrayList<>();

        for (Dish dish : affectedDishes) {
            calculationEngine.computeNutritionAndMedicalTags(dish, dish.getIngredients());
            dishRepository.save(dish);
            updatedCodes.add(dish.getCode());
        }
        return CascadeRecalculationResponse.builder()
                .ingredientId(ing.getId())
                .ingredientName(ing.getNameVi())
                .affectedDishesCount(updatedCodes.size())
                .updatedDishCodes(updatedCodes)
                .build();
    }

    private void applyIngredientFields(Ingredient ing, UpsertIngredientRequest req) {
        ing.setVfcCode(req.getVfcCode());
        ing.setNameVi(req.getNameVi());
        ing.setIngredientGroup(req.getIngredientGroup());
        if (req.getEdiblePortionPct() != null) ing.setEdiblePortionPct(req.getEdiblePortionPct());
        ing.setCaloriesPer100g(req.getCaloriesPer100g());
        ing.setTotalCarbG(req.getTotalCarbG());
        ing.setFiberG(req.getFiberG());
        ing.setProteinG(req.getProteinG());
        ing.setFatG(req.getFatG());
        ing.setSaturatedFatG(req.getSaturatedFatG() != null ? req.getSaturatedFatG() : BigDecimal.ZERO);
        ing.setCholesterolMg(req.getCholesterolMg() != null ? req.getCholesterolMg() : BigDecimal.ZERO);
        ing.setSodiumMg(req.getSodiumMg() != null ? req.getSodiumMg() : BigDecimal.ZERO);
        ing.setPotassiumMg(req.getPotassiumMg() != null ? req.getPotassiumMg() : BigDecimal.ZERO);
        ing.setPurineMg(req.getPurineMg() != null ? req.getPurineMg() : BigDecimal.ZERO);
        ing.setGiValue(req.getGiValue());
        ing.setAllergenTags(req.getAllergenTags() != null ? new HashSet<>(req.getAllergenTags()) : new HashSet<>());
    }
}
