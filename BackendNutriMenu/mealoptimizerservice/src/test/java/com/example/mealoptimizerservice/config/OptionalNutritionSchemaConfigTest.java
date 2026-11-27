package com.example.mealoptimizerservice.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SQL boundary only; PostgreSQL execution is verified by deployment smoke checks. */
class OptionalNutritionSchemaConfigTest {
    @Test void additiveMigrationPreservesOtherChecksAndQuotesStatusConstraintNames() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var transactions=mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        new OptionalNutritionSchemaConfig().optionalNutritionSchema(jdbc,transactions).run();
        var sql=ArgumentCaptor.forClass(String.class);verify(jdbc,times(10)).execute(sql.capture());
        String status=sql.getAllValues().stream().filter(s -> s.contains("pg_constraint")).findFirst().orElseThrow();
        assertTrue(status.contains("c.contype = 'c'"));assertTrue(status.contains("array_length(c.conkey, 1) = 1"));
        assertTrue(status.contains("a.attname = 'solver_status'"));
        assertTrue(status.contains("to_regclass('meal_plans'), to_regclass('solver_audit_logs')"));
        for(String value:new String[]{"OPTIMAL","FEASIBLE","INFEASIBLE","MODEL_INVALID","FALLBACK_CURATED"})
            assertTrue(status.contains("LIKE '%''"+value+"''%'"));
        assertTrue(status.contains("NOT LIKE '%''UNKNOWN''%'"));
        assertTrue(status.contains("ALTER TABLE %I.%I DROP CONSTRAINT %I"));
        assertTrue(status.contains("ALTER TABLE %I.%I ADD CONSTRAINT %I CHECK (solver_status IN"));
        assertFalse(status.contains("CASCADE"));assertFalse(status.contains("DROP TABLE"));
        assertEquals(6,sql.getAllValues().stream().filter(s -> s.contains("DROP NOT NULL")).count());
        verify(transactions).commit(any());
    }
}
