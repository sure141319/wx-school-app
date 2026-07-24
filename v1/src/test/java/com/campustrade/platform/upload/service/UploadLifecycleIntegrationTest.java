package com.campustrade.platform.upload.service;

import com.campustrade.platform.upload.dataobject.UploadObjectDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class UploadLifecycleIntegrationTest {

    @Autowired
    private UploadLifecycleService uploadLifecycleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void persistsStagesAndBindsUploadThroughMapper() {
        Long userId = insertUser();
        String objectKey = "images/2026/07/goods/goods_u" + userId + "_integration.jpg";
        String thumbnailObjectKey = "images/2026/07/goods/thumbs/goods_u" + userId + "_integration_thumb.webp";
        UploadObjectDO reserved = uploadLifecycleService.reserve(
                userId,
                "goods",
                objectKey,
                100L,
                new UploadService.ImageVariantKeys(thumbnailObjectKey, null, null)
        );
        UploadObjectDO uploading = uploadLifecycleService.findByObjectKey(objectKey);

        uploadLifecycleService.markStaged(
                reserved.getId(),
                userId,
                100L,
                new UploadService.ImageVariantKeys("thumb.webp", "display.webp", "audit.webp"),
                160L
        );
        UploadObjectDO bound = uploadLifecycleService.bindToGoods(objectKey, userId, 88L);
        UploadObjectDO persisted = uploadLifecycleService.findByObjectKey(objectKey);

        assertEquals(UploadLifecycleService.STATUS_BOUND, bound.getStatus());
        assertEquals(thumbnailObjectKey, uploading.getThumbnailObjectKey());
        assertEquals(160L, persisted.getTotalSizeBytes());
        assertEquals("thumb.webp", persisted.getThumbnailObjectKey());
        assertEquals(88L, persisted.getBoundId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void boundDeletionStateRollsBackWithBusinessTransaction() {
        Long userId = insertUser();
        String objectKey = "images/2026/07/goods/goods_u" + userId + "_after_commit.jpg";
        UploadObjectDO reserved = uploadLifecycleService.reserve(userId, "goods", objectKey, 100L);
        uploadLifecycleService.markStaged(
                reserved.getId(),
                userId,
                100L,
                new UploadService.ImageVariantKeys(null, null, null),
                100L
        );
        uploadLifecycleService.bindToGoods(objectKey, userId, 99L);

        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                UploadObjectDO deleting = uploadLifecycleService.beginBoundDeletion(objectKey);
                assertEquals(UploadLifecycleService.STATUS_DELETING, deleting.getStatus());
                status.setRollbackOnly();
            });

            UploadObjectDO persisted = uploadLifecycleService.findByObjectKey(objectKey);
            assertEquals(UploadLifecycleService.STATUS_BOUND, persisted.getStatus());
        } finally {
            jdbcTemplate.update("DELETE FROM upload_object_do WHERE object_key = ?", objectKey);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void boundDeletionStateCommitsWithBusinessTransaction() {
        Long userId = insertUser();
        String objectKey = "images/2026/07/goods/goods_u" + userId + "_deleting_commit.jpg";
        UploadObjectDO reserved = uploadLifecycleService.reserve(userId, "goods", objectKey, 100L);
        uploadLifecycleService.markStaged(
                reserved.getId(),
                userId,
                100L,
                new UploadService.ImageVariantKeys(null, null, null),
                100L
        );
        uploadLifecycleService.bindToGoods(objectKey, userId, 100L);

        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    uploadLifecycleService.beginBoundDeletion(objectKey)
            );

            UploadObjectDO persisted = uploadLifecycleService.findByObjectKey(objectKey);
            assertEquals(UploadLifecycleService.STATUS_DELETING, persisted.getStatus());
        } finally {
            jdbcTemplate.update("DELETE FROM upload_object_do WHERE object_key = ?", objectKey);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    private Long insertUser() {
        String email = "upload-lifecycle-" + System.nanoTime() + "@qq.com";
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, nickname, failed_login_count, created_at, updated_at)
                VALUES (?, ?, ?, 0, NOW(), NOW())
                """, email, "hash", "上传测试用户");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}
