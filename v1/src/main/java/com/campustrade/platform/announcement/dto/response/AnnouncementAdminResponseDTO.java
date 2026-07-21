package com.campustrade.platform.announcement.dto.response;

import java.time.LocalDateTime;

public record AnnouncementAdminResponseDTO(
        String title,
        String content,
        boolean enabled,
        long revision,
        LocalDateTime updatedAt
) {
}
