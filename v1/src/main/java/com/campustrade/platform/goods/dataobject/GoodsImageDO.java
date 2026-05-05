package com.campustrade.platform.goods.dataobject;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class GoodsImageDO {

    private Long id;
    private Long goodsId;
    private String imageUrl;
    private Integer sortOrder = 0;
    private ImageAuditStatusEnum auditStatus = ImageAuditStatusEnum.PENDING;
    private String auditRemark;
    private Long auditedBy;
    private LocalDateTime auditedAt;
    private LocalDateTime createdAt;
}
