package com.campustrade.platform.upload.service;

import com.campustrade.platform.config.AppProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class UploadServiceKeyTest {

    @Test
    void buildObjectKeyUsesAvatarDirectoryAndReadablePrefix() throws Exception {
        UploadService service = newService();

        String key = buildObjectKey(service, ".jpg", "avatar", 12L);

        assertTrue(
                key.matches("^images/\\d{4}/\\d{2}/avatar/avatar_u12_\\d{14}_[0-9a-f]{6}\\.jpg$"),
                "avatar key should include date, usage directory, user id, timestamp and short random suffix: " + key
        );
    }

    @Test
    void buildObjectKeyDefaultsMissingUsageToGoodsDirectory() throws Exception {
        UploadService service = newService();

        String key = buildObjectKey(service, ".png", null, 8L);

        assertTrue(
                key.matches("^images/\\d{4}/\\d{2}/goods/goods_u8_\\d{14}_[0-9a-f]{6}\\.png$"),
                "missing usage should default to goods naming: " + key
        );
    }

    @Test
    void buildThumbnailObjectKeyKeepsThumbsUnderUsageDirectory() throws Exception {
        UploadService service = newService();

        String thumbnailKey = buildThumbnailObjectKey(
                service,
                "images/2026/07/goods/goods_u12_20260708183022_a8f3c2.jpg"
        );

        assertEquals(
                "images/2026/07/goods/thumbs/goods_u12_20260708183022_a8f3c2_thumb.jpg",
                thumbnailKey
        );
    }

    private static String buildObjectKey(UploadService service,
                                         String extension,
                                         String usage,
                                         Long userId) throws Exception {
        Method method = UploadService.class.getDeclaredMethod(
                "buildObjectKey",
                String.class,
                String.class,
                Long.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, extension, usage, userId);
    }

    private static String buildThumbnailObjectKey(UploadService service, String objectKey) throws Exception {
        Method method = UploadService.class.getDeclaredMethod("buildThumbnailObjectKey", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, objectKey);
    }

    private static UploadService newService() {
        AppProperties properties = new AppProperties();
        properties.setApiBaseUrl("https://www.ahut-campus.site");
        properties.getMinio().setEndpoint("http://127.0.0.1:9000");
        properties.getMinio().setAccessKey("access");
        properties.getMinio().setSecretKey("secret");
        properties.getMinio().setBucket("campus-trade");
        return new UploadService(mock(MinioClient.class), properties);
    }
}
