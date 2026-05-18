package com.campustrade.platform.goods.dto.response;

import com.campustrade.platform.category.dto.response.CategorySummaryResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.user.dto.response.UserSummaryResponseDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GoodsListItemResponseDTO(
        Long id,
        String title,
        BigDecimal price,
        String conditionLevel,
        String campusLocation,
        GoodsStatusEnum status,
        CategorySummaryResponseDTO category,
        UserSummaryResponseDTO seller,
        String coverImageUrl,
        LocalDateTime createdAt
) {
}
