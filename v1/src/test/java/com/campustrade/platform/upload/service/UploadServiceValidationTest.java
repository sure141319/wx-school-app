package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

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

    private UploadService newService() {
        AppProperties properties = new AppProperties();
        properties.setApiBaseUrl("https://www.ahut-campus.site");
        properties.getMinio().setEndpoint("http://127.0.0.1:9000");
        properties.getMinio().setAccessKey("access");
        properties.getMinio().setSecretKey("secret");
        properties.getMinio().setBucket("campus-trade");
        return new UploadService(mock(MinioClient.class), properties);
    }
}
