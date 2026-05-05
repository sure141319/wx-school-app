package com.campustrade.platform.audit.dto.response;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;

import java.time.LocalDateTime;

public record AuditImageResponseDTO(
        Long imageId,
        Long goodsId,
        String goodsTitle,
        String goodsDescription,
        Long sellerId,
        String sellerNickname,
        String originalImageUrl,
        Integer sortOrder,
        ImageAuditStatusEnum auditStatus,
        String auditRemark,
        Long auditedBy,
        LocalDateTime auditedAt,
        LocalDateTime createdAt
) {
}
