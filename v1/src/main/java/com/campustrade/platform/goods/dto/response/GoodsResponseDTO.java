package com.campustrade.platform.goods.dto.response;

import com.campustrade.platform.category.dto.response.CategoryResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GoodsResponseDTO(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String conditionLevel,
        String campusLocation,
        GoodsStatusEnum status,
        CategoryResponseDTO category,
        UserProfileResponseDTO seller,
        List<String> imageUrls,
        String auditRemark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

