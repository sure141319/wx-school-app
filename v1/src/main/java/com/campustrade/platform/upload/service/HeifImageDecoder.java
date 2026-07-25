package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class HeifImageDecoder {

    private static final Logger log = LoggerFactory.getLogger(HeifImageDecoder.class);
    private static final String DEFAULT_CONVERTER_COMMAND = "heif-convert";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_MAX_CONCURRENT_CONVERSIONS = 1;
    private static final long DEFAULT_MAX_DECODED_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_INPUT_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_LOG_BYTES = 8 * 1024;

    private final String converterCommand;
    private final Duration timeout;
    private final long maxDecodedBytes;
    private final Semaphore conversionSlots;
    private final ConversionCommand conversionCommand;

    @Autowired
    public HeifImageDecoder(AppProperties appProperties) {
        this(
                appProperties.getUpload().getHeifConverterCommand(),
                Duration.ofSeconds(appProperties.getUpload().getHeifConverterTimeoutSeconds()),
                appProperties.getUpload().getMaxHeifDecodedBytes(),
                appProperties.getUpload().getMaxConcurrentHeifConversions(),
                null
        );
    }

    HeifImageDecoder(ConversionCommand conversionCommand) {
        this(
                DEFAULT_CONVERTER_COMMAND,
                Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
                DEFAULT_MAX_DECODED_BYTES,
                DEFAULT_MAX_CONCURRENT_CONVERSIONS,
                conversionCommand
        );
    }

    static HeifImageDecoder withDefaults() {
        return new HeifImageDecoder(
                DEFAULT_CONVERTER_COMMAND,
                Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
                DEFAULT_MAX_DECODED_BYTES,
                DEFAULT_MAX_CONCURRENT_CONVERSIONS,
                null
        );
    }

    private HeifImageDecoder(String converterCommand,
                             Duration timeout,
                             long maxDecodedBytes,
                             int maxConcurrentConversions,
                             ConversionCommand conversionCommand) {
        this.converterCommand = converterCommand;
        this.timeout = timeout;
        this.maxDecodedBytes = maxDecodedBytes;
        this.conversionSlots = new Semaphore(maxConcurrentConversions, true);
        this.conversionCommand = conversionCommand == null ? this::runConverter : conversionCommand;
    }

    BufferedImage decode(InputStream inputStream) throws IOException {
        return decode(inputStream, Integer.MAX_VALUE);
    }

    BufferedImage decode(InputStream inputStream, int decodeMaxSize) throws IOException {
        boolean acquired = false;
        Path temporaryDirectory = null;
        try {
            acquired = conversionSlots.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new AppException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "HEIC/HEIF 图片处理繁忙，请稍后重试"
                );
            }

            temporaryDirectory = Files.createTempDirectory("campus-heif-");
            Path inputFile = temporaryDirectory.resolve("source.heif");
            Path outputFile = temporaryDirectory.resolve("decoded.png");
            Path logFile = temporaryDirectory.resolve("converter.log");
            copyInput(inputStream, inputFile);

            try {
                conversionCommand.convert(inputFile, outputFile, logFile, timeout);
            } catch (HeifConversionException ex) {
                throw mapConversionFailure(ex);
            }

            Path decodedFile = findDecodedFile(temporaryDirectory, outputFile);
            long decodedBytes = Files.size(decodedFile);
            if (decodedBytes <= 0) {
                throw invalidImage("HEIC/HEIF 解码结果为空", null);
            }
            if (decodedBytes > maxDecodedBytes) {
                throw new AppException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "HEIC/HEIF 图片解码后过大"
                );
            }

            return readDecodedImage(decodedFile, decodeMaxSize);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "HEIC/HEIF 图片处理被中断，请重试",
                    ex
            );
        } finally {
            if (acquired) {
                conversionSlots.release();
            }
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    private BufferedImage readDecodedImage(Path decodedFile, int decodeMaxSize) throws IOException {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(decodedFile.toFile())) {
            if (imageInput == null) {
                throw invalidImage("HEIC/HEIF 解码结果不是有效图片", null);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage("HEIC/HEIF 解码结果不是有效图片", null);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                UploadImageLimits.validateDimensions(width, height);

                ImageReadParam readParam = reader.getDefaultReadParam();
                int largestDimension = Math.max(width, height);
                int subsampling = decodeMaxSize == Integer.MAX_VALUE
                        ? 1
                        : Math.max(1, (int) Math.ceil((double) largestDimension / decodeMaxSize));
                readParam.setSourceSubsampling(subsampling, subsampling, 0, 0);
                return reader.read(0, readParam);
            } finally {
                reader.dispose();
            }
        } catch (AppException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidImage("HEIC/HEIF 解码结果读取失败", ex);
        }
    }

    private void copyInput(InputStream inputStream, Path inputFile) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(inputFile)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_INPUT_BYTES) {
                    throw new AppException(
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            "HEIC/HEIF 图片文件不能超过 10MB"
                    );
                }
                outputStream.write(buffer, 0, read);
            }
        }
    }

    private void runConverter(Path inputFile,
                              Path outputFile,
                              Path logFile,
                              Duration conversionTimeout) throws HeifConversionException {
        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    List.of(converterCommand, inputFile.toString(), outputFile.toString())
            );
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(logFile.toFile());
            process = processBuilder.start();
        } catch (IOException ex) {
            throw new HeifConversionException(
                    FailureReason.UNAVAILABLE,
                    "Unable to start " + converterCommand,
                    ex
            );
        }

        try {
            boolean finished = process.waitFor(conversionTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                throw new HeifConversionException(
                        FailureReason.TIMEOUT,
                        "HEIC/HEIF conversion timed out"
                );
            }
            if (process.exitValue() != 0) {
                String converterOutput = readConverterOutput(logFile);
                throw new HeifConversionException(
                        FailureReason.INVALID_IMAGE,
                        "heif-convert exited with " + process.exitValue() + ": " + converterOutput
                );
            }
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new HeifConversionException(
                    FailureReason.INTERRUPTED,
                    "HEIC/HEIF conversion was interrupted",
                    ex
            );
        }
    }

    private Path findDecodedFile(Path temporaryDirectory, Path requestedOutput) {
        if (Files.isRegularFile(requestedOutput)) {
            return requestedOutput;
        }
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        return filename.startsWith("decoded") && filename.endsWith(".png");
                    })
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> invalidImage("HEIC/HEIF 解码器未生成图片", null));
        } catch (IOException ex) {
            throw invalidImage("HEIC/HEIF 解码结果读取失败", ex);
        }
    }

    private AppException mapConversionFailure(HeifConversionException exception) {
        return switch (exception.reason()) {
            case UNAVAILABLE -> new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "服务器未安装或未正确配置 HEIC/HEIF 解码器",
                    exception
            );
            case TIMEOUT -> new AppException(
                    HttpStatus.REQUEST_TIMEOUT,
                    "HEIC/HEIF 图片处理超时，请压缩后重试",
                    exception
            );
            case INTERRUPTED -> new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "HEIC/HEIF 图片处理被中断，请重试",
                    exception
            );
            case INVALID_IMAGE -> invalidImage("HEIC/HEIF 图片内容无效或编码不受支持", exception);
        };
    }

    private AppException invalidImage(String message, Throwable cause) {
        return new AppException(HttpStatus.BAD_REQUEST, message, cause);
    }

    private String readConverterOutput(Path logFile) {
        if (!Files.isRegularFile(logFile)) {
            return "";
        }
        try (InputStream inputStream = Files.newInputStream(logFile)) {
            return new String(inputStream.readNBytes(MAX_LOG_BYTES), StandardCharsets.UTF_8)
                    .replace('\r', ' ')
                    .replace('\n', ' ')
                    .trim();
        } catch (IOException ex) {
            return "";
        }
    }

    private void deleteTemporaryDirectory(Path temporaryDirectory) {
        if (temporaryDirectory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("Failed to delete temporary HEIF file: {}", path, ex);
                }
            });
        } catch (IOException ex) {
            log.warn("Failed to clean temporary HEIF directory: {}", temporaryDirectory, ex);
        }
    }

    @FunctionalInterface
    interface ConversionCommand {
        void convert(Path inputFile,
                     Path outputFile,
                     Path logFile,
                     Duration timeout) throws HeifConversionException;
    }

    enum FailureReason {
        UNAVAILABLE,
        TIMEOUT,
        INTERRUPTED,
        INVALID_IMAGE
    }

    static final class HeifConversionException extends IOException {

        private final FailureReason reason;

        HeifConversionException(FailureReason reason, String message) {
            super(message);
            this.reason = reason;
        }

        HeifConversionException(FailureReason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        FailureReason reason() {
            return reason;
        }
    }
}
