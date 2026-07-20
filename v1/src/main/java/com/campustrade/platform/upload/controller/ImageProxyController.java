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
import org.springframework.web.bind.annotation.RequestHeader;
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
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        validateDatePath(year, month);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/" + filename, ifNoneMatch);
    }

    public ResponseEntity<StreamingResponseBody> serveImage(String year, String month, String filename) {
        return serveImage(year, month, filename, null);
    }

    @GetMapping("/{year}/{month}/thumbs/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveThumbnail(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        validateDatePath(year, month);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/thumbs/" + filename, ifNoneMatch);
    }

    @GetMapping("/{year}/{month}/display/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveDisplay(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        validateDatePath(year, month);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/display/" + filename, ifNoneMatch);
    }

    @GetMapping("/{year}/{month}/audit/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveAuditThumbnail(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        validateDatePath(year, month);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/audit/" + filename, ifNoneMatch);
    }

    @GetMapping("/{year}/{month}/{usage}/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveUsageImage(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String usage,
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        validateDatePath(year, month);
        validateUsagePath(usage);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/" + usage + "/" + filename, ifNoneMatch);
    }

    @GetMapping("/{year}/{month}/{usage}/thumbs/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveUsageThumbnail(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String usage,
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        validateDatePath(year, month);
        validateUsagePath(usage);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/" + usage + "/thumbs/" + filename, ifNoneMatch);
    }

    @GetMapping("/{year}/{month}/{usage}/display/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveUsageDisplay(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String usage,
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        validateDatePath(year, month);
        validateUsagePath(usage);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/" + usage + "/display/" + filename, ifNoneMatch);
    }

    @GetMapping("/{year}/{month}/{usage}/audit/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveUsageAuditThumbnail(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String usage,
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        validateDatePath(year, month);
        validateUsagePath(usage);
        validatePathSegment(filename, "Invalid filename parameter");
        return serveObject("images/" + year + "/" + month + "/" + usage + "/audit/" + filename, ifNoneMatch);
    }

    private ResponseEntity<StreamingResponseBody> serveObject(String objectKey, String ifNoneMatch) {
        StatObjectResponse info = uploadService.getImageInfo(objectKey);
        String etag = quoteEtag(info.etag());
        CacheControl cacheControl = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();
        if (etagMatches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(cacheControl)
                    .header(HttpHeaders.ETAG, etag)
                    .body(null);
        }
        MediaType mediaType = resolveMediaType(info.contentType());
        StreamingResponseBody body = outputStream -> {
            try (InputStream stream = uploadService.getImageStream(objectKey)) {
                stream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(info.size())
                .cacheControl(cacheControl)
                .header(HttpHeaders.ETAG, etag)
                .body(body);
    }

    private String quoteEtag(String etag) {
        if (etag == null || etag.isBlank()) {
            return "\"\"";
        }
        String trimmed = etag.trim();
        return trimmed.startsWith("\"") || trimmed.startsWith("W/\"") ? trimmed : "\"" + trimmed + "\"";
    }

    private boolean etagMatches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        if ("*".equals(ifNoneMatch.trim())) {
            return true;
        }
        String normalizedEtag = etag.startsWith("W/") ? etag.substring(2) : etag;
        for (String candidate : ifNoneMatch.split(",")) {
            String normalizedCandidate = candidate.trim();
            if (normalizedCandidate.startsWith("W/")) {
                normalizedCandidate = normalizedCandidate.substring(2);
            }
            if (normalizedEtag.equals(normalizedCandidate)) {
                return true;
            }
        }
        return false;
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
