package com.campustrade.platform.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BindEmailRequestDTO(
        @NotBlank(message = "QQ邮箱不能为空")
        @Email(message = "QQ邮箱格式不正确")
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@qq\\.com$",
                message = "只支持QQ邮箱") String email,
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码必须为6位数字") String code,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码需为6-64位") String password
) {
}
