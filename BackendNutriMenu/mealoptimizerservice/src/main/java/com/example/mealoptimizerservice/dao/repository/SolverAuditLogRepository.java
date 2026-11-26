package com.example.mealoptimizerservice.dao.repository;

import com.example.mealoptimizerservice.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SolverAuditLogRepository extends JpaRepository<SolverAuditLog, Long> {
    Page<SolverAuditLog> findAllByOrderByExecutedAtDesc(Pageable pageable);
}
