package com.campustrade.platform.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WechatLoginRequestDTO(
        @NotBlank @Size(max = 256) String code
) {
}
