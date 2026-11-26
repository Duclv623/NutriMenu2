package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.entity.CuratedFallbackPlan;
import com.example.mealoptimizerservice.entity.MealSlotTemplate;
import com.example.mealoptimizerservice.entity.SolverWeightProfile;
import com.example.mealoptimizerservice.exception.ResourceNotFoundException;
import com.example.mealoptimizerservice.dao.repository.CuratedFallbackPlanRepository;
import com.example.mealoptimizerservice.dao.repository.MealSlotTemplateRepository;
import com.example.mealoptimizerservice.dao.repository.SolverWeightProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SolverConfigurationService {
    private final SolverWeightProfileRepository profileRepo;
    private final MealSlotTemplateRepository templateRepo;
    private final CuratedFallbackPlanRepository fallbackRepo;

    @Transactional(readOnly = true)
    public List<SolverWeightProfile> getAllWeightProfiles() {
        return profileRepo.findAll();
    }

    @Transactional
    public SolverWeightProfile createWeightProfile(SolverWeightProfile req) {
        return profileRepo.save(req);
    }

    @Transactional
    public SolverWeightProfile updateWeightProfile(Long id, SolverWeightProfile req) {
        SolverWeightProfile existing = profileRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình trọng số ID: " + id));
        existing.setNameVi(req.getNameVi());
        existing.setWeightCarbDeviation(req.getWeightCarbDeviation());
        existing.setWeightGlycemicLoad(req.getWeightGlycemicLoad());
        existing.setWeightCalorieDeviation(req.getWeightCalorieDeviation());
        existing.setWeightProteinFatDeviation(req.getWeightProteinFatDeviation());
        existing.setWeightFiberBonus(req.getWeightFiberBonus());
        existing.setWeightPreferenceBonus(req.getWeightPreferenceBonus());
        existing.setMaxTimeSeconds(req.getMaxTimeSeconds());
        existing.setNumSearchWorkers(req.getNumSearchWorkers());
        return profileRepo.save(existing);
    }

    @Transactional
    public SolverWeightProfile activateDefaultProfile(Long id) {
        List<SolverWeightProfile> all = profileRepo.findAll();
        SolverWeightProfile activated = null;
        for (SolverWeightProfile p : all) {
            p.setIsDefault(Objects.equals(p.getId(), id));
            if (p.getIsDefault()) activated = p;
        }
        profileRepo.saveAll(all);
        if (activated == null) {
            throw new ResourceNotFoundException("Không tìm thấy cấu hình trọng số ID: " + id);
        }
        return activated;
    }

    @Transactional(readOnly = true)
    public List<MealSlotTemplate> getMealTemplates(String templateCode) {
        return templateRepo.findByTemplateCodeOrderByIdAsc(templateCode);
    }

    @Transactional
    public MealSlotTemplate updateMealTemplate(Long id, MealSlotTemplate req) {
        MealSlotTemplate tpl = templateRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu bữa ăn ID: " + id));
        tpl.setCalorieRatioPct(req.getCalorieRatioPct());
        tpl.setMinNetCarbG(req.getMinNetCarbG());
        tpl.setMaxNetCarbG(req.getMaxNetCarbG());
        tpl.setMaxGl(req.getMaxGl());
        return templateRepo.save(tpl);
    }

    @Transactional
    public CuratedFallbackPlan createCuratedFallbackPlan(CuratedFallbackPlan plan) {
        return fallbackRepo.save(plan);
    }

    @Transactional(readOnly = true)
    public List<CuratedFallbackPlan> getAllCuratedFallbackPlans() {
        return fallbackRepo.findAllByOrderByTargetCalorieLevelAsc();
    }
}
