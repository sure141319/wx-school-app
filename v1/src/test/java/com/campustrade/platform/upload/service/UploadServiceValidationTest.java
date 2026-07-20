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
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void acceptsValidImageWithinDimensionLimits() throws IOException {
        UploadService service = newService();
        BufferedImage image = new BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        MockMultipartFile file = new MockMultipartFile("file", "goods.png", "image/png", output.toByteArray());

        assertDoesNotThrow(() -> service.validateImage(file));
    }

    @Test
    void rejectsFileLargerThanServiceLimitBeforeReadingIt() {
        UploadService service = newService();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L * 1024L * 1024L + 1L);

        AppException ex = assertThrows(AppException.class, () -> service.validateImage(file));

        assertEquals(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatus());
    }

    @Test
    void rejectsExcessiveImageDimensionsAndPixelCounts() {
        UploadService service = newService();

        AppException oversizedWidth = assertThrows(
                AppException.class,
                () -> service.validateImageDimensions(10_001, 100)
        );
        AppException excessivePixels = assertThrows(
                AppException.class,
                () -> service.validateImageDimensions(8_000, 7_000)
        );

        assertEquals(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, oversizedWidth.getStatus());
        assertEquals(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, excessivePixels.getStatus());
    }

    @Test
    void calculatesSubsamplingBeforeThumbnailDecode() {
        UploadService service = newService();

        assertEquals(1, service.calculateThumbnailSubsampling(960, 640));
        assertEquals(5, service.calculateThumbnailSubsampling(4_000, 3_000));
    }

    @Test
    void decodesThumbnailSourceWithSubsampling() throws IOException {
        UploadService service = newService();
        BufferedImage image = new BufferedImage(1_000, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);

        BufferedImage decoded = service.readImageForThumbnail(new ByteArrayInputStream(output.toByteArray()));

        assertEquals(500, decoded.getWidth());
        assertEquals(50, decoded.getHeight());
    }

    @Test
    void encodesLossyWebpThatImageIoCanDecode() throws IOException {
        UploadService service = newService();
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);

        byte[] encoded = service.encodeWebp(image, 0.45f);

        assertTrue(encoded.length > 12);
        assertEquals("RIFF", new String(encoded, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WEBP", new String(encoded, 8, 4, StandardCharsets.US_ASCII));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded));
        assertEquals(320, decoded.getWidth());
        assertEquals(180, decoded.getHeight());
    }

    @Test
    void optimizesAvatarToSmallWebpWithoutUpscaling() throws IOException {
        UploadService service = newService();
        BufferedImage image = new BufferedImage(1_600, 800, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        ImageIO.write(image, "png", source);

        byte[] encoded = service.optimizeAvatar(new ByteArrayInputStream(source.toByteArray()));

        assertTrue(encoded.length > 12);
        assertEquals("WEBP", new String(encoded, 8, 4, StandardCharsets.US_ASCII));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded));
        assertEquals(320, decoded.getWidth());
        assertEquals(160, decoded.getHeight());
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
    void validatesOwnedWebpVariantReferences() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadService service = newService(minioClient);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));
        String displayKey = "images/2026/07/goods/display/goods_u12_20260712120000_a1b2c3_display.webp";
        String auditKey = "images/2026/07/goods/audit/goods_u12_20260712120000_a1b2c3_audit.webp";

        assertEquals(displayKey, service.validateUploadedDisplayReference(displayKey, "goods", 12L));
        assertEquals(auditKey, service.validateUploadedAuditThumbnailReference(auditKey, "goods", 12L));
    }

    @Test
    void acceptsLegacyJpegThumbnailReferenceDuringRollingUpgrade() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        UploadService service = newService(minioClient);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));
        String legacyThumbnail = "images/2026/07/goods/thumbs/goods_u12_20260712120000_a1b2c3_thumb.jpg";

        assertEquals(
                legacyThumbnail,
                service.validateUploadedThumbnailReference(legacyThumbnail, "goods", 12L)
        );
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
