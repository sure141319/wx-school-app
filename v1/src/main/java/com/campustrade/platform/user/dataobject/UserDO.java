package com.campustrade.platform.user.dataobject;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UserDO {

    private Long id;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private ImageAuditStatusEnum avatarAuditStatus = ImageAuditStatusEnum.APPROVED;
    private String avatarAuditRemark;
    private Long avatarAuditedBy;
    private LocalDateTime avatarAuditedAt;
    private Integer failedLoginCount = 0;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
