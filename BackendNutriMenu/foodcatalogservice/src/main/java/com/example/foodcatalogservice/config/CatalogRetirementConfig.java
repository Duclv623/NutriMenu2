package com.example.foodcatalogservice.config;

import com.example.foodcatalogservice.service.CatalogRetirementService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Runs independently of seed-enabled, so existing databases cannot expose QA rows. */
@Configuration(proxyBeanMethods = false)
public class CatalogRetirementConfig {
    @Bean
    @Order(30)
    CommandLineRunner retireLegacyCatalogChoices(CatalogRetirementService retirement) {
        return args -> retirement.retireNonSourceChoices();
    }
}
