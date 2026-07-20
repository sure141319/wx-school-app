package com.campustrade.platform.audit.dataobject;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AuditImageRecordDO {

    private Long imageId;
    private Long goodsId;
    private String goodsTitle;
    private String goodsDescription;
    private Long sellerId;
    private String sellerNickname;
    private String sellerWechatId;
    private String sellerQq;
    private String imageUrl;
    private String thumbnailUrl;
    private String displayUrl;
    private String auditThumbnailUrl;
    private Integer sortOrder;
    private ImageAuditStatusEnum auditStatus;
    private String auditRemark;
    private Long auditedBy;
    private LocalDateTime auditedAt;
    private LocalDateTime createdAt;
}
