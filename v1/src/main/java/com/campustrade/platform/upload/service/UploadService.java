package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.upload.dto.response.UploadResponseDTO;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UploadService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif");
    private static final DateTimeFormatter OBJECT_PREFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UploadService.class);

    private final MinioClient minioClient;
    private final AppProperties.Minio minioProperties;
    private final String apiBaseUrl;
    private volatile boolean bucketReady;

    public UploadService(MinioClient minioClient, AppProperties appProperties) {
        this.minioClient = minioClient;
        this.minioProperties = appProperties.getMinio();
        this.apiBaseUrl = StringUtils.hasText(appProperties.getApiBaseUrl())
                ? trimTrailingSlash(appProperties.getApiBaseUrl().trim())
                : "";
    }

    public UploadResponseDTO storeImage(MultipartFile file) {
        validateImage(file);
        ensureBucketReady();

        String extension = getExtension(file.getOriginalFilename());
        String objectKey = buildObjectKey(extension);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(resolveContentType(file))
                            .build());
        } catch (Exception ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "图片上传失败", ex);
        }

        String url = StringUtils.hasText(minioProperties.getPublicBaseUrl())
                ? buildPublicUrl(objectKey)
                : buildProxyUrl(objectKey);
        return new UploadResponseDTO(url, objectKey);
    }

    private void ensureBucketReady() {
        if (bucketReady) {
            return;
        }

        synchronized (this) {
            if (bucketReady) {
                return;
            }

            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build());
                if (!exists) {
                    if (!minioProperties.isAutoCreateBucket()) {
                        throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "MinIO 存储桶不存在");
                    }
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
                }
                bucketReady = true;
            } catch (AppException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "MinIO 存储桶初始化失败", ex);
            }
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "请选择要上传的图片");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "仅支持 jpg/jpeg/png/webp 格式图片");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "仅支持图片文件上传");
        }
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return ".jpg";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        int index = lower.lastIndexOf('.');
        if (index < 0) {
            return ".jpg";
        }
        return lower.substring(index);
    }

    private String buildObjectKey(String extension) {
        String prefix = LocalDate.now(ZoneOffset.UTC).format(OBJECT_PREFIX_FORMATTER);
        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        return "images/" + prefix + "/" + filename;
    }

    private String resolveContentType(MultipartFile file) {
        return StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
    }

    private String buildPublicUrl(String objectKey) {
        String baseUrl = StringUtils.hasText(minioProperties.getPublicBaseUrl())
                ? minioProperties.getPublicBaseUrl().trim()
                : buildDefaultPublicBaseUrl();
        return trimTrailingSlash(baseUrl) + "/" + objectKey;
    }

    private String buildDefaultPublicBaseUrl() {
        String endpoint = minioProperties.getEndpoint().trim();
        String normalizedEndpoint;
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            normalizedEndpoint = endpoint;
        } else {
            normalizedEndpoint = (minioProperties.isSecure() ? "https://" : "http://") + endpoint;
        }
        return trimTrailingSlash(normalizedEndpoint) + "/" + minioProperties.getBucket();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public String presignUrl(String urlOrObjectKey) {
        if (!StringUtils.hasText(urlOrObjectKey)) {
            return urlOrObjectKey;
        }
        if (urlOrObjectKey.contains("X-Amz-")) {
            return urlOrObjectKey;
        }
        String objectKey = extractObjectKey(urlOrObjectKey);
        if (objectKey == null) {
            return urlOrObjectKey;
        }
        return presignObjectKey(objectKey);
    }

    public Map<String, String> presignUrls(List<String> urls) {
        Map<String, String> result = new HashMap<>();
        if (urls == null || urls.isEmpty()) {
            return result;
        }
        for (String url : urls) {
            result.put(url, presignUrl(url));
        }
        return result;
    }

    public String getProxyUrl(String urlOrObjectKey) {
        if (!StringUtils.hasText(urlOrObjectKey)) {
            return urlOrObjectKey;
        }
        if (urlOrObjectKey.contains("/api/v1/images/")) {
            return urlOrObjectKey;
        }
        String objectKey = extractObjectKey(urlOrObjectKey);
        if (objectKey == null) {
            return urlOrObjectKey;
        }
        if (StringUtils.hasText(minioProperties.getPublicBaseUrl())) {
            return buildPublicUrl(objectKey);
        }
        return buildProxyUrl(objectKey);
    }

    private String buildProxyUrl(String objectKey) {
        String path = objectKey.startsWith("images/") ? objectKey.substring("images/".length()) : objectKey;
        return apiBaseUrl + "/api/v1/images/" + path;
    }

    public String buildStaticAssetUrl(String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return apiBaseUrl + normalizedPath;
    }

    public InputStream getImageStream(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build());
        } catch (Exception ex) {
            throw new AppException(HttpStatus.NOT_FOUND, "图片不存在", ex);
        }
    }

    public StatObjectResponse getImageInfo(String objectKey) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build());
        } catch (Exception ex) {
            throw new AppException(HttpStatus.NOT_FOUND, "图片不存在", ex);
        }
    }

    private String presignObjectKey(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .expiry(minioProperties.getPresignExpiryDays(), TimeUnit.DAYS)
                            .build());
        } catch (Exception ex) {
            log.warn("Failed to presign object key: {}", objectKey, ex);
            return buildPublicUrl(objectKey);
        }
    }

    public String extractObjectKey(String urlOrObjectKey) {
        if (!StringUtils.hasText(urlOrObjectKey)) {
            return null;
        }

        String input = urlOrObjectKey;

        int queryIndex = input.indexOf('?');
        if (queryIndex > 0) {
            input = input.substring(0, queryIndex);
        }
        int fragmentIndex = input.indexOf('#');
        if (fragmentIndex > 0) {
            input = input.substring(0, fragmentIndex);
        }

        String proxyMarker = "/api/v1/images/";
        int proxyIndex = input.indexOf(proxyMarker);
        if (proxyIndex >= 0) {
            String relativePath = input.substring(proxyIndex + proxyMarker.length());
            return "images/" + relativePath;
        }

        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            return input;
        }

        String baseUrl = buildDefaultPublicBaseUrl();
        String prefix = trimTrailingSlash(baseUrl) + "/";
        if (input.startsWith(prefix)) {
            return input.substring(prefix.length());
        }

        if (StringUtils.hasText(minioProperties.getPublicBaseUrl())) {
            String publicPrefix = trimTrailingSlash(minioProperties.getPublicBaseUrl().trim()) + "/";
            if (input.startsWith(publicPrefix)) {
                return input.substring(publicPrefix.length());
            }
        }

        String bucket = minioProperties.getBucket();
        String bucketSegment = "/" + bucket + "/";
        int bucketIndex = input.indexOf(bucketSegment);
        if (bucketIndex >= 0) {
            return input.substring(bucketIndex + bucketSegment.length());
        }

        return null;
    }

    public void deleteObject(String urlOrObjectKey) {
        if (!StringUtils.hasText(urlOrObjectKey)) {
            return;
        }
        String objectKey = extractObjectKey(urlOrObjectKey);
        if (objectKey == null) {
            log.warn("Could not extract object key from URL: {}", urlOrObjectKey);
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build());
            log.info("Successfully deleted object: {}", objectKey);
        } catch (Exception ex) {
            log.warn("Failed to delete object: {}", objectKey, ex);
        }
    }
}
