package com.campustrade.platform.announcement.dto.response;

public record AnnouncementPublicResponseDTO(
        String title,
        String content,
        long revision
) {
}
