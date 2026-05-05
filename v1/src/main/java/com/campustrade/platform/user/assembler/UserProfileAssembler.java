package com.campustrade.platform.user.assembler;

import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UserProfileAssembler {

    private final UploadService uploadService;
    private final AppProperties appProperties;

    public UserProfileAssembler(UploadService uploadService, AppProperties appProperties) {
        this.uploadService = uploadService;
        this.appProperties = appProperties;
    }

    public UserProfileResponseDTO toResponse(UserDO user) {
        return new UserProfileResponseDTO(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                toVisibleAvatarUrl(user)
        );
    }

    private String toVisibleAvatarUrl(UserDO user) {
        if (user.getAvatarAuditStatus() == ImageAuditStatusEnum.APPROVED) {
            return uploadService.getProxyUrl(user.getAvatarUrl());
        }
        String configuredUrl = appProperties.getImageAudit().getPendingPlaceholderUrl();
        if (StringUtils.hasText(configuredUrl)) {
            return configuredUrl.trim();
        }
        return uploadService.buildStaticAssetUrl("/static/auditing.webp");
    }
}

