package com.campustrade.platform.upload.service;

import com.campustrade.platform.config.AppProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HeifImageDecoderIntegrationTest {

    @Test
    void decodesRealHeicAndHeifFixturesWhenLibheifIsInstalled() throws Exception {
        String converterCommand = System.getProperty("heif.converter.command", "heif-convert");
        Assumptions.assumeTrue(
                isHeifConverterAvailable(converterCommand),
                converterCommand + " is not installed"
        );
        AppProperties properties = new AppProperties();
        properties.getUpload().setHeifConverterCommand(converterCommand);
        HeifImageDecoder decoder = new HeifImageDecoder(properties);

        for (String resource : new String[]{"/images/heic-32.heic", "/images/heif-32.heif"}) {
            try (InputStream inputStream = getClass().getResourceAsStream(resource)) {
                assertTrue(inputStream != null, "Missing fixture: " + resource);
                BufferedImage decoded = decoder.decode(inputStream);
                assertTrue(decoded.getWidth() > 0);
                assertTrue(decoded.getHeight() > 0);
            }
        }
    }

    private boolean isHeifConverterAvailable(String converterCommand) {
        try {
            Process process = new ProcessBuilder(converterCommand, "--version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
