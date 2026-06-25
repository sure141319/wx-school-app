package com.campustrade.platform.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequestDTO(
        @NotBlank @Size(max = 64) String nickname,
        @Size(max = 500) String avatarUrl,
        @Size(max = 64) String wechatId,
        @Pattern(regexp = "^$|^\\d{5,12}$", message = "QQ号需为5-12位数字") String qq
) {
}

