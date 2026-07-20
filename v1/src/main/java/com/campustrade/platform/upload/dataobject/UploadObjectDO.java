package com.campustrade.platform.upload.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UploadObjectDO {

    private Long id;
    private Long userId;
    private String usageType;
    private String objectKey;
    private String thumbnailObjectKey;
    private String displayObjectKey;
    private String auditThumbnailObjectKey;
    private Long sourceSizeBytes;
    private Long totalSizeBytes;
    private String status;
    private String boundType;
    private Long boundId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
}
