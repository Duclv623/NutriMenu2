package com.example.mealoptimizerservice.dao.repository;
import com.example.mealoptimizerservice.entity.MealPlanRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MealPlanRevisionRepository extends JpaRepository<MealPlanRevision,Long> {
    List<MealPlanRevision> findByUserIdAndPlanIdOrderByArchivedAtDesc(Long userId,Long planId);
}
