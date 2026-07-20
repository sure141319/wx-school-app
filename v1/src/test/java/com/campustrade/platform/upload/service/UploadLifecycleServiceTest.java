package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.upload.dataobject.UploadObjectDO;
import com.campustrade.platform.upload.mapper.UploadObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadLifecycleServiceTest {

    private final UploadObjectMapper mapper = mock(UploadObjectMapper.class);
    private final AppProperties properties = new AppProperties();
    private final UploadLifecycleService service = new UploadLifecycleService(mapper, properties);

    @Test
    void reservesQuotaAndCreatesExpiringUploadingRecord() {
        when(mapper.lockUserForUpload(12L)).thenReturn(12L);
        when(mapper.insert(any(UploadObjectDO.class))).thenAnswer(invocation -> {
            UploadObjectDO record = invocation.getArgument(0);
            record.setId(7L);
            return 1;
        });
        LocalDateTime before = LocalDateTime.now().plusHours(23);

        UploadObjectDO record = service.reserve(12L, "goods", "images/demo.jpg", 1024L);

        assertEquals(7L, record.getId());
        assertEquals(UploadLifecycleService.STATUS_UPLOADING, record.getStatus());
        assertEquals(1024L, record.getTotalSizeBytes());
        assertNotNull(record.getExpiresAt());
        assertTrue(record.getExpiresAt().isAfter(before));
    }

    @Test
    void rejectsWhenStagedFileQuotaIsAlreadyFull() {
        when(mapper.lockUserForUpload(12L)).thenReturn(12L);
        when(mapper.countStagedByUser(12L))
                .thenReturn((long) properties.getUpload().getMaxStagedFilesPerUser());

        AppException ex = assertThrows(
                AppException.class,
                () -> service.reserve(12L, "goods", "images/demo.jpg", 1024L)
        );

        assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        verify(mapper, never()).insert(any(UploadObjectDO.class));
    }

    @Test
    void bindsOnlyOwnedStagedUpload() {
        UploadObjectDO record = stagedRecord(12L);
        when(mapper.findByObjectKeyForUpdate(record.getObjectKey())).thenReturn(record);
        when(mapper.bind(anyLong(), any(), anyLong(), any())).thenReturn(1);

        UploadObjectDO bound = service.bindToGoods(record.getObjectKey(), 12L, 33L);

        assertEquals(UploadLifecycleService.STATUS_BOUND, bound.getStatus());
        assertEquals(UploadLifecycleService.BOUND_TYPE_GOODS, bound.getBoundType());
        assertEquals(33L, bound.getBoundId());
    }

    @Test
    void rejectsDeletingAnotherUsersStagedUpload() {
        UploadObjectDO record = stagedRecord(99L);
        when(mapper.findByObjectKeyForUpdate(record.getObjectKey())).thenReturn(record);

        assertThrows(AppException.class, () -> service.beginStagedDeletion(record.getObjectKey(), 12L));

        verify(mapper, never()).markDeleting(anyLong(), any(), any());
    }

    private UploadObjectDO stagedRecord(Long userId) {
        UploadObjectDO record = new UploadObjectDO();
        record.setId(5L);
        record.setUserId(userId);
        record.setUsageType("goods");
        record.setObjectKey("images/2026/07/goods/goods_u" + userId + "_demo.jpg");
        record.setStatus(UploadLifecycleService.STATUS_STAGED);
        return record;
    }
}
