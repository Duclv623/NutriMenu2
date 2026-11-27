package com.example.foodcatalogservice.config;

import com.example.foodcatalogservice.service.SourceCatalogImportService;
import com.example.foodcatalogservice.service.CatalogRetirementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.catalog", name = "seed-enabled", havingValue = "true", matchIfMissing = true)
public class SourceCatalogSeederConfig {
    private static final String ROOT = "catalog/nin-bulk-20261126/";
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final SourceCatalogImportService importer;
    private final CatalogRetirementService retirement;

    @Bean
    @Order(20)
    public CommandLineRunner importReviewedNinSourceCatalog() {
        return args -> {
            SourceCatalogImportService.CatalogFile file;
            String migration;
            try (var stream = new ClassPathResource(ROOT + "nin-source-import.json").getInputStream()) {
                file = objectMapper.readValue(stream, SourceCatalogImportService.CatalogFile.class);
            }
            try (var stream = new ClassPathResource("catalog/nin-real-20261123/nullable-source-migration.sql").getInputStream()) {
                migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.execute("SELECT pg_advisory_xact_lock(8132, 1)");
                for (String statement : migration.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
                int imported = importer.importCatalog(file);
                retirement.retireNonSourceChoices();
                log.info("NIN source catalog {} imported {} source records; public visibility and solver eligibility follow data quality; legacy choices retired; NIN-only runtime with no QA backfill", file.datasetId(), imported);
            });
        };
    }
}
