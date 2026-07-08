package com.campustrade.platform.upload.controller;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.upload.service.UploadService;
import io.minio.StatObjectResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

        validateDatePath(year, month);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/" + filename);
    }

    @GetMapping("/{year}/{month}/thumbs/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveThumbnail(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String filename) {

        validateDatePath(year, month);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/thumbs/" + filename);
    }

    @GetMapping("/{year}/{month}/{usage}/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveUsageImage(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String usage,
            @PathVariable String filename) {

        validateDatePath(year, month);
        validateUsagePath(usage);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/" + usage + "/" + filename);
    }

    @GetMapping("/{year}/{month}/{usage}/thumbs/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveUsageThumbnail(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String usage,
            @PathVariable String filename) {

        validateDatePath(year, month);
        validateUsagePath(usage);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/" + usage + "/thumbs/" + filename);
    }

    private ResponseEntity<StreamingResponseBody> serveObject(String objectKey) {
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

    private void validateDatePath(String year, String month) {
        if (!year.matches("^\\d{4}$")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid year parameter");
        }
        if (!month.matches("^(0[1-9]|1[0-2])$")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid month parameter");
        }
    }

    private void validateUsagePath(String usage) {
        if (!"avatar".equals(usage) && !"goods".equals(usage)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid image usage parameter");
        }
    }

    private void validatePathSegment(String value, String message) {
        if (value.contains("..") || value.contains("/") || value.contains("\\")) {
            throw new AppException(HttpStatus.BAD_REQUEST, message);
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
