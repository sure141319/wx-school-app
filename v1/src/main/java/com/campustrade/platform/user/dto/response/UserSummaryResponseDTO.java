package com.campustrade.platform.user.dto.response;

public record UserSummaryResponseDTO(
        Long id,
        String nickname,
        String avatarUrl,
        String avatarSource
) {
}
