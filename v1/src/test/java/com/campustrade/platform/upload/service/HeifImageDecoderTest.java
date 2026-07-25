package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeifImageDecoderTest {

    @Test
    void decodesPngProducedByConverter() throws Exception {
        HeifImageDecoder decoder = decoderProducingImage(48, 24);

        BufferedImage decoded = decoder.decode(new ByteArrayInputStream(heifHeader("heic")));

        assertEquals(48, decoded.getWidth());
        assertEquals(24, decoded.getHeight());
    }

    @Test
    void createsWebpVariantsFromHeicAndHeifInputs() throws Exception {
        UploadImageProcessor processor = new UploadImageProcessor(decoderProducingImage(80, 40));

        for (String brand : new String[]{"heic", "mif1"}) {
            UploadImageProcessor.ProcessedVariants variants = processor.prepareVariants(
                    new ByteArrayInputStream(heifHeader(brand)),
                    true
            );

            assertTrue(variants.master().length > 12);
            assertTrue(variants.thumbnail().length > 12);
            assertEquals(
                    "WEBP",
                    new String(variants.master(), 8, 4, StandardCharsets.US_ASCII)
            );
        }
    }

    @Test
    void reportsInvalidHeifAsBadRequest() {
        HeifImageDecoder decoder = new HeifImageDecoder((input, output, log, timeout) -> {
            throw new HeifImageDecoder.HeifConversionException(
                    HeifImageDecoder.FailureReason.INVALID_IMAGE,
                    "invalid fixture"
            );
        });

        AppException exception = assertThrows(
                AppException.class,
                () -> decoder.decode(new ByteArrayInputStream(heifHeader("heic")))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void reportsMissingConverterAsServerConfigurationError() {
        HeifImageDecoder decoder = new HeifImageDecoder((input, output, log, timeout) -> {
            throw new HeifImageDecoder.HeifConversionException(
                    HeifImageDecoder.FailureReason.UNAVAILABLE,
                    "missing converter"
            );
        });

        AppException exception = assertThrows(
                AppException.class,
                () -> decoder.decode(new ByteArrayInputStream(heifHeader("heic")))
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
    }

    private HeifImageDecoder decoderProducingImage(int width, int height) {
        return new HeifImageDecoder((input, output, log, timeout) -> {
            try {
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                if (!ImageIO.write(image, "png", output.toFile())) {
                    throw new IOException("PNG writer unavailable");
                }
            } catch (IOException ex) {
                throw new HeifImageDecoder.HeifConversionException(
                        HeifImageDecoder.FailureReason.INVALID_IMAGE,
                        "Unable to create test conversion",
                        ex
                );
            }
        });
    }

    private byte[] heifHeader(String brand) {
        byte[] header = new byte[UploadImageFormat.HEADER_BYTES];
        byte[] fileType = "ftyp".getBytes(StandardCharsets.US_ASCII);
        byte[] brandBytes = brand.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(fileType, 0, header, 4, fileType.length);
        System.arraycopy(brandBytes, 0, header, 8, brandBytes.length);
        return header;
    }
}
