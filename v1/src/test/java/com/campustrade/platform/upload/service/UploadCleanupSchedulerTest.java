package com.campustrade.platform.upload.service;

import com.campustrade.platform.upload.dataobject.UploadObjectDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadCleanupSchedulerTest {

    private final UploadLifecycleService lifecycleService = mock(UploadLifecycleService.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final UploadCleanupScheduler scheduler = new UploadCleanupScheduler(lifecycleService, uploadService);

    @Test
    void claimsAndDeletesExpiredUploadGroups() {
        UploadObjectDO claimed = record(1L);
        UploadObjectDO skipped = record(2L);
        when(lifecycleService.cleanupBatchSize()).thenReturn(100);
        when(lifecycleService.findExpired(100)).thenReturn(List.of(claimed, skipped));
        when(lifecycleService.claimExpired(1L)).thenReturn(true);
        when(lifecycleService.claimExpired(2L)).thenReturn(false);
        when(uploadService.deleteClaimedUpload(claimed)).thenReturn(true);

        scheduler.cleanupExpiredUploads();

        verify(uploadService).deleteClaimedUpload(claimed);
        verify(uploadService, never()).deleteClaimedUpload(skipped);
    }

    private UploadObjectDO record(Long id) {
        UploadObjectDO record = new UploadObjectDO();
        record.setId(id);
        record.setObjectKey("images/expired-" + id + ".jpg");
        return record;
    }
}
