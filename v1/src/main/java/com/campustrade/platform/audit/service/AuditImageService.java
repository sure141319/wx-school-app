package com.campustrade.platform.audit.service;

import com.campustrade.platform.audit.dataobject.AuditImageRecordDO;
import com.campustrade.platform.audit.dataobject.AvatarAuditRecordDO;
import com.campustrade.platform.audit.dto.response.AuditImageResponseDTO;
import com.campustrade.platform.audit.dto.response.AvatarAuditResponseDTO;
import com.campustrade.platform.audit.dto.response.ThumbnailBackfillResponseDTO;
import com.campustrade.platform.audit.mapper.AuditImageMapper;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.config.cache.GoodsListCacheInvalidator;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.goods.mapper.GoodsMapper;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AuditImageService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditImageService.class);
    private static final String BULK_APPROVE_CONFIRMATION = "APPROVE_ALL_PENDING";
    private static final String BULK_REJECT_CONFIRMATION = "REJECT_ALL_APPROVED";

    private final AuditImageMapper auditImageMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final UploadService uploadService;
    private final AppProperties appProperties;
    private final GoodsListCacheInvalidator goodsListCacheInvalidator;

    public AuditImageService(AuditImageMapper auditImageMapper,
                             GoodsMapper goodsMapper,
                             UserMapper userMapper,
                             UploadService uploadService,
                             AppProperties appProperties,
                             GoodsListCacheInvalidator goodsListCacheInvalidator) {
        this.auditImageMapper = auditImageMapper;
        this.goodsMapper = goodsMapper;
        this.userMapper = userMapper;
        this.uploadService = uploadService;
        this.appProperties = appProperties;
        this.goodsListCacheInvalidator = goodsListCacheInvalidator;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditImageResponseDTO> list(Long reviewerUserId,
                                                    ImageAuditStatusEnum status,
                                                    int page,
                                                    int size) {
        ensureReviewer(reviewerUserId);
        ImageAuditStatusEnum effectiveStatus = status == null ? ImageAuditStatusEnum.PENDING : status;
        int offset = page * size;

        List<AuditImageResponseDTO> items = auditImageMapper.search(effectiveStatus, size, offset).stream()
                .map(this::toResponse)
                .toList();
        long total = auditImageMapper.countSearch(effectiveStatus);
        return PageResponse.of(items, total, page, size);
    }

    @Transactional
    public AuditImageResponseDTO approve(Long reviewerUserId, Long imageId) {
        ensureReviewer(reviewerUserId);
        updateAuditStatus(imageId, reviewerUserId, ImageAuditStatusEnum.APPROVED, null);
        if (tryApproveGoods(imageId)) {
            goodsListCacheInvalidator.evictAfterCommit();
        }
        return getAuditImageOrThrow(imageId);
    }

    @Transactional
    public int approveAllPending(Long reviewerUserId, String confirmation) {
        ensureReviewer(reviewerUserId);
        ensureBulkApproveConfirmed(confirmation);

        List<GoodsImageDO> pendingImages = goodsMapper.findImagesByAuditStatus(ImageAuditStatusEnum.PENDING);
        int count = 0;
        boolean goodsStatusChanged = false;
        for (GoodsImageDO image : pendingImages) {
            goodsMapper.updateImageAudit(image.getId(), ImageAuditStatusEnum.APPROVED, null, reviewerUserId);
            goodsStatusChanged = tryApproveGoods(image.getId()) || goodsStatusChanged;
            count++;
        }
        if (goodsStatusChanged) {
            goodsListCacheInvalidator.evictAfterCommit();
        }
        return count;
    }

    @Transactional
    public AuditImageResponseDTO reject(Long reviewerUserId, Long imageId, String remark) {
        ensureReviewer(reviewerUserId);
        String normalizedRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        updateAuditStatus(imageId, reviewerUserId, ImageAuditStatusEnum.REJECTED, normalizedRemark);
        rejectGoods(imageId, normalizedRemark);
        goodsListCacheInvalidator.evictAfterCommit();
        return getAuditImageOrThrow(imageId);
    }

    @Transactional
    public int rejectAllApproved(Long reviewerUserId, String remark, String confirmation) {
        ensureReviewer(reviewerUserId);
        ensureBulkRejectConfirmed(confirmation);
        String normalizedRemark = StringUtils.hasText(remark) ? remark.trim() : null;

        List<GoodsImageDO> approvedImages = goodsMapper.findImagesByAuditStatus(ImageAuditStatusEnum.APPROVED);
        int count = 0;
        for (GoodsImageDO image : approvedImages) {
            goodsMapper.updateImageAudit(image.getId(), ImageAuditStatusEnum.REJECTED, normalizedRemark, reviewerUserId);
            rejectGoods(image.getId(), normalizedRemark);
            count++;
        }
        if (count > 0) {
            goodsListCacheInvalidator.evictAfterCommit();
        }
        return count;
    }

    @Transactional
    public ThumbnailBackfillResponseDTO backfillMissingThumbnails(Long reviewerUserId, int limit) {
        ensureReviewer(reviewerUserId);

        List<GoodsImageDO> images = goodsMapper.findImagesMissingThumbnails(limit);
        int generated = 0;
        int skipped = 0;
        int failed = 0;

        for (GoodsImageDO image : images) {
            try {
                String thumbnailKey = uploadService.generateThumbnailForObject(image.getImageUrl());
                if (StringUtils.hasText(thumbnailKey)) {
                    goodsMapper.updateImageThumbnail(image.getId(), thumbnailKey);
                    generated++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Failed to backfill thumbnail for image id {}: {}", image.getId(), ex.getMessage());
            }
        }
        if (generated > 0) {
            goodsListCacheInvalidator.evictAfterCommit();
        }

        long remaining = goodsMapper.countImagesMissingThumbnails();
        return new ThumbnailBackfillResponseDTO(images.size(), generated, skipped, failed, remaining);
    }

    private void updateAuditStatus(Long imageId,
                                   Long reviewerUserId,
                                   ImageAuditStatusEnum status,
                                   String remark) {
        GoodsImageDO image = goodsMapper.findImageById(imageId);
        if (image == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        goodsMapper.updateImageAudit(imageId, status, remark, reviewerUserId);
    }

    private boolean tryApproveGoods(Long imageId) {
        GoodsImageDO image = goodsMapper.findImageById(imageId);
        if (image == null) {
            return false;
        }
        Long goodsId = image.getGoodsId();
        int total = goodsMapper.countImagesByGoodsId(goodsId);
        if (total == 0) {
            goodsMapper.updateAuditStatus(goodsId, GoodsStatusEnum.ON_SALE, null);
            return true;
        }
        int approved = goodsMapper.countApprovedImagesByGoodsId(goodsId);
        if (approved == total) {
            goodsMapper.updateAuditStatus(goodsId, GoodsStatusEnum.ON_SALE, null);
            return true;
        }
        return false;
    }

    private void rejectGoods(Long imageId, String remark) {
        GoodsImageDO image = goodsMapper.findImageById(imageId);
        if (image == null) {
            return;
        }
        goodsMapper.updateAuditStatus(image.getGoodsId(), GoodsStatusEnum.REJECTED, remark);
    }

    private AuditImageResponseDTO getAuditImageOrThrow(Long imageId) {
        AuditImageRecordDO record = auditImageMapper.findByImageId(imageId);
        if (record == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        return toResponse(record);
    }

    private AuditImageResponseDTO toResponse(AuditImageRecordDO record) {
        return new AuditImageResponseDTO(
                record.getImageId(),
                record.getGoodsId(),
                record.getGoodsTitle(),
                record.getGoodsDescription(),
                record.getSellerId(),
                record.getSellerNickname(),
                record.getSellerWechatId(),
                record.getSellerQq(),
                uploadService.getProxyUrl(record.getImageUrl()),
                record.getSortOrder(),
                record.getAuditStatus(),
                record.getAuditRemark(),
                record.getAuditedBy(),
                record.getAuditedAt(),
                record.getCreatedAt()
        );
    }

    // ==================== 头像审核 ====================

    @Transactional(readOnly = true)
    public PageResponse<AvatarAuditResponseDTO> listAvatars(Long reviewerUserId,
                                                            ImageAuditStatusEnum status,
                                                            int page,
                                                            int size) {
        ensureReviewer(reviewerUserId);
        ImageAuditStatusEnum effectiveStatus = status == null ? ImageAuditStatusEnum.PENDING : status;
        int offset = page * size;

        List<AvatarAuditResponseDTO> items = auditImageMapper.searchAvatars(effectiveStatus, size, offset).stream()
                .map(this::toAvatarResponse)
                .toList();
        long total = auditImageMapper.countSearchAvatars(effectiveStatus);
        return PageResponse.of(items, total, page, size);
    }

    @Transactional
    public AvatarAuditResponseDTO approveAvatar(Long reviewerUserId, Long userId) {
        ensureReviewer(reviewerUserId);
        updateAvatarAuditStatus(userId, reviewerUserId, ImageAuditStatusEnum.APPROVED, null);
        goodsListCacheInvalidator.evictAfterCommit();
        return getAvatarAuditOrThrow(userId);
    }

    @Transactional
    public AvatarAuditResponseDTO rejectAvatar(Long reviewerUserId, Long userId, String remark) {
        ensureReviewer(reviewerUserId);
        String normalizedRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        updateAvatarAuditStatus(userId, reviewerUserId, ImageAuditStatusEnum.REJECTED, normalizedRemark);
        goodsListCacheInvalidator.evictAfterCommit();
        return getAvatarAuditOrThrow(userId);
    }

    private void updateAvatarAuditStatus(Long userId,
                                         Long reviewerUserId,
                                         ImageAuditStatusEnum status,
                                         String remark) {
        UserDO user = userMapper.findById(userId);
        if (user == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        userMapper.updateAvatarAuditStatus(userId, status, remark, reviewerUserId);
    }

    private AvatarAuditResponseDTO getAvatarAuditOrThrow(Long userId) {
        AvatarAuditRecordDO record = auditImageMapper.findAvatarByUserId(userId);
        if (record == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return toAvatarResponse(record);
    }

    private AvatarAuditResponseDTO toAvatarResponse(AvatarAuditRecordDO record) {
        return new AvatarAuditResponseDTO(
                record.getUserId(),
                record.getNickname(),
                record.getWechatId(),
                record.getQq(),
                uploadService.getProxyUrl(record.getAvatarUrl()),
                record.getAvatarAuditStatus(),
                record.getAvatarAuditRemark(),
                record.getAvatarAuditedBy(),
                record.getAvatarAuditedAt(),
                record.getUpdatedAt()
        );
    }

    private void ensureReviewer(Long reviewerUserId) {
        if (reviewerUserId == null || !appProperties.getImageAudit().getReviewerUserIds().contains(reviewerUserId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权审核图片");
        }
    }

    private void ensureBulkApproveConfirmed(String confirmation) {
        if (!BULK_APPROVE_CONFIRMATION.equals(confirmation)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "批量通过需确认操作");
        }
    }

    private void ensureBulkRejectConfirmed(String confirmation) {
        if (!BULK_REJECT_CONFIRMATION.equals(confirmation)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "批量驳回需确认操作");
        }
    }
}
