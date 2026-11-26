package com.example.mealoptimizerservice.dao.repository;

import com.example.mealoptimizerservice.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CuratedFallbackPlanRepository extends JpaRepository<CuratedFallbackPlan, Long> {
    List<CuratedFallbackPlan> findAllByOrderByTargetCalorieLevelAsc();
}
