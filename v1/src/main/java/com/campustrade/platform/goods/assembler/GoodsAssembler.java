package com.campustrade.platform.goods.assembler;

import com.campustrade.platform.category.assembler.CategoryAssembler;
import com.campustrade.platform.category.dto.response.CategoryResponseDTO;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.dto.response.GoodsListItemResponseDTO;
import com.campustrade.platform.goods.dto.response.GoodsResponseDTO;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.assembler.UserProfileAssembler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GoodsAssembler {

    private final UserProfileAssembler userProfileAssembler;
    private final CategoryAssembler categoryAssembler;
    private final UploadService uploadService;

    public GoodsAssembler(UserProfileAssembler userProfileAssembler,
                          CategoryAssembler categoryAssembler,
                          UploadService uploadService) {
        this.userProfileAssembler = userProfileAssembler;
        this.categoryAssembler = categoryAssembler;
        this.uploadService = uploadService;
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

    public GoodsListItemResponseDTO toListItemResponse(GoodsDO goods) {
        return new GoodsListItemResponseDTO(
                goods.getId(),
                goods.getTitle(),
                goods.getPrice(),
                goods.getConditionLevel(),
                goods.getCampusLocation(),
                goods.getStatus(),
                goods.getCategory() == null ? null : categoryAssembler.toSummaryResponse(goods.getCategory()),
                goods.getSeller() == null ? null : userProfileAssembler.toSummaryResponse(goods.getSeller()),
                goods.getImages().isEmpty() ? null : toVisibleCoverImageUrl(goods.getImages().get(0)),
                goods.getCreatedAt()
        );
    }

    private String toVisibleCoverImageUrl(GoodsImageDO image) {
        String coverKey = StringUtils.hasText(image.getThumbnailUrl())
                ? image.getThumbnailUrl()
                : image.getImageUrl();
        return uploadService.getProxyUrl(coverKey);
    }

    private String toVisibleImageUrl(GoodsImageDO image) {
        return uploadService.getProxyUrl(image.getImageUrl());
    }
}
