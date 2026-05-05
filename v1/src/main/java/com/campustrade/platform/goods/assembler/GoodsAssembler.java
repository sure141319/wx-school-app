package com.campustrade.platform.goods.assembler;

import com.campustrade.platform.category.assembler.CategoryAssembler;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.category.dto.response.CategoryResponseDTO;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.dto.response.GoodsResponseDTO;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.assembler.UserProfileAssembler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GoodsAssembler {
    private static final String MINI_PROGRAM_PENDING_PLACEHOLDER = "/static/auditing.webp";

    private final UserProfileAssembler userProfileAssembler;
    private final CategoryAssembler categoryAssembler;
    private final UploadService uploadService;
    private final AppProperties appProperties;

    public GoodsAssembler(UserProfileAssembler userProfileAssembler,
                          CategoryAssembler categoryAssembler,
                          UploadService uploadService,
                          AppProperties appProperties) {
        this.userProfileAssembler = userProfileAssembler;
        this.categoryAssembler = categoryAssembler;
        this.uploadService = uploadService;
        this.appProperties = appProperties;
    }

    public GoodsResponseDTO toResponse(GoodsDO goods) {
        CategoryResponseDTO categoryResponse = goods.getCategory() == null ? null : categoryAssembler.toResponse(goods.getCategory());
        return new GoodsResponseDTO(
                goods.getId(),
                goods.getTitle(),
                goods.getDescription(),
                goods.getPrice(),
                goods.getConditionLevel(),
                goods.getCampusLocation(),
                goods.getStatus(),
                categoryResponse,
                userProfileAssembler.toResponse(goods.getSeller()),
                goods.getImages().stream()
                        .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                        .map(this::toVisibleImageUrl)
                        .toList(),
                goods.getImages().stream()
                        .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                        .map(GoodsImageDO::getImageUrl)
                        .toList(),
                goods.getAuditRemark(),
                goods.getCreatedAt(),
                goods.getUpdatedAt()
        );
    }

    private String toVisibleImageUrl(GoodsImageDO image) {
        if (image.getAuditStatus() == ImageAuditStatusEnum.APPROVED) {
            return uploadService.getProxyUrl(image.getImageUrl());
        }
        String configuredUrl = appProperties.getImageAudit().getPendingPlaceholderUrl();
        if (StringUtils.hasText(configuredUrl)) {
            return configuredUrl.trim();
        }
        return MINI_PROGRAM_PENDING_PLACEHOLDER;
    }
}