package com.campustrade.platform.goods.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record GoodsSaveRequestDTO(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        @NotBlank @Size(max = 50) String conditionLevel,
        @NotBlank @Size(max = 120) String campusLocation,
        Long categoryId,
        @NotEmpty @Size(max = 9) List<@NotBlank @Size(max = 500) String> imageUrls
) {
}

