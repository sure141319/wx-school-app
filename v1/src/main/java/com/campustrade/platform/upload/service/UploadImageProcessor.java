package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

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
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Component
public class UploadImageProcessor {

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

    private final HeifImageDecoder heifImageDecoder;

    @Autowired
    public UploadImageProcessor(HeifImageDecoder heifImageDecoder) {
        this.heifImageDecoder = heifImageDecoder;
    }

    public UploadImageProcessor() {
        this(HeifImageDecoder.withDefaults());
    }

    record ProcessedVariants(byte[] master, byte[] thumbnail) {

        static ProcessedVariants empty() {
            return new ProcessedVariants(null, null);
        }
    }

    ProcessedVariants prepareVariants(InputStream inputStream, boolean includeCompressedMaster) throws IOException {
        BufferedImage source = readImageForVariants(inputStream);
        if (source == null) {
            return ProcessedVariants.empty();
        }

        byte[] master = includeCompressedMaster
                ? encodeRequiredWebp(resize(source, DISPLAY_MAX_SIZE), DISPLAY_QUALITY)
                : null;
        byte[] thumbnail = encodeRequiredWebp(resize(source, THUMBNAIL_MAX_SIZE), THUMBNAIL_QUALITY);
        return new ProcessedVariants(master, thumbnail);
    }

    byte[] optimizeAvatar(InputStream inputStream) throws IOException {
        BufferedImage source = readImageWithSubsampling(inputStream, AVATAR_DECODE_MAX_SIZE);
        if (source == null) {
            throw new AppException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "头像格式暂不支持，请选择 JPEG、PNG、HEIC、HEIF 或 WebP 图片"
            );
        }
        return encodeRequiredWebp(resize(source, AVATAR_MAX_SIZE), AVATAR_QUALITY);
    }

    BufferedImage readImageForThumbnail(InputStream inputStream) throws IOException {
        return readImageWithSubsampling(inputStream, THUMBNAIL_DECODE_MAX_SIZE);
    }

    BufferedImage readImageForVariants(InputStream inputStream) throws IOException {
        return readImageWithSubsampling(inputStream, VARIANT_DECODE_MAX_SIZE);
    }

    void validateImageDimensions(int width, int height) {
        UploadImageLimits.validateDimensions(width, height);
    }

    int calculateThumbnailSubsampling(int width, int height) {
        return calculateSubsampling(width, height, THUMBNAIL_DECODE_MAX_SIZE);
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

    private byte[] encodeRequiredWebp(BufferedImage image, float quality) throws IOException {
        byte[] encoded = encodeWebp(image, quality);
        if (encoded.length == 0) {
            throw new IOException("WebP encoder is unavailable");
        }
        return encoded;
    }

    private BufferedImage readImageWithSubsampling(InputStream inputStream, int decodeMaxSize) throws IOException {
        BufferedInputStream bufferedInput = new BufferedInputStream(inputStream);
        bufferedInput.mark(UploadImageFormat.HEADER_BYTES);
        byte[] header = bufferedInput.readNBytes(UploadImageFormat.HEADER_BYTES);
        bufferedInput.reset();

        if (UploadImageFormat.isHeifFamily(header)) {
            return heifImageDecoder.decode(bufferedInput, decodeMaxSize);
        }

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(bufferedInput)) {
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

    private int calculateSubsampling(int width, int height, int decodeMaxSize) {
        int largestDimension = Math.max(width, height);
        return Math.max(1, (int) Math.ceil((double) largestDimension / decodeMaxSize));
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
}
