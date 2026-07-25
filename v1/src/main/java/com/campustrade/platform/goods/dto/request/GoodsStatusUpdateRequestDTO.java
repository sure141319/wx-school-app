package com.campustrade.platform.goods.dto.request;

import com.campustrade.platform.goods.enums.GoodsStatusEnum;

import jakarta.validation.constraints.NotNull;

public record GoodsStatusUpdateRequestDTO(
        @NotNull(message = "商品状态不能为空") GoodsStatusEnum status
) {
}

