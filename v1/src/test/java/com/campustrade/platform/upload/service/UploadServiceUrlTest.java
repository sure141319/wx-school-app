package com.campustrade.platform.upload.service;

import com.campustrade.platform.config.AppProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class UploadServiceUrlTest {

    @Test
    void getProxyUrlUsesPublicBaseUrlWhenConfigured() {
        AppProperties properties = new AppProperties();
        properties.setApiBaseUrl("https://www.ahut-campus.site");
        properties.getMinio().setEndpoint("http://127.0.0.1:9000");
        properties.getMinio().setAccessKey("access");
        properties.getMinio().setSecretKey("secret");
        properties.getMinio().setBucket("campus-trade");
        properties.getMinio().setPublicBaseUrl("https://cdn.example.com/campus-trade");
        UploadService service = new UploadService(mock(MinioClient.class), properties);

        String url = service.getProxyUrl("images/2026/04/demo.jpg");

        assertEquals("https://cdn.example.com/campus-trade/images/2026/04/demo.jpg", url);
    }
}
