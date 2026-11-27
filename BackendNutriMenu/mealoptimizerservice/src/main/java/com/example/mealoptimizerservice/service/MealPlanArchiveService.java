package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.entity.*;
import com.example.mealoptimizerservice.dto.response.MealPlanResponse;
import com.example.mealoptimizerservice.dao.repository.MealPlanRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service @RequiredArgsConstructor
public class MealPlanArchiveService {
    private final MealPlanRevisionRepository revisions;
    private final ObjectMapper mapper;
    public void archive(MealPlan plan, Long previousProfileVersion) {
        Map<String,Object> policy=new LinkedHashMap<>();
        policy.put("evaluateFiber",plan.isEvaluateFiber()); policy.put("evaluateCholesterol",plan.isEvaluateCholesterol());
        policy.put("templateCode",plan.getTemplateCode()); policy.put("maxGiAllowed",plan.getMaxGiAllowed());
        policy.put("maxDailyGl",plan.getMaxDailyGl()); policy.put("maxDailySodiumMg",plan.getMaxDailySodiumMg());
        policy.put("maxDailyCholesterolMg",plan.getMaxDailyCholesterolMg()); policy.put("minDailyFiberG",plan.getMinDailyFiberG());
        policy.put("excludedDishIds",plan.getExcludedDishIds()); policy.put("contraindications",plan.getContraindications());
        policy.put("changePolicyRecorded",plan.getChangePolicyRecorded());
        Map<String,Object> snapshot=new LinkedHashMap<>();
        snapshot.put("mealPlan",MealPlanResponse.fromEntity(plan,null,null)); snapshot.put("policy",policy);
        snapshot.put("profileVersion",previousProfileVersion); snapshot.put("createdAt",plan.getCreatedAt());
        revisions.save(MealPlanRevision.builder().userId(plan.getUserId()).planId(plan.getId()).originalVersion(plan.getVersion())
                .snapshotJson(mapper.writeValueAsString(snapshot)).build());
    }
    public List<MealPlanRevision> list(Long userId,Long planId) {
        return revisions.findByUserIdAndPlanIdOrderByArchivedAtDesc(userId,planId);
    }
}
