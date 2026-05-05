package com.campustrade.platform.message.dto.request;

import jakarta.validation.constraints.NotNull;

public record StartConversationRequestDTO(@NotNull Long goodsId) {
}

