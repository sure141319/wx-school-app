package com.campustrade.platform.upload.controller;

import com.campustrade.platform.upload.service.UploadService;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageProxyControllerTest {

    @Test
    void serveImageClosesMinioStreamAfterWritingResponse() throws Exception {
        UploadService uploadService = mock(UploadService.class);
        ImageProxyController controller = new ImageProxyController(uploadService);
        TrackingInputStream input = new TrackingInputStream("image-data".getBytes(StandardCharsets.UTF_8));
        StatObjectResponse info = mock(StatObjectResponse.class);

        when(info.contentType()).thenReturn("image/jpeg");
        when(info.size()).thenReturn(10L);
        when(info.etag()).thenReturn("abc123");
        when(uploadService.getImageInfo("images/2026/04/demo.jpg")).thenReturn(info);
        when(uploadService.getImageStream("images/2026/04/demo.jpg")).thenReturn(input);

        ResponseEntity<?> response = controller.serveImage("2026", "04", "demo.jpg");

        StreamingResponseBody body = assertInstanceOf(StreamingResponseBody.class, response.getBody());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        body.writeTo(output);

        assertArrayEquals("image-data".getBytes(StandardCharsets.UTF_8), output.toByteArray());
        assertTrue(input.closed);
    }

    @Test
    void serveImageSupportsUsageSubdirectoryPaths() throws Exception {
        UploadService uploadService = mock(UploadService.class);
        ImageProxyController controller = new ImageProxyController(uploadService);
        StatObjectResponse info = mock(StatObjectResponse.class);

        when(info.contentType()).thenReturn("image/jpeg");
        when(info.size()).thenReturn(10L);
        when(info.etag()).thenReturn("abc123");
        when(uploadService.getImageInfo("images/2026/07/goods/goods_u12_20260708183022_a8f3c2.jpg"))
                .thenReturn(info);
        when(uploadService.getImageStream("images/2026/07/goods/goods_u12_20260708183022_a8f3c2.jpg"))
                .thenReturn(new ByteArrayInputStream("image-data".getBytes(StandardCharsets.UTF_8)));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/images/2026/07/goods/goods_u12_20260708183022_a8f3c2.jpg"))
                .andExpect(status().isOk());
        verify(uploadService).getImageInfo("images/2026/07/goods/goods_u12_20260708183022_a8f3c2.jpg");
    }

    @Test
    void serveThumbnailSupportsUsageSubdirectoryPaths() throws Exception {
        UploadService uploadService = mock(UploadService.class);
        ImageProxyController controller = new ImageProxyController(uploadService);
        StatObjectResponse info = mock(StatObjectResponse.class);

        when(info.contentType()).thenReturn("image/jpeg");
        when(info.size()).thenReturn(10L);
        when(info.etag()).thenReturn("abc123");
        when(uploadService.getImageInfo("images/2026/07/goods/thumbs/goods_u12_20260708183022_a8f3c2_thumb.jpg"))
                .thenReturn(info);
        when(uploadService.getImageStream("images/2026/07/goods/thumbs/goods_u12_20260708183022_a8f3c2_thumb.jpg"))
                .thenReturn(new ByteArrayInputStream("thumb-data".getBytes(StandardCharsets.UTF_8)));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/images/2026/07/goods/thumbs/goods_u12_20260708183022_a8f3c2_thumb.jpg"))
                .andExpect(status().isOk());
        verify(uploadService).getImageInfo("images/2026/07/goods/thumbs/goods_u12_20260708183022_a8f3c2_thumb.jpg");
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
