package com.campustrade.platform.audit.dto.response;

public record ThumbnailBackfillResponseDTO(
        int processed,
        int generated,
        int skipped,
        int failed,
        long remaining
) {
}
