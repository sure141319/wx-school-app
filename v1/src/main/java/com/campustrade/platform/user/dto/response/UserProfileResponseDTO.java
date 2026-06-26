package com.campustrade.platform.user.dto.response;

public record UserProfileResponseDTO(
        Long id,
        String email,
        String nickname,
        String avatarUrl,
        String wechatOpenid,
        String wechatId,
        String qq,
        String avatarSource
) {
}

