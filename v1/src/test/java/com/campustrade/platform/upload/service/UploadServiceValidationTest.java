package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadServiceValidationTest {

    @Test
    void rejectsSpoofedImageContentType() {
        UploadService service = newService();
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/svg+xml", jpegHeader);

        assertThrows(AppException.class, () -> service.storeImage(file));
    }

    @Test
    void rejectsImageContentTypeWithInvalidFileSignature() {
        UploadService service = newService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "<svg></svg>".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(AppException.class, () -> service.storeImage(file));
    }

    @Test
    void validatesUploadedImageReferenceBelongsToCurrentUserAndExists() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadService service = newService(minioClient);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));
        String objectKey = "images/2026/07/goods/goods_u12_20260712120000_a1b2c3.jpg";

        String result = service.validateUploadedImageReference(objectKey, "goods", 12L);

        assertEquals(objectKey, result);
        verify(minioClient).statObject(any(StatObjectArgs.class));
    }

    @Test
    void rejectsUploadedImageReferenceOwnedByAnotherUser() {
        MinioClient minioClient = mock(MinioClient.class);
        UploadService service = newService(minioClient);
        String objectKey = "images/2026/07/goods/goods_u99_20260712120000_a1b2c3.jpg";

        assertThrows(AppException.class, () -> service.validateUploadedImageReference(objectKey, "goods", 12L));
    }

    @Test
    void defersObjectDeletionUntilTransactionCommit() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadService service = newService(minioClient);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteObjectAfterCommit("images/2026/07/goods/goods_u12_20260712120000_a1b2c3.jpg");

            verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private UploadService newService() {
        return newService(mock(MinioClient.class));
    }

    private UploadService newService(MinioClient minioClient) {
        AppProperties properties = new AppProperties();
        properties.setApiBaseUrl("https://www.ahut-campus.site");
        properties.getMinio().setEndpoint("http://127.0.0.1:9000");
        properties.getMinio().setAccessKey("access");
        properties.getMinio().setSecretKey("secret");
        properties.getMinio().setBucket("campus-trade");
        return new UploadService(minioClient, properties);
    }
}
