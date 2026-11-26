package com.menufood.userservice.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CheckInRequestDTO(@NotNull UUID id, @NotNull LocalDate date, @DecimalMin("1") @DecimalMax("600") double weight,
                          @DecimalMin("0.1") @DecimalMax("100") Double glucose, @NotNull @Size(max=2000) String note) {}
