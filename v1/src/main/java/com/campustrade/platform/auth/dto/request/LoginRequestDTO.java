package com.campustrade.platform.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @NotBlank @Email @Pattern(regexp = "^[A-Za-z0-9._%+-]+@qq\\.com$",
                message = "只支持QQ邮箱") String email,
        @NotBlank @Size(min = 6, max = 64) String password
) {
}

