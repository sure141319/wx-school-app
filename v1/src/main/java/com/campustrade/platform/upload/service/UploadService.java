package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.common.time.BeijingTime;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.upload.dataobject.UploadObjectDO;
import com.campustrade.platform.upload.dto.response.UploadResponseDTO;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadService {

    private static final DateTimeFormatter OBJECT_PREFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final DateTimeFormatter OBJECT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String USAGE_AVATAR = "avatar";
    private static final String USAGE_GOODS = "goods";
    private static final String WEBP_FORMAT = "webp";
    private static final String WEBP_CONTENT_TYPE = "image/webp";
    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UploadService.class);

    private final MinioClient minioClient;
    private final AppProperties.Minio minioProperties;
    private final UploadLifecycleService uploadLifecycleService;
    private final UploadImageProcessor imageProcessor;
    private final UploadImageValidator imageValidator;
    private final String apiBaseUrl;
    private volatile boolean bucketReady;

    public record ImageVariantKeys(
            String thumbnailObjectKey,
            String displayObjectKey,
            String auditThumbnailObjectKey
    ) {
        private static ImageVariantKeys empty() {
            return new ImageVariantKeys(null, null, null);
        }
    }

    private record VariantPayload(String objectKey, byte[] bytes) {
    }

    private record PreparedVariants(
            VariantPayload master,
            VariantPayload thumbnail
    ) {
        private static PreparedVariants empty() {
            return new PreparedVariants(null, null);
        }

        private ImageVariantKeys keys() {
            return new ImageVariantKeys(
                    thumbnail == null ? null : thumbnail.objectKey(),
                    null,
                    null
            );
        }

        private boolean completeForUpload() {
            return master != null && thumbnail != null;
        }

        private long totalBytes() {
            long total = 0;
            for (VariantPayload payload : payloads()) {
                total += payload.bytes().length;
            }
            return total;
        }

        private List<VariantPayload> payloads() {
            List<VariantPayload> result = new ArrayList<>(2);
            if (master != null) {
                result.add(master);
            }
            if (thumbnail != null) {
                result.add(thumbnail);
            }
            return result;
        }
    }

    @Autowired
    public UploadService(MinioClient minioClient,
                         AppProperties appProperties,
                         UploadLifecycleService uploadLifecycleService,
                         UploadImageProcessor imageProcessor,
                         UploadImageValidator imageValidator) {
        this.minioClient = minioClient;
        this.minioProperties = appProperties.getMinio();
        this.uploadLifecycleService = uploadLifecycleService;
        this.imageProcessor = imageProcessor;
        this.imageValidator = imageValidator == null ? new UploadImageValidator(imageProcessor) : imageValidator;
        this.apiBaseUrl = StringUtils.hasText(appProperties.getApiBaseUrl())
                ? trimTrailingSlash(appProperties.getApiBaseUrl().trim())
                : "";
    }

    public UploadService(MinioClient minioClient,
                         AppProperties appProperties,
                         UploadLifecycleService uploadLifecycleService) {
        this(minioClient, appProperties, uploadLifecycleService, new UploadImageProcessor(), null);
    }

    public UploadService(MinioClient minioClient, AppProperties appProperties) {
        this(minioClient, appProperties, null);
    }

    public UploadResponseDTO storeImage(MultipartFile file) {
        return storeImage(file, USAGE_GOODS, null);
    }

    public UploadResponseDTO storeImage(MultipartFile file, String usage, Long userId) {
        validateImage(file);
        String normalizedUsage = normalizeUsage(usage);
        String extension = ".webp";
        String objectKey = buildObjectKey(extension, normalizedUsage, userId);
        ImageVariantKeys expectedVariants = USAGE_GOODS.equals(normalizedUsage)
                ? new ImageVariantKeys(buildThumbnailObjectKey(objectKey), null, null)
                : ImageVariantKeys.empty();
        UploadLifecycleService lifecycle = requireUploadLifecycle();
        UploadObjectDO reservation = lifecycle.reserve(
                userId,
                normalizedUsage,
                objectKey,
                file.getSize(),
                expectedVariants
        );

        try {
            ensureBucketReady();
            PreparedVariants preparedVariants = USAGE_GOODS.equals(normalizedUsage)
                    ? prepareVariants(file, objectKey)
                    : PreparedVariants.empty();
            VariantPayload optimizedAvatar = USAGE_AVATAR.equals(normalizedUsage)
                    ? prepareAvatar(file, objectKey)
                    : null;
            long totalSizeBytes = optimizedAvatar == null
                    ? preparedVariants.totalBytes()
                    : optimizedAvatar.bytes().length;

            if (optimizedAvatar != null) {
                putWebpVariant(optimizedAvatar);
            }
            for (VariantPayload payload : preparedVariants.payloads()) {
                putWebpVariant(payload);
            }

            ImageVariantKeys variants = preparedVariants.keys();
            lifecycle.markStaged(reservation.getId(), userId, file.getSize(), variants, totalSizeBytes);
            String url = StringUtils.hasText(minioProperties.getPublicBaseUrl())
                    ? buildPublicUrl(objectKey)
                    : buildProxyUrl(objectKey);
            return new UploadResponseDTO(
                    url,
                    objectKey,
                    buildDeliveryUrl(variants.thumbnailObjectKey()),
                    variants.thumbnailObjectKey(),
                    buildDeliveryUrl(variants.displayObjectKey()),
                    variants.displayObjectKey(),
                    buildDeliveryUrl(variants.auditThumbnailObjectKey()),
                    variants.auditThumbnailObjectKey(),
                    true
            );
        } catch (Exception ex) {
            try {
                lifecycle.markForCleanup(reservation.getId());
            } catch (RuntimeException cleanupStateException) {
                log.warn("Failed to mark upload {} for cleanup", reservation.getObjectKey(), cleanupStateException);
            }
            try {
                deleteClaimedUpload(reservation);
            } catch (RuntimeException cleanupException) {
                log.warn("Failed to clean upload {} after upload error", reservation.getObjectKey(), cleanupException);
            }
            if (ex instanceof AppException appException) {
                throw appException;
            }
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "图片上传失败", ex);
        }
    }

    private PreparedVariants prepareVariants(MultipartFile file, String objectKey) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            PreparedVariants prepared = prepareVariants(inputStream, objectKey, true);
            if (!prepared.completeForUpload()) {
                throw new AppException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "图片格式暂不支持，请选择 JPG、PNG 或 WebP 图片"
                );
            }
            return prepared;
        }
    }

    private VariantPayload prepareAvatar(MultipartFile file, String objectKey) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            return new VariantPayload(objectKey, optimizeAvatar(inputStream));
        }
    }

    byte[] optimizeAvatar(InputStream inputStream) throws IOException {
        return imageProcessor.optimizeAvatar(inputStream);
    }

    public String generateThumbnailForObject(String urlOrObjectKey) {
        return generateVariantsForObject(urlOrObjectKey).thumbnailObjectKey();
    }

    public ImageVariantKeys generateVariantsForObject(String urlOrObjectKey) {
        String objectKey = extractObjectKey(urlOrObjectKey);
        if (!StringUtils.hasText(objectKey)) {
            return ImageVariantKeys.empty();
        }

        ensureBucketReady();
        UploadObjectDO trackedUpload = uploadLifecycleService == null
                ? null
                : uploadLifecycleService.findByObjectKey(objectKey);
        try (InputStream inputStream = getImageStream(objectKey)) {
            PreparedVariants prepared = prepareVariants(inputStream, objectKey, false);
            ImageVariantKeys generatedVariants = storePreparedVariants(prepared);
            ImageVariantKeys trackedVariants = mergeTrackedVariantKeys(trackedUpload, generatedVariants);
            if (trackedUpload != null
                    && prepared.totalBytes() > 0
                    && !uploadLifecycleService.updateTrackedVariants(objectKey, trackedVariants, prepared.totalBytes())) {
                deleteObjectKeys(prepared.keys().thumbnailObjectKey() == null
                        ? List.of()
                        : List.of(prepared.keys().thumbnailObjectKey()));
                return ImageVariantKeys.empty();
            }
            return generatedVariants;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to backfill WebP variants for object: {}", objectKey, ex);
            return ImageVariantKeys.empty();
        }
    }

    private ImageVariantKeys mergeTrackedVariantKeys(UploadObjectDO trackedUpload,
                                                      ImageVariantKeys generatedVariants) {
        if (trackedUpload == null) {
            return generatedVariants;
        }
        return new ImageVariantKeys(
                firstPresent(generatedVariants.thumbnailObjectKey(), trackedUpload.getThumbnailObjectKey()),
                firstPresent(generatedVariants.displayObjectKey(), trackedUpload.getDisplayObjectKey()),
                firstPresent(generatedVariants.auditThumbnailObjectKey(), trackedUpload.getAuditThumbnailObjectKey())
        );
    }

    private String firstPresent(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private ImageVariantKeys storePreparedVariants(PreparedVariants prepared) throws Exception {
        List<String> writtenObjectKeys = new ArrayList<>(1);
        try {
            for (VariantPayload payload : prepared.payloads()) {
                putWebpVariant(payload);
                writtenObjectKeys.add(payload.objectKey());
            }
            return prepared.keys();
        } catch (Exception ex) {
            deleteObjectKeys(writtenObjectKeys);
            throw ex;
        }
    }

    private PreparedVariants prepareVariants(InputStream inputStream,
                                              String objectKey,
                                              boolean includeCompressedMaster) throws Exception {
        UploadImageProcessor.ProcessedVariants processed = imageProcessor.prepareVariants(
                inputStream,
                includeCompressedMaster
        );
        if (processed.thumbnail() == null) {
            log.warn("Skip WebP variant generation for unsupported image format: {}", objectKey);
            return PreparedVariants.empty();
        }

        String thumbnailObjectKey = buildThumbnailObjectKey(objectKey);
        return new PreparedVariants(
                processed.master() == null
                        ? null
                        : new VariantPayload(objectKey, processed.master()),
                new VariantPayload(thumbnailObjectKey, processed.thumbnail())
        );
    }

    private void putWebpVariant(VariantPayload payload) throws Exception {
        try (ByteArrayInputStream variantInput = new ByteArrayInputStream(payload.bytes())) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(payload.objectKey())
                            .stream(variantInput, payload.bytes().length, -1)
                            .contentType(WEBP_CONTENT_TYPE)
                            .headers(Map.of("Cache-Control", IMMUTABLE_CACHE_CONTROL))
                            .build());
        }
    }

    BufferedImage readImageForThumbnail(InputStream inputStream) throws IOException {
        return imageProcessor.readImageForThumbnail(inputStream);
    }

    BufferedImage readImageForVariants(InputStream inputStream) throws IOException {
        return imageProcessor.readImageForVariants(inputStream);
    }

    byte[] encodeWebp(BufferedImage image, float quality) throws IOException {
        return imageProcessor.encodeWebp(image, quality);
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

    void validateImage(MultipartFile file) {
        imageValidator.validate(file);
    }

    void validateImageDimensions(int width, int height) {
        imageProcessor.validateImageDimensions(width, height);
    }

    int calculateThumbnailSubsampling(int width, int height) {
        return imageProcessor.calculateThumbnailSubsampling(width, height);
    }

    private String buildObjectKey(String extension, String usage, Long userId) {
        return buildObjectKey(extension, usage, userId, BeijingTime.now());
    }

    private String buildObjectKey(String extension, String usage, Long userId, LocalDateTime uploadTime) {
        String normalizedUsage = normalizeUsage(usage);
        LocalDateTime now = uploadTime == null ? BeijingTime.now() : uploadTime;
        String prefix = now.format(OBJECT_PREFIX_FORMATTER);
        String timestamp = now.format(OBJECT_TIMESTAMP_FORMATTER);
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        long safeUserId = userId == null || userId <= 0 ? 0 : userId;
        String filename = normalizedUsage + "_u" + safeUserId + "_" + timestamp + "_" + randomSuffix + extension;
        return "images/" + prefix + "/" + normalizedUsage + "/" + filename;
    }

    private String normalizeUsage(String usage) {
        if (!StringUtils.hasText(usage)) {
            return USAGE_GOODS;
        }
        String normalized = usage.trim().toLowerCase(Locale.ROOT);
        if (USAGE_AVATAR.equals(normalized) || USAGE_GOODS.equals(normalized)) {
            return normalized;
        }
        throw new AppException(HttpStatus.BAD_REQUEST, "图片用途参数无效");
    }

    private String buildThumbnailObjectKey(String objectKey) {
        return buildVariantObjectKey(objectKey, "thumbs", "thumb");
    }

    private String buildDisplayObjectKey(String objectKey) {
        return buildVariantObjectKey(objectKey, "display", "display");
    }

    private String buildAuditThumbnailObjectKey(String objectKey) {
        return buildVariantObjectKey(objectKey, "audit", "audit");
    }

    private String buildVariantObjectKey(String objectKey, String directoryName, String suffix) {
        int slashIndex = objectKey.lastIndexOf('/');
        String directory = slashIndex >= 0 ? objectKey.substring(0, slashIndex) : "";
        String filename = slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
        int dotIndex = filename.lastIndexOf('.');
        String basename = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        String prefix = StringUtils.hasText(directory) ? directory + "/" + directoryName + "/" : directoryName + "/";
        return prefix + basename + "_" + suffix + "." + WEBP_FORMAT;
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

    private String buildDeliveryUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        return StringUtils.hasText(minioProperties.getPublicBaseUrl())
                ? buildPublicUrl(objectKey)
                : buildProxyUrl(objectKey);
    }

    public String validateUploadedImageReference(String urlOrObjectKey, String usage, Long userId) {
        return validateUploadedObjectReference(urlOrObjectKey, usage, userId, null, null);
    }

    public String validateUploadedThumbnailReference(String urlOrObjectKey, String usage, Long userId) {
        return validateUploadedObjectReference(urlOrObjectKey, usage, userId, "thumbs", "_thumb.webp");
    }

    public String validateUploadedDisplayReference(String urlOrObjectKey, String usage, Long userId) {
        return validateUploadedObjectReference(urlOrObjectKey, usage, userId, "display", "_display.webp");
    }

    public String validateUploadedAuditThumbnailReference(String urlOrObjectKey, String usage, Long userId) {
        return validateUploadedObjectReference(urlOrObjectKey, usage, userId, "audit", "_audit.webp");
    }

    private String validateUploadedObjectReference(String urlOrObjectKey,
                                                   String usage,
                                                   Long userId,
                                                   String variantDirectory,
                                                   String expectedSuffix) {
        String objectKey = extractObjectKey(urlOrObjectKey);
        if (!StringUtils.hasText(objectKey)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图片地址无效");
        }
        String normalizedUsage = normalizeUsage(usage);
        if (!isOwnedUploadObjectKey(objectKey, normalizedUsage, userId, variantDirectory, expectedSuffix)) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权使用该图片");
        }
        ensureObjectExistsForReference(objectKey);
        return objectKey;
    }

    private boolean isOwnedUploadObjectKey(String objectKey,
                                           String usage,
                                           Long userId,
                                           String variantDirectory,
                                           String expectedSuffix) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(objectKey)) {
            return false;
        }
        String[] segments = objectKey.split("/");
        boolean variant = StringUtils.hasText(variantDirectory);
        int expectedLength = variant ? 6 : 5;
        if (segments.length != expectedLength) {
            return false;
        }
        if (!"images".equals(segments[0])
                || !segments[1].matches("^\\d{4}$")
                || !segments[2].matches("^(0[1-9]|1[0-2])$")
                || !usage.equals(segments[3])) {
            return false;
        }
        String filename = variant ? segments[5] : segments[4];
        if (variant && !variantDirectory.equals(segments[4])) {
            return false;
        }
        if (variant) {
            boolean validSuffix = filename.endsWith(expectedSuffix);
            if ("thumbs".equals(variantDirectory)) {
                validSuffix = validSuffix || filename.endsWith("_thumb.jpg");
            }
            if (!validSuffix) {
                return false;
            }
        }
        return filename.startsWith(usage + "_u" + userId + "_");
    }

    private void ensureObjectExistsForReference(String objectKey) {
        try {
            getImageInfo(objectKey);
        } catch (AppException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图片文件不存在或已失效", ex);
        }
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

    public ImageVariantKeys bindUploadedImageToGoods(String urlOrObjectKey, Long userId, Long goodsId) {
        String objectKey = extractRequiredObjectKey(urlOrObjectKey);
        UploadObjectDO record = requireUploadLifecycle().bindToGoods(objectKey, userId, goodsId);
        return variantKeys(record);
    }

    public void bindUploadedImageToAvatar(String urlOrObjectKey, Long userId) {
        String objectKey = extractRequiredObjectKey(urlOrObjectKey);
        requireUploadLifecycle().bindToAvatar(objectKey, userId);
    }

    private String extractRequiredObjectKey(String urlOrObjectKey) {
        String objectKey = extractObjectKey(urlOrObjectKey);
        if (!StringUtils.hasText(objectKey)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图片地址无效");
        }
        return objectKey;
    }

    public void deleteStagedUpload(Long userId, String urlOrObjectKey) {
        String objectKey = extractObjectKey(urlOrObjectKey);
        if (!StringUtils.hasText(objectKey)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图片地址无效");
        }
        UploadObjectDO record = requireUploadLifecycle().beginStagedDeletion(objectKey, userId);
        if (record != null && !deleteClaimedUpload(record)) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "图片删除失败，系统稍后会自动重试");
        }
    }

    public boolean deleteClaimedUpload(UploadObjectDO record) {
        boolean deleted = deleteObjectKeys(objectKeys(record));
        if (deleted) {
            requireUploadLifecycle().deleteRecord(record.getId());
        }
        return deleted;
    }

    public void deleteUploadGroupAfterCommit(String originalObjectKey,
                                             String thumbnailObjectKey,
                                             String displayObjectKey,
                                             String auditThumbnailObjectKey) {
        if (!StringUtils.hasText(originalObjectKey)) {
            return;
        }
        UploadLifecycleService lifecycle = requireUploadLifecycle();
        UploadObjectDO record = lifecycle.beginBoundDeletion(extractObjectKey(originalObjectKey));
        Runnable deletion = () -> deletePreparedUploadGroup(
                record,
                originalObjectKey,
                thumbnailObjectKey,
                displayObjectKey,
                auditThumbnailObjectKey
        );
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deletion.run();
            }
        });
    }

    private void deletePreparedUploadGroup(UploadObjectDO record,
                                           String originalObjectKey,
                                           String thumbnailObjectKey,
                                           String displayObjectKey,
                                           String auditThumbnailObjectKey) {
        if (record != null) {
            deleteClaimedUpload(record);
            return;
        }
        deleteObjectKeys(List.of(
                originalObjectKey,
                thumbnailObjectKey == null ? "" : thumbnailObjectKey,
                displayObjectKey == null ? "" : displayObjectKey,
                auditThumbnailObjectKey == null ? "" : auditThumbnailObjectKey
        ));
    }

    private ImageVariantKeys variantKeys(UploadObjectDO record) {
        return new ImageVariantKeys(
                record.getThumbnailObjectKey(),
                record.getDisplayObjectKey(),
                record.getAuditThumbnailObjectKey()
        );
    }

    private List<String> objectKeys(UploadObjectDO record) {
        return List.of(
                record.getObjectKey(),
                record.getThumbnailObjectKey() == null ? "" : record.getThumbnailObjectKey(),
                record.getDisplayObjectKey() == null ? "" : record.getDisplayObjectKey(),
                record.getAuditThumbnailObjectKey() == null ? "" : record.getAuditThumbnailObjectKey()
        );
    }

    private UploadLifecycleService requireUploadLifecycle() {
        if (uploadLifecycleService == null) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "上传生命周期服务未初始化");
        }
        return uploadLifecycleService;
    }

    private boolean deleteObjectKeys(Iterable<String> urlOrObjectKeys) {
        Set<String> uniqueObjectKeys = new LinkedHashSet<>();
        for (String urlOrObjectKey : urlOrObjectKeys) {
            String objectKey = extractObjectKey(urlOrObjectKey);
            if (StringUtils.hasText(objectKey)) {
                uniqueObjectKeys.add(objectKey);
            }
        }
        boolean allDeleted = true;
        for (String objectKey : uniqueObjectKeys) {
            allDeleted = deleteObjectKey(objectKey) && allDeleted;
        }
        return allDeleted;
    }

    private boolean deleteObjectKey(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build());
            log.info("Successfully deleted object: {}", objectKey);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to delete object: {}", objectKey, ex);
            return false;
        }
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
        deleteObjectKey(objectKey);
    }

    public void deleteObjectAfterCommit(String urlOrObjectKey) {
        if (!StringUtils.hasText(urlOrObjectKey)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteObject(urlOrObjectKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteObject(urlOrObjectKey);
            }
        });
    }
}
