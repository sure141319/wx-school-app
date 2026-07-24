package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.common.time.BeijingTime;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.upload.dataobject.UploadObjectDO;
import com.campustrade.platform.upload.mapper.UploadObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UploadLifecycleService {

    static final String STATUS_UPLOADING = "UPLOADING";
    static final String STATUS_STAGED = "STAGED";
    static final String STATUS_BOUND = "BOUND";
    static final String STATUS_DELETING = "DELETING";
    static final String BOUND_TYPE_GOODS = "GOODS";
    static final String BOUND_TYPE_AVATAR = "AVATAR";
    private static final int DELETE_RETRY_DELAY_MINUTES = 5;

    private final UploadObjectMapper uploadObjectMapper;
    private final AppProperties.Upload uploadProperties;

    public UploadLifecycleService(UploadObjectMapper uploadObjectMapper, AppProperties appProperties) {
        this.uploadObjectMapper = uploadObjectMapper;
        this.uploadProperties = appProperties.getUpload();
    }

    @Transactional
    public UploadObjectDO reserve(Long userId, String usage, String objectKey, long totalSizeBytes) {
        return reserve(
                userId,
                usage,
                objectKey,
                totalSizeBytes,
                new UploadService.ImageVariantKeys(null, null, null)
        );
    }

    @Transactional
    public UploadObjectDO reserve(Long userId,
                                  String usage,
                                  String objectKey,
                                  long totalSizeBytes,
                                  UploadService.ImageVariantKeys expectedVariants) {
        if (userId == null || userId <= 0) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "请先登录后再上传图片");
        }
        if (uploadObjectMapper.lockUserForUpload(userId) == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "用户不存在");
        }

        long allCount = uploadObjectMapper.countAllByUser(userId);
        long allBytes = uploadObjectMapper.sumAllBytesByUser(userId);
        long stagedCount = uploadObjectMapper.countStagedByUser(userId);
        long stagedBytes = uploadObjectMapper.sumStagedBytesByUser(userId);

        if (allCount + 1 > uploadProperties.getMaxFilesPerUser()
                || allBytes + totalSizeBytes > uploadProperties.getMaxBytesPerUser()) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "个人图片存储配额已满，请先删除不再使用的商品或图片");
        }
        if (stagedCount + 1 > uploadProperties.getMaxStagedFilesPerUser()
                || stagedBytes + totalSizeBytes > uploadProperties.getMaxStagedBytesPerUser()) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "待发布图片过多，请先完成发布或删除暂存图片");
        }

        LocalDateTime now = now();
        UploadObjectDO record = new UploadObjectDO();
        record.setUserId(userId);
        record.setUsageType(usage);
        record.setObjectKey(objectKey);
        if (expectedVariants != null) {
            record.setThumbnailObjectKey(expectedVariants.thumbnailObjectKey());
            record.setDisplayObjectKey(expectedVariants.displayObjectKey());
            record.setAuditThumbnailObjectKey(expectedVariants.auditThumbnailObjectKey());
        }
        record.setSourceSizeBytes(totalSizeBytes);
        record.setTotalSizeBytes(totalSizeBytes);
        record.setStatus(STATUS_UPLOADING);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setExpiresAt(now.plusHours(uploadProperties.getStagedTtlHours()));
        uploadObjectMapper.insert(record);
        return record;
    }

    @Transactional
    public void markStaged(Long id,
                           Long userId,
                           long reservedSizeBytes,
                           UploadService.ImageVariantKeys variants,
                           long totalSizeBytes) {
        uploadObjectMapper.lockUserForUpload(userId);
        long adjustedAllBytes = uploadObjectMapper.sumAllBytesByUser(userId) - reservedSizeBytes + totalSizeBytes;
        long adjustedStagedBytes = uploadObjectMapper.sumStagedBytesByUser(userId) - reservedSizeBytes + totalSizeBytes;
        if (adjustedAllBytes > uploadProperties.getMaxBytesPerUser()) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "个人图片存储配额已满，请先删除不再使用的商品或图片");
        }
        if (adjustedStagedBytes > uploadProperties.getMaxStagedBytesPerUser()) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "待发布图片占用空间过多，请先完成发布或删除暂存图片");
        }
        int updated = uploadObjectMapper.markStaged(
                id,
                variants.thumbnailObjectKey(),
                variants.displayObjectKey(),
                variants.auditThumbnailObjectKey(),
                totalSizeBytes,
                now()
        );
        if (updated != 1) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "上传暂存状态更新失败");
        }
    }

    @Transactional
    public UploadObjectDO bindToGoods(String objectKey, Long userId, Long goodsId) {
        return bind(objectKey, userId, "goods", BOUND_TYPE_GOODS, goodsId);
    }

    @Transactional
    public boolean updateTrackedVariants(String objectKey,
                                         UploadService.ImageVariantKeys variants,
                                         long variantSizeBytes) {
        return uploadObjectMapper.updateVariantsByObjectKey(
                objectKey,
                variants.thumbnailObjectKey(),
                variants.displayObjectKey(),
                variants.auditThumbnailObjectKey(),
                variantSizeBytes,
                now()
        ) == 1;
    }

    @Transactional
    public UploadObjectDO bindToAvatar(String objectKey, Long userId) {
        return bind(objectKey, userId, "avatar", BOUND_TYPE_AVATAR, userId);
    }

    @Transactional
    public UploadObjectDO beginStagedDeletion(String objectKey, Long userId) {
        UploadObjectDO record = uploadObjectMapper.findByObjectKeyForUpdate(objectKey);
        if (record == null) {
            return null;
        }
        if (!record.getUserId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权删除该图片");
        }
        if (!STATUS_STAGED.equals(record.getStatus()) && !STATUS_DELETING.equals(record.getStatus())) {
            throw new AppException(HttpStatus.CONFLICT, "图片已绑定，不能按暂存图片删除");
        }
        markDeleting(record.getId());
        record.setStatus(STATUS_DELETING);
        return record;
    }

    @Transactional
    public UploadObjectDO beginBoundDeletion(String objectKey) {
        UploadObjectDO record = uploadObjectMapper.findByObjectKeyForUpdate(objectKey);
        if (record == null) {
            return null;
        }
        markDeleting(record.getId());
        record.setStatus(STATUS_DELETING);
        return record;
    }

    @Transactional(readOnly = true)
    public UploadObjectDO findByObjectKey(String objectKey) {
        return uploadObjectMapper.findByObjectKey(objectKey);
    }

    @Transactional(readOnly = true)
    public List<UploadObjectDO> findExpired(int limit) {
        return uploadObjectMapper.findExpired(now(), limit);
    }

    @Transactional
    public boolean claimExpired(Long id) {
        LocalDateTime now = now();
        return uploadObjectMapper.markExpiredDeleting(id, now, now.plusMinutes(DELETE_RETRY_DELAY_MINUTES)) == 1;
    }

    @Transactional
    public void markForCleanup(Long id) {
        markDeleting(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteRecord(Long id) {
        uploadObjectMapper.deleteById(id);
    }

    public int cleanupBatchSize() {
        return uploadProperties.getCleanupBatchSize();
    }

    private UploadObjectDO bind(String objectKey,
                                Long userId,
                                String usage,
                                String boundType,
                                Long boundId) {
        UploadObjectDO record = uploadObjectMapper.findByObjectKeyForUpdate(objectKey);
        if (record == null
                || !record.getUserId().equals(userId)
                || !usage.equals(record.getUsageType())) {
            throw new AppException(HttpStatus.FORBIDDEN, "图片不属于当前用户的有效暂存上传");
        }
        if (!STATUS_STAGED.equals(record.getStatus())) {
            throw new AppException(HttpStatus.CONFLICT, "图片已绑定、已过期或正在删除");
        }
        if (uploadObjectMapper.bind(record.getId(), boundType, boundId, now()) != 1) {
            throw new AppException(HttpStatus.CONFLICT, "图片暂存状态已变化，请重新上传");
        }
        record.setStatus(STATUS_BOUND);
        record.setBoundType(boundType);
        record.setBoundId(boundId);
        return record;
    }

    private void markDeleting(Long id) {
        LocalDateTime now = now();
        uploadObjectMapper.markDeleting(id, now.plusMinutes(DELETE_RETRY_DELAY_MINUTES), now);
    }

    private LocalDateTime now() {
        return BeijingTime.now();
    }
}
