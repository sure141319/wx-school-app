package com.campustrade.platform.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(AppProperties appProperties) {
        AppProperties.Minio minio = appProperties.getMinio();
        MinioClient.Builder builder = MinioClient.builder()
                .credentials(minio.getAccessKey(), minio.getSecretKey());

        String endpoint = minio.getEndpoint().trim();
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            builder.endpoint(endpoint);
        } else {
            String[] hostAndPort = endpoint.split(":", 2);
            String host = hostAndPort[0];
            int port = hostAndPort.length > 1
                    ? Integer.parseInt(hostAndPort[1])
                    : (minio.isSecure() ? 443 : 80);
            builder.endpoint(host, port, minio.isSecure());
        }

        return builder.build();
    }
}
