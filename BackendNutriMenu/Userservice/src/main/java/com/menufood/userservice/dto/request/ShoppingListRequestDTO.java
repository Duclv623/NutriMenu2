package com.menufood.userservice.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public record ShoppingListRequestDTO(@NotNull @Size(max=2000) List<@NotBlank @Size(max=200) String> checkedIds) {}
