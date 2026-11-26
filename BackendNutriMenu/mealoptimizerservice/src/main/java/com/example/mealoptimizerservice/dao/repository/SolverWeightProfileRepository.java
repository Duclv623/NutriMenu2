package com.example.mealoptimizerservice.dao.repository;

import com.example.mealoptimizerservice.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SolverWeightProfileRepository extends JpaRepository<SolverWeightProfile, Long> {
    Optional<SolverWeightProfile> findFirstByIsDefaultTrue();
}
