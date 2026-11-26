package com.example.mealoptimizerservice.controller;

import com.example.mealoptimizerservice.dto.*;
import com.example.mealoptimizerservice.entity.*;
import com.example.mealoptimizerservice.service.MealOptimizerService;
import com.example.mealoptimizerservice.service.SolverConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.example.mealoptimizerservice.dto.request.*;
import com.example.mealoptimizerservice.dto.response.*;

// --- FILE: AdminOptimizerController.java ---
@RestController
@RequestMapping("/api/v1/admin/optimizer")
@RequiredArgsConstructor
public class AdminOptimizerController {

    private final MealOptimizerService optimizerService;
    private final SolverConfigurationService configurationService;

    @GetMapping("/profiles")
    public ResponseEntity<List<SolverWeightProfile>> getProfiles() {
        return ResponseEntity.ok(configurationService.getAllWeightProfiles());
    }

    @PostMapping("/profiles")
    public ResponseEntity<SolverWeightProfile> createProfile(@RequestBody @Valid SolverWeightProfile req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configurationService.createWeightProfile(req));
    }

    @PutMapping("/profiles/{id}")
    public ResponseEntity<SolverWeightProfile> updateProfile(
            @PathVariable Long id, @RequestBody @Valid SolverWeightProfile req) {
        return ResponseEntity.ok(configurationService.updateWeightProfile(id, req));
    }

    @PatchMapping("/profiles/{id}/activate")
    public ResponseEntity<SolverWeightProfile> activateProfile(@PathVariable Long id) {
        return ResponseEntity.ok(configurationService.activateDefaultProfile(id));
    }

    @GetMapping("/meal-templates")
    public ResponseEntity<List<MealSlotTemplate>> getMealTemplates(
            @RequestParam(defaultValue = "STANDARD_4_MEALS") String templateCode) {
        return ResponseEntity.ok(configurationService.getMealTemplates(templateCode));
    }

    @PutMapping("/meal-templates/{id}")
    public ResponseEntity<MealSlotTemplate> updateMealTemplate(
            @PathVariable Long id, @RequestBody @Valid MealSlotTemplate req) {
        return ResponseEntity.ok(configurationService.updateMealTemplate(id, req));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<SolverAuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(optimizerService.getAuditLogs(page, size));
    }

    @GetMapping("/metrics/summary")
    public ResponseEntity<SolverMetricsSummaryResponse> getMetricsSummary() {
        return ResponseEntity.ok(optimizerService.getMetricsSummary());
    }

    @PostMapping("/curated-plans")
    public ResponseEntity<CuratedFallbackPlan> createCuratedPlan(@RequestBody @Valid CuratedFallbackPlan plan) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configurationService.createCuratedFallbackPlan(plan));
    }

    @GetMapping("/curated-plans")
    public ResponseEntity<List<CuratedFallbackPlan>> getCuratedPlans() {
        return ResponseEntity.ok(configurationService.getAllCuratedFallbackPlans());
    }

    @PostMapping("/dry-run")
    public ResponseEntity<MealPlanResponse> executeDryRun(@RequestBody @Valid GenerateDailyMealRequest req) {
        return ResponseEntity.ok(optimizerService.executeDryRunSandbox(req));
    }

    @PostMapping("/benchmark-profiles")
    public ResponseEntity<BenchmarkComparisonResponse> benchmarkProfiles(
            @RequestBody @Valid BenchmarkComparisonRequest req) {
        return ResponseEntity.ok(optimizerService.benchmarkTwoProfiles(req));
    }

    @PostMapping("/diagnostics/explain-infeasible")
    public ResponseEntity<InfeasibilityDiagnosisResponse> explainInfeasible(
            @RequestBody @Valid GenerateDailyMealRequest req) {
        return ResponseEntity.ok(optimizerService.explainInfeasibility(req));
    }
}
