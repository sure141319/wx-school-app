package com.campustrade.platform.goods.dto.response;

import com.campustrade.platform.goods.enums.GoodsStatusEnum;

import java.math.BigDecimal;

public record MyGoodsListItemResponseDTO(
        Long id,
        String title,
        BigDecimal price,
        GoodsStatusEnum status,
        String auditRemark
) {
}
