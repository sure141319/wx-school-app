package com.campustrade.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCompressionConfigTest {

    @Test
    void enablesCompressionOnlyAboveTwoKilobytesForJsonAndText() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new FileSystemResource("src/main/resources/application.yml"));
        PropertySource<?> properties = sources.get(0);

        assertEquals(Boolean.TRUE, properties.getProperty("server.compression.enabled"));
        assertEquals("2KB", properties.getProperty("server.compression.min-response-size"));
        String mimeTypes = String.valueOf(properties.getProperty("server.compression.mime-types"));
        assertTrue(mimeTypes.contains("application/json"));
        assertTrue(mimeTypes.contains("text/plain"));
        assertTrue(!mimeTypes.contains("image/"));
    }
}
