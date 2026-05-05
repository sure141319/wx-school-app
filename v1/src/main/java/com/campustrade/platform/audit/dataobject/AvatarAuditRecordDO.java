package com.campustrade.platform.audit.dataobject;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AvatarAuditRecordDO {

    private Long userId;
    private String nickname;
    private String avatarUrl;
    private ImageAuditStatusEnum avatarAuditStatus;
    private String avatarAuditRemark;
    private Long avatarAuditedBy;
    private LocalDateTime avatarAuditedAt;
    private LocalDateTime updatedAt;
}