package com.campustrade.platform.message.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequestDTO(
        @NotNull Long conversationId,
        @NotBlank @Size(max = 1000) String content
) {
}

