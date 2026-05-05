package com.campustrade.platform.upload.controller;

import com.campustrade.platform.upload.service.UploadService;
import io.minio.StatObjectResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/images")
public class ImageProxyController {

    private final UploadService uploadService;

    public ImageProxyController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @GetMapping("/{year}/{month}/{filename:.+}")
    public ResponseEntity<InputStreamResource> serveImage(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String filename) {

        if (!year.matches("^\\d{4}$")) {
            throw new com.campustrade.platform.common.AppException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "无效的年份参数");
        }
        if (!month.matches("^(0[1-9]|1[0-2])$")) {
            throw new com.campustrade.platform.common.AppException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "无效的月份参数");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new com.campustrade.platform.common.AppException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "无效的文件名参数");
        }

        String objectKey = "images/" + year + "/" + month + "/" + filename;

        StatObjectResponse info = uploadService.getImageInfo(objectKey);
        InputStream stream = uploadService.getImageStream(objectKey);

        MediaType mediaType = resolveMediaType(info.contentType());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(info.size())
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.ETAG, info.etag())
                .body(new CloseableInputStreamResource(stream, info.size()));
    }

    private static class CloseableInputStreamResource extends InputStreamResource {

        private final InputStream inputStream;
        private final long contentLength;

        CloseableInputStreamResource(InputStream inputStream, long contentLength) {
            super(inputStream);
            this.inputStream = inputStream;
            this.contentLength = contentLength;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public String getDescription() {
            return "Image stream resource";
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                inputStream.close();
            } finally {
                super.finalize();
            }
        }
    }

    private MediaType resolveMediaType(String contentType) {
        if (contentType != null) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
