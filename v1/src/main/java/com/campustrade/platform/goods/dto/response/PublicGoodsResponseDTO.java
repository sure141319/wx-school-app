package com.campustrade.platform.goods.dto.response;

import com.campustrade.platform.category.dto.response.CategoryResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.user.dto.response.PublicSellerResponseDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PublicGoodsResponseDTO(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String conditionLevel,
        String campusLocation,
        GoodsStatusEnum status,
        CategoryResponseDTO category,
        PublicSellerResponseDTO seller,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
