package com.campustrade.platform.auth.dto.request;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendCodeRequestDTO(
        @NotBlank @Email @Pattern(regexp = "^[A-Za-z0-9._%+-]+@qq\\.com$",
                message = "只支持QQ邮箱") String email,
        VerificationPurposeEnum purpose
) {
}
