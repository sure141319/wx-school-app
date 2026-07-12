package com.campustrade.platform.user.dto.response;

public record PublicSellerResponseDTO(
        Long id,
        String nickname,
        String avatarUrl,
        String avatarSource,
        String wechatId,
        String qq
) {
}
