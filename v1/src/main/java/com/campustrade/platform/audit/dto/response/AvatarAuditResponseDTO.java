package com.campustrade.platform.audit.dto.response;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;

import java.time.LocalDateTime;

public record AvatarAuditResponseDTO(
        Long userId,
        String nickname,
        String avatarUrl,
        ImageAuditStatusEnum auditStatus,
        String auditRemark,
        Long auditedBy,
        LocalDateTime auditedAt,
        LocalDateTime updatedAt
) {
}