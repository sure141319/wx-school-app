package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif");
    private static final Map<String, List<String>> ALLOWED_CONTENT_TYPES_BY_EXTENSION = Map.of(
            ".jpg", List.of("image/jpeg", "image/jpg", "image/pjpeg"),
            ".jpeg", List.of("image/jpeg", "image/jpg", "image/pjpeg"),
            ".png", List.of("image/png"),
            ".webp", List.of("image/webp"),
            ".heic", List.of("image/heic", "image/heif"),
            ".heif", List.of("image/heif", "image/heic")
    );
    private static final int IMAGE_HEADER_BYTES = 32;
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_IMAGE_WIDTH = 10_000;
    private static final int MAX_IMAGE_HEIGHT = 10_000;
    private static final long MAX_IMAGE_PIXELS = 50_000_000L;
    private static final ZoneId UPLOAD_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OBJECT_PREFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final DateTimeFormatter OBJECT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String USAGE_AVATAR = "avatar";
    private static final String USAGE_GOODS = "goods";
    private static final int THUMBNAIL_MAX_SIZE = 640;
    private static final int THUMBNAIL_DECODE_MAX_SIZE = 960;
    private static final int AVATAR_DECODE_MAX_SIZE = 768;
    private static final int AVATAR_MAX_SIZE = 320;
    private static final int VARIANT_DECODE_MAX_SIZE = 2_560;
    private static final int DISPLAY_MAX_SIZE = 1_600;
    private static final float THUMBNAIL_QUALITY = 0.70f;
    private static final float AVATAR_QUALITY = 0.72f;
    private static final float DISPLAY_QUALITY = 0.82f;
    private static final String WEBP_FORMAT = "webp";
    private static final String WEBP_CONTENT_TYPE = "image/webp";
    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UploadService.class);

    private final MinioClient minioClient;
    private final AppProperties.Minio minioProperties;
    private final UploadLifecycleService uploadLifecycleService;
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
                         UploadLifecycleService uploadLifecycleService) {
        this.minioClient = minioClient;
        this.minioProperties = appProperties.getMinio();
        this.uploadLifecycleService = uploadLifecycleService;
        this.apiBaseUrl = StringUtils.hasText(appProperties.getApiBaseUrl())
                ? trimTrailingSlash(appProperties.getApiBaseUrl().trim())
                : "";
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
        UploadLifecycleService lifecycle = requireUploadLifecycle();
        UploadObjectDO reservation = lifecycle.reserve(userId, normalizedUsage, objectKey, file.getSize());
        List<String> writtenObjectKeys = new ArrayList<>(2);

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
                writtenObjectKeys.add(objectKey);
            }
            for (VariantPayload payload : preparedVariants.payloads()) {
                putWebpVariant(payload);
                writtenObjectKeys.add(payload.objectKey());
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
            boolean rolledBack = deleteObjectKeys(writtenObjectKeys);
            if (rolledBack) {
                lifecycle.deleteRecord(reservation.getId());
            } else {
                lifecycle.markForCleanup(reservation.getId());
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
        BufferedImage source = readImageWithSubsampling(inputStream, AVATAR_DECODE_MAX_SIZE);
        if (source == null) {
            throw new AppException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "头像格式暂不支持，请选择 JPG、PNG 或 WebP 图片");
        }
        byte[] encoded = encodeWebp(resize(source, AVATAR_MAX_SIZE), AVATAR_QUALITY);
        if (encoded.length == 0) {
            throw new IOException("WebP encoder is unavailable");
        }
        return encoded;
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
        BufferedImage source = readImageForVariants(inputStream);
        if (source == null) {
            log.warn("Skip WebP variant generation for unsupported image format: {}", objectKey);
            return PreparedVariants.empty();
        }

        String thumbnailObjectKey = buildThumbnailObjectKey(objectKey);

        return new PreparedVariants(
                includeCompressedMaster
                        ? prepareWebpVariant(objectKey, resize(source, DISPLAY_MAX_SIZE), DISPLAY_QUALITY)
                        : null,
                prepareWebpVariant(thumbnailObjectKey, resize(source, THUMBNAIL_MAX_SIZE), THUMBNAIL_QUALITY)
        );
    }

    private VariantPayload prepareWebpVariant(String objectKey, BufferedImage image, float quality) throws Exception {
        byte[] bytes = encodeWebp(image, quality);
        if (bytes.length == 0) {
            throw new IOException("WebP encoder is unavailable");
        }
        return new VariantPayload(objectKey, bytes);
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
        return readImageWithSubsampling(inputStream, THUMBNAIL_DECODE_MAX_SIZE);
    }

    BufferedImage readImageForVariants(InputStream inputStream) throws IOException {
        return readImageWithSubsampling(inputStream, VARIANT_DECODE_MAX_SIZE);
    }

    private BufferedImage readImageWithSubsampling(InputStream inputStream, int decodeMaxSize) throws IOException {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(inputStream)) {
            if (imageInput == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateImageDimensions(width, height);

                ImageReadParam readParam = reader.getDefaultReadParam();
                int subsampling = calculateSubsampling(width, height, decodeMaxSize);
                readParam.setSourceSubsampling(subsampling, subsampling, 0, 0);
                return reader.read(0, readParam);
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage resize(BufferedImage source, int maxSize) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double scale = Math.min(
                (double) maxSize / sourceWidth,
                (double) maxSize / sourceHeight
        );
        scale = Math.min(1.0d, scale);

        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    byte[] encodeWebp(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(WEBP_FORMAT);
        if (!writers.hasNext()) {
            return new byte[0];
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] compressionTypes = params.getCompressionTypes();
                if (compressionTypes != null) {
                    for (String compressionType : compressionTypes) {
                        if ("Lossy".equalsIgnoreCase(compressionType)) {
                            params.setCompressionType(compressionType);
                            break;
                        }
                    }
                }
                params.setCompressionQuality(Math.max(0.0f, Math.min(1.0f, quality)));
            }
            writer.write(null, new IIOImage(image, null, null), params);
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
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
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "图片文件不能超过 10MB");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "仅支持 jpg/jpeg/png/webp/heic/heif 格式图片");
        }
        String contentType = normalizeContentType(file.getContentType());
        List<String> allowedContentTypes = ALLOWED_CONTENT_TYPES_BY_EXTENSION.get(extension);
        if (!allowedContentTypes.contains(contentType)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图片格式与文件类型不匹配");
        }
        if (!hasExpectedImageSignature(file, extension)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图片文件内容无效");
        }
        validateImageDimensions(file, extension);
    }

    private void validateImageDimensions(MultipartFile file, String extension) {
        try (InputStream inputStream = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(inputStream)) {
            if (imageInput == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "图片文件内容无效");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                if (".jpg".equals(extension) || ".jpeg".equals(extension) || ".png".equals(extension)) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "图片文件内容无效");
                }
                return;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                validateImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (AppException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图片文件内容无效", ex);
        }
    }

    void validateImageDimensions(int width, int height) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0
                || width > MAX_IMAGE_WIDTH
                || height > MAX_IMAGE_HEIGHT
                || pixels > MAX_IMAGE_PIXELS) {
            throw new AppException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "图片分辨率过大，最大支持 10000×10000 且不超过 5000 万像素"
            );
        }
    }

    int calculateThumbnailSubsampling(int width, int height) {
        return calculateSubsampling(width, height, THUMBNAIL_DECODE_MAX_SIZE);
    }

    private int calculateSubsampling(int width, int height, int decodeMaxSize) {
        int largestDimension = Math.max(width, height);
        return Math.max(1, (int) Math.ceil((double) largestDimension / decodeMaxSize));
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int parameterIndex = normalized.indexOf(';');
        return parameterIndex >= 0 ? normalized.substring(0, parameterIndex).trim() : normalized;
    }

    private boolean hasExpectedImageSignature(MultipartFile file, String extension) {
        byte[] header = readImageHeader(file);
        return switch (extension) {
            case ".jpg", ".jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case ".png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case ".webp" -> header.length >= 12 && asciiEquals(header, 0, "RIFF") && asciiEquals(header, 8, "WEBP");
            case ".heic", ".heif" -> isHeifFamily(header);
            default -> false;
        };
    }

    private byte[] readImageHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(IMAGE_HEADER_BYTES);
        } catch (IOException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图片文件读取失败", ex);
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isHeifFamily(byte[] header) {
        if (header.length < 12 || !asciiEquals(header, 4, "ftyp")) {
            return false;
        }
        for (int offset = 8; offset + 4 <= header.length; offset += 4) {
            if (isHeifBrand(header, offset)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeifBrand(byte[] header, int offset) {
        return asciiEquals(header, offset, "heic")
                || asciiEquals(header, offset, "heix")
                || asciiEquals(header, offset, "hevc")
                || asciiEquals(header, offset, "hevx")
                || asciiEquals(header, offset, "heif")
                || asciiEquals(header, offset, "mif1")
                || asciiEquals(header, offset, "msf1");
    }

    private boolean asciiEquals(byte[] value, int offset, String expected) {
        if (offset < 0 || value.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (value[offset + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
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

    private String buildObjectKey(String extension, String usage, Long userId) {
        return buildObjectKey(extension, usage, userId, LocalDateTime.now(UPLOAD_TIME_ZONE));
    }

    private String buildObjectKey(String extension, String usage, Long userId, LocalDateTime uploadTime) {
        String normalizedUsage = normalizeUsage(usage);
        LocalDateTime now = uploadTime == null ? LocalDateTime.now(UPLOAD_TIME_ZONE) : uploadTime;
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
        throw new AppException(HttpStatus.BAD_REQUEST, "Invalid image usage");
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

    private String resolveContentType(MultipartFile file) {
        return switch (getExtension(file.getOriginalFilename())) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            case ".heic" -> "image/heic";
            case ".heif" -> "image/heif";
            default -> "application/octet-stream";
        };
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
        Runnable deletion = () -> deleteUploadGroupNow(
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

    private void deleteUploadGroupNow(String originalObjectKey,
                                      String thumbnailObjectKey,
                                      String displayObjectKey,
                                      String auditThumbnailObjectKey) {
        UploadLifecycleService lifecycle = requireUploadLifecycle();
        UploadObjectDO record = lifecycle.beginBoundDeletion(extractObjectKey(originalObjectKey));
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
