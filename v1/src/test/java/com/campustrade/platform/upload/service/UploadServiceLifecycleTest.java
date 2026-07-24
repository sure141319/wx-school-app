package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.upload.dataobject.UploadObjectDO;
import com.campustrade.platform.upload.dto.response.UploadResponseDTO;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import okhttp3.Headers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadServiceLifecycleTest {

    @Test
    void cleansAllExpectedObjectsWhenThumbnailWriteOutcomeIsUnknown() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadLifecycleService lifecycle = mock(UploadLifecycleService.class);
        UploadObjectDO reservation = new UploadObjectDO();
        reservation.setId(7L);
        stubReservation(lifecycle, reservation);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        ObjectWriteResponse response = mock(ObjectWriteResponse.class);
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(response)
                .thenThrow(new RuntimeException("thumbnail write failed"));

        UploadService service = new UploadService(minioClient, properties(), lifecycle);

        assertThrows(AppException.class, () -> service.storeImage(validPng(), "goods", 12L));

        ArgumentCaptor<RemoveObjectArgs> removeArgs = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, times(2)).removeObject(removeArgs.capture());
        assertTrue(removeArgs.getAllValues().stream()
                .anyMatch(args -> args.object().equals(reservation.getObjectKey())));
        assertTrue(removeArgs.getAllValues().stream()
                .anyMatch(args -> args.object().equals(reservation.getThumbnailObjectKey())));
        verify(lifecycle).markForCleanup(7L);
        verify(lifecycle).deleteRecord(7L);
        verify(lifecycle, never()).markStaged(anyLong(), anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void storesCompressedMasterAndOneThumbnailForGoods() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadLifecycleService lifecycle = mock(UploadLifecycleService.class);
        UploadObjectDO reservation = new UploadObjectDO();
        reservation.setId(10L);
        MockMultipartFile goods = noisyJpeg();
        stubReservation(lifecycle, reservation);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        UploadService service = new UploadService(minioClient, properties(), lifecycle);

        UploadResponseDTO result = service.storeImage(goods, "goods", 12L);

        assertTrue(result.filename().matches("^images/\\d{4}/\\d{2}/goods/goods_u12_.+\\.webp$"));
        assertTrue(result.thumbnailFilename().endsWith("_thumb.webp"));
        assertNull(result.displayFilename());
        assertNull(result.auditThumbnailFilename());
        ArgumentCaptor<PutObjectArgs> putArgs = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, times(2)).putObject(putArgs.capture());
        assertEquals(result.filename(), putArgs.getAllValues().get(0).object());
        assertEquals(result.thumbnailFilename(), putArgs.getAllValues().get(1).object());
        for (PutObjectArgs args : putArgs.getAllValues()) {
            assertEquals("image/webp", args.contentType());
        }
        verify(lifecycle).markStaged(
                eq(10L),
                eq(12L),
                eq(goods.getSize()),
                argThat(keys -> result.thumbnailFilename().equals(keys.thumbnailObjectKey())
                        && keys.displayObjectKey() == null
                        && keys.auditThumbnailObjectKey() == null),
                longThat(size -> size > 0)
        );
    }

    @Test
    void preservesTrackedLegacyVariantsWhenBackfillingOnlyThumbnail() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadLifecycleService lifecycle = mock(UploadLifecycleService.class);
        String objectKey = "images/2026/07/goods/goods_u12_legacy.jpg";
        String displayKey = "images/2026/07/goods/display/goods_u12_legacy_display.webp";
        String auditKey = "images/2026/07/goods/audit/goods_u12_legacy_audit.webp";
        UploadObjectDO tracked = new UploadObjectDO();
        tracked.setObjectKey(objectKey);
        tracked.setDisplayObjectKey(displayKey);
        tracked.setAuditThumbnailObjectKey(auditKey);
        MockMultipartFile source = validPng();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(new GetObjectResponse(
                new Headers.Builder().build(),
                "campus-trade",
                null,
                objectKey,
                new ByteArrayInputStream(source.getBytes())
        ));
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        when(lifecycle.findByObjectKey(objectKey)).thenReturn(tracked);
        when(lifecycle.updateTrackedVariants(
                eq(objectKey),
                argThat(keys -> keys.thumbnailObjectKey().endsWith("_thumb.webp")
                        && displayKey.equals(keys.displayObjectKey())
                        && auditKey.equals(keys.auditThumbnailObjectKey())),
                longThat(size -> size > 0)
        )).thenReturn(true);

        UploadService service = new UploadService(minioClient, properties(), lifecycle);

        UploadService.ImageVariantKeys generated = service.generateVariantsForObject(objectKey);

        assertTrue(generated.thumbnailObjectKey().endsWith("_thumb.webp"));
        assertNull(generated.displayObjectKey());
        assertNull(generated.auditThumbnailObjectKey());
        verify(lifecycle).updateTrackedVariants(
                eq(objectKey),
                argThat(keys -> keys.thumbnailObjectKey().endsWith("_thumb.webp")
                        && displayKey.equals(keys.displayObjectKey())
                        && auditKey.equals(keys.auditThumbnailObjectKey())),
                longThat(size -> size > 0)
        );
    }

    @Test
    void storesOnlyOneOptimizedWebpForAvatar() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadLifecycleService lifecycle = mock(UploadLifecycleService.class);
        UploadObjectDO reservation = new UploadObjectDO();
        reservation.setId(8L);
        MockMultipartFile avatar = noisyJpeg();
        stubReservation(lifecycle, reservation);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        UploadService service = new UploadService(minioClient, properties(), lifecycle);

        UploadResponseDTO result = service.storeImage(avatar, "avatar", 12L);

        assertTrue(result.filename().matches("^images/\\d{4}/\\d{2}/avatar/avatar_u12_.+\\.webp$"));
        assertNull(result.thumbnailFilename());
        assertNull(result.displayFilename());
        ArgumentCaptor<PutObjectArgs> putArgs = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putArgs.capture());
        assertEquals(result.filename(), putArgs.getValue().object());
        assertEquals("image/webp", putArgs.getValue().contentType());
        verify(lifecycle).markStaged(
                eq(8L),
                eq(12L),
                eq(avatar.getSize()),
                argThat(keys -> keys.thumbnailObjectKey() == null
                        && keys.displayObjectKey() == null
                        && keys.auditThumbnailObjectKey() == null),
                longThat(size -> size > 0 && size < avatar.getSize())
        );
    }

    @Test
    void rejectsUndecodableAvatarInsteadOfKeepingLargeOriginal() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadLifecycleService lifecycle = mock(UploadLifecycleService.class);
        UploadObjectDO reservation = new UploadObjectDO();
        reservation.setId(9L);
        stubReservation(lifecycle, reservation);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        byte[] heicHeader = new byte[32];
        System.arraycopy("ftyp".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, heicHeader, 4, 4);
        System.arraycopy("heic".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, heicHeader, 8, 4);
        MockMultipartFile avatar = new MockMultipartFile("file", "avatar.heic", "image/heic", heicHeader);
        UploadService service = new UploadService(minioClient, properties(), lifecycle);

        AppException error = assertThrows(AppException.class, () -> service.storeImage(avatar, "avatar", 12L));

        assertEquals(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatus());
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        verify(lifecycle).deleteRecord(9L);
    }

    @Test
    void marksBoundUploadDeletingBeforeTransactionCommitCallback() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadLifecycleService lifecycle = mock(UploadLifecycleService.class);
        UploadObjectDO record = new UploadObjectDO();
        record.setId(21L);
        record.setObjectKey("images/2026/07/goods/goods_u12_old.webp");
        record.setThumbnailObjectKey("images/2026/07/goods/thumbs/goods_u12_old_thumb.webp");
        when(lifecycle.beginBoundDeletion(record.getObjectKey())).thenReturn(record);
        UploadService service = new UploadService(minioClient, properties(), lifecycle);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteUploadGroupAfterCommit(
                    record.getObjectKey(),
                    record.getThumbnailObjectKey(),
                    null,
                    null
            );

            verify(lifecycle).beginBoundDeletion(record.getObjectKey());
            verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(minioClient, times(2)).removeObject(any(RemoveObjectArgs.class));
            verify(lifecycle).deleteRecord(21L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void stubReservation(UploadLifecycleService lifecycle, UploadObjectDO reservation) {
        when(lifecycle.reserve(anyLong(), anyString(), anyString(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    reservation.setObjectKey(invocation.getArgument(2));
                    UploadService.ImageVariantKeys variants = invocation.getArgument(4);
                    reservation.setThumbnailObjectKey(variants.thumbnailObjectKey());
                    reservation.setDisplayObjectKey(variants.displayObjectKey());
                    reservation.setAuditThumbnailObjectKey(variants.auditThumbnailObjectKey());
                    return reservation;
                });
    }

    private MockMultipartFile validPng() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "goods.png", "image/png", output.toByteArray());
    }

    private MockMultipartFile noisyJpeg() throws Exception {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(12L);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return new MockMultipartFile("file", "avatar.jpg", "image/jpeg", output.toByteArray());
    }

    private AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.setApiBaseUrl("http://localhost:8080");
        properties.getMinio().setEndpoint("http://127.0.0.1:9000");
        properties.getMinio().setAccessKey("access");
        properties.getMinio().setSecretKey("secret");
        properties.getMinio().setBucket("campus-trade");
        return properties;
    }
}
