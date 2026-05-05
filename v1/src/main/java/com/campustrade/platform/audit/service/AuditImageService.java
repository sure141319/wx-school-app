package com.campustrade.platform.audit.service;

import com.campustrade.platform.audit.dataobject.AuditImageRecordDO;
import com.campustrade.platform.audit.dataobject.AvatarAuditRecordDO;
import com.campustrade.platform.audit.dto.response.AuditImageResponseDTO;
import com.campustrade.platform.audit.dto.response.AvatarAuditResponseDTO;
import com.campustrade.platform.audit.mapper.AuditImageMapper;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.goods.mapper.GoodsMapper;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.mapper.UserMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AuditImageService {

    private final AuditImageMapper auditImageMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final UploadService uploadService;
    private final AppProperties appProperties;

    public AuditImageService(AuditImageMapper auditImageMapper,
                             GoodsMapper goodsMapper,
                             UserMapper userMapper,
                             UploadService uploadService,
                             AppProperties appProperties) {
        this.auditImageMapper = auditImageMapper;
        this.goodsMapper = goodsMapper;
        this.userMapper = userMapper;
        this.uploadService = uploadService;
        this.appProperties = appProperties;
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
    @CacheEvict(cacheNames = "goods:list", allEntries = true)
    public AuditImageResponseDTO approve(Long reviewerUserId, Long imageId) {
        ensureReviewer(reviewerUserId);
        updateAuditStatus(imageId, reviewerUserId, ImageAuditStatusEnum.APPROVED, null);
        tryApproveGoods(imageId);
        return getAuditImageOrThrow(imageId);
    }

    @Transactional
    @CacheEvict(cacheNames = "goods:list", allEntries = true)
    public AuditImageResponseDTO reject(Long reviewerUserId, Long imageId, String remark) {
        ensureReviewer(reviewerUserId);
        String normalizedRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        updateAuditStatus(imageId, reviewerUserId, ImageAuditStatusEnum.REJECTED, normalizedRemark);
        rejectGoods(imageId, normalizedRemark);
        return getAuditImageOrThrow(imageId);
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

    private void tryApproveGoods(Long imageId) {
        GoodsImageDO image = goodsMapper.findImageById(imageId);
        if (image == null) {
            return;
        }
        Long goodsId = image.getGoodsId();
        int total = goodsMapper.countImagesByGoodsId(goodsId);
        if (total == 0) {
            goodsMapper.updateAuditStatus(goodsId, GoodsStatusEnum.ON_SALE, null);
            return;
        }
        int approved = goodsMapper.countApprovedImagesByGoodsId(goodsId);
        if (approved == total) {
            goodsMapper.updateAuditStatus(goodsId, GoodsStatusEnum.ON_SALE, null);
        }
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
        return getAvatarAuditOrThrow(userId);
    }

    @Transactional
    public AvatarAuditResponseDTO rejectAvatar(Long reviewerUserId, Long userId, String remark) {
        ensureReviewer(reviewerUserId);
        String normalizedRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        updateAvatarAuditStatus(userId, reviewerUserId, ImageAuditStatusEnum.REJECTED, normalizedRemark);
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
}
