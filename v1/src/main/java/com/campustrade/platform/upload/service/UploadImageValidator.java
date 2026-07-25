package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class UploadImageValidator {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif");
    private static final Map<String, List<String>> ALLOWED_CONTENT_TYPES_BY_EXTENSION = Map.of(
            ".jpg", List.of("image/jpeg", "image/jpg", "image/pjpeg"),
            ".jpeg", List.of("image/jpeg", "image/jpg", "image/pjpeg"),
            ".png", List.of("image/png"),
            ".webp", List.of("image/webp"),
            ".heic", List.of(
                    "image/heic",
                    "image/heif",
                    "image/heic-sequence",
                    "image/heif-sequence",
                    "application/octet-stream"
            ),
            ".heif", List.of(
                    "image/heif",
                    "image/heic",
                    "image/heif-sequence",
                    "image/heic-sequence",
                    "application/octet-stream"
            )
    );
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;

    private final UploadImageProcessor imageProcessor;

    public UploadImageValidator(UploadImageProcessor imageProcessor) {
        this.imageProcessor = imageProcessor;
    }

    void validate(MultipartFile file) {
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
                if (".heic".equals(extension) || ".heif".equals(extension)) {
                    return;
                }
                throw new AppException(HttpStatus.BAD_REQUEST, "图片文件内容无效");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                imageProcessor.validateImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (AppException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图片文件内容无效", ex);
        }
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
            case ".webp" -> header.length >= 12
                    && UploadImageFormat.asciiEquals(header, 0, "RIFF")
                    && UploadImageFormat.asciiEquals(header, 8, "WEBP");
            case ".heic", ".heif" -> UploadImageFormat.isHeifFamily(header);
            default -> false;
        };
    }

    private byte[] readImageHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(UploadImageFormat.HEADER_BYTES);
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
}
