package com.campustrade.platform.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequestDTO(
        @NotBlank @Size(max = 64) String nickname,
        @Size(max = 500) String avatarUrl
) {
}

