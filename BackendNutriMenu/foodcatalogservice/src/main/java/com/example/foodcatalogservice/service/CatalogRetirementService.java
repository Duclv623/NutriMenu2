package com.example.foodcatalogservice.service;

import com.example.foodcatalogservice.dao.repository.DishRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reversible retirement: keep IDs and relationships for historical plans. */
@Service
@RequiredArgsConstructor
public class CatalogRetirementService {
    private final DishRepository dishes;

    @Transactional
    public int retireNonSourceChoices() {
        int retired = 0;
        for (var dish : dishes.findAll()) {
            if (!CatalogSourcePolicy.isCurrentSource(dish)
                    && (Boolean.TRUE.equals(dish.getIsActive()) || Boolean.TRUE.equals(dish.getSolverEligible()))) {
                dish.setIsActive(false);
                dish.setSolverEligible(false);
                dishes.save(dish);
                retired++;
            }
        }
        return retired;
    }
}
