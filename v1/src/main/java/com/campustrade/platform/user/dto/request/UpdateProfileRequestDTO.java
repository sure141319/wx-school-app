package com.campustrade.platform.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequestDTO(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称不能超过64个字符") String nickname,
        @Size(max = 500, message = "头像地址过长") String avatarUrl,
        @Size(max = 64, message = "微信号不能超过64个字符") String wechatId,
        @Pattern(regexp = "^$|^\\d{5,12}$", message = "QQ号需为5-12位数字") String qq
) {
}

