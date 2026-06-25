package com.campustrade.platform.upload.controller;

import com.campustrade.platform.upload.service.UploadService;
import io.minio.StatObjectResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
    public ResponseEntity<StreamingResponseBody> serveImage(
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
        MediaType mediaType = resolveMediaType(info.contentType());
        StreamingResponseBody body = outputStream -> {
            try (InputStream stream = uploadService.getImageStream(objectKey)) {
                stream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(info.size())
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.ETAG, info.etag())
                .body(body);
    }

    @GetMapping("/{year}/{month}/thumbs/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveThumbnail(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String filename) {

        if (!year.matches("^\\d{4}$")) {
            throw new com.campustrade.platform.common.AppException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid year parameter");
        }
        if (!month.matches("^(0[1-9]|1[0-2])$")) {
            throw new com.campustrade.platform.common.AppException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid month parameter");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new com.campustrade.platform.common.AppException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid filename parameter");
        }

        String objectKey = "images/" + year + "/" + month + "/thumbs/" + filename;

        StatObjectResponse info = uploadService.getImageInfo(objectKey);
        MediaType mediaType = resolveMediaType(info.contentType());
        StreamingResponseBody body = outputStream -> {
            try (InputStream stream = uploadService.getImageStream(objectKey)) {
                stream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(info.size())
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.ETAG, info.etag())
                .body(body);
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
