package com.campustrade.platform.upload.service;

import com.campustrade.platform.upload.dataobject.UploadObjectDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UploadCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupScheduler.class);

    private final UploadLifecycleService uploadLifecycleService;
    private final UploadService uploadService;

    public UploadCleanupScheduler(UploadLifecycleService uploadLifecycleService, UploadService uploadService) {
        this.uploadLifecycleService = uploadLifecycleService;
        this.uploadService = uploadService;
    }

    @Scheduled(
            fixedDelayString = "${app.upload.cleanup-interval-ms:3600000}",
            initialDelayString = "${app.upload.cleanup-interval-ms:3600000}"
    )
    public void cleanupExpiredUploads() {
        List<UploadObjectDO> expired = uploadLifecycleService.findExpired(uploadLifecycleService.cleanupBatchSize());
        int cleaned = 0;
        for (UploadObjectDO record : expired) {
            if (!uploadLifecycleService.claimExpired(record.getId())) {
                continue;
            }
            if (uploadService.deleteClaimedUpload(record)) {
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.info("Cleaned {} expired upload groups", cleaned);
        }
    }
}
