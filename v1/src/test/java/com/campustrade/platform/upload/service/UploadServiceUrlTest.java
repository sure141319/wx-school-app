package com.campustrade.platform.upload.service;

import com.campustrade.platform.config.AppProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class UploadServiceUrlTest {

    @Test
    void resolvePublicUrlUsesConfiguredPublicBaseUrl() {
        UploadService service = new UploadService(
                mock(MinioClient.class),
                properties("https://cdn.example.com/campus-trade/")
        );

        String url = service.resolvePublicUrl("images/2026/04/demo.jpg");

        assertEquals("https://cdn.example.com/campus-trade/images/2026/04/demo.jpg", url);
    }

    @Test
    void extractObjectKeyAcceptsOnlyConfiguredPublicPrefix() {
        UploadService service = new UploadService(
                mock(MinioClient.class),
                properties("https://www.ahut-campus.site/minio/campus-trade")
        );

        assertEquals(
                "images/2026/04/demo.jpg",
                service.extractObjectKey(
                        "https://www.ahut-campus.site/minio/campus-trade/images/2026/04/demo.jpg?version=1#preview"
                )
        );
        assertNull(service.extractObjectKey(
                "https://www.ahut-campus.site/api/v1/images/2026/04/demo.jpg"
        ));
        assertNull(service.extractObjectKey(
                "https://untrusted.example/campus-trade/images/2026/04/demo.jpg"
        ));
    }

    @Test
    void requiresPublicBaseUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UploadService(mock(MinioClient.class), properties(""))
        );
    }

    private AppProperties properties(String publicBaseUrl) {
        AppProperties properties = new AppProperties();
        properties.getMinio().setEndpoint("http://127.0.0.1:9000");
        properties.getMinio().setAccessKey("access");
        properties.getMinio().setSecretKey("secret");
        properties.getMinio().setBucket("campus-trade");
        properties.getMinio().setPublicBaseUrl(publicBaseUrl);
        return properties;
    }
}
