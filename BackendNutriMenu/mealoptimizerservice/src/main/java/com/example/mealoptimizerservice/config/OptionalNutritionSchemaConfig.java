package com.example.mealoptimizerservice.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Additive startup migration: unknown optional nutrients stay NULL in existing databases. */
@Configuration(proxyBeanMethods=false)
public class OptionalNutritionSchemaConfig {
    @Bean @Order(5)
    CommandLineRunner optionalNutritionSchema(JdbcTemplate jdbc,PlatformTransactionManager transactions) {
        return args -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.execute("SELECT pg_advisory_xact_lock(8132, 2)");
            for (String column:new String[]{"actual_fiber_g","actual_total_gl","actual_cholesterol_mg"})
                jdbc.execute("ALTER TABLE meal_plans ALTER COLUMN "+column+" DROP NOT NULL");
            for (String column:new String[]{"snapshot_fiber_g","snapshot_gl","snapshot_cholesterol_mg"})
                jdbc.execute("ALTER TABLE meal_plan_items ALTER COLUMN "+column+" DROP NOT NULL");
            jdbc.execute("ALTER TABLE meal_plans ADD COLUMN IF NOT EXISTS evaluate_fiber boolean NOT NULL DEFAULT true");
            jdbc.execute("ALTER TABLE meal_plans ADD COLUMN IF NOT EXISTS evaluate_cholesterol boolean NOT NULL DEFAULT true");
            jdbc.execute("""
                    DO $general_optional_status$
                    DECLARE item record;
                    BEGIN
                        FOR item IN
                            SELECT n.nspname AS schema_name, t.relname AS table_name, c.conname AS constraint_name
                            FROM pg_constraint c
                            JOIN pg_class t ON t.oid = c.conrelid
                            JOIN pg_namespace n ON n.oid = t.relnamespace
                            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
                            WHERE c.contype = 'c'
                              AND c.conrelid IN (to_regclass('meal_plans'), to_regclass('solver_audit_logs'))
                              AND array_length(c.conkey, 1) = 1 AND a.attname = 'solver_status'
                              AND pg_get_constraintdef(c.oid) LIKE '%''OPTIMAL''%'
                              AND pg_get_constraintdef(c.oid) LIKE '%''FEASIBLE''%'
                              AND pg_get_constraintdef(c.oid) LIKE '%''INFEASIBLE''%'
                              AND pg_get_constraintdef(c.oid) LIKE '%''MODEL_INVALID''%'
                              AND pg_get_constraintdef(c.oid) LIKE '%''FALLBACK_CURATED''%'
                              AND pg_get_constraintdef(c.oid) NOT LIKE '%''UNKNOWN''%'
                        LOOP
                            EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I', item.schema_name, item.table_name, item.constraint_name);
                            EXECUTE format('ALTER TABLE %I.%I ADD CONSTRAINT %I CHECK (solver_status IN (''OPTIMAL'', ''FEASIBLE'', ''INFEASIBLE'', ''UNKNOWN'', ''MODEL_INVALID'', ''FALLBACK_CURATED''))', item.schema_name, item.table_name, item.constraint_name);
                        END LOOP;
                    END;
                    $general_optional_status$;
                    """);
        });
    }
}
