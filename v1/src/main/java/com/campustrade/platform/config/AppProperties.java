package com.campustrade.platform.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank
    private String jwtSecret;

    @Min(1)
    private int jwtExpirationMinutes = 1440;

    private String apiBaseUrl = "";

    private VerificationCode verificationCode = new VerificationCode();
    private Auth auth = new Auth();
    private Mail mail = new Mail();
    private Minio minio = new Minio();
    private Redis redis = new Redis();
    private Cache cache = new Cache();
    private Cors cors = new Cors();
    private ImageAudit imageAudit = new ImageAudit();

    @Getter
    @Setter
    public static class VerificationCode {
        @Min(1)
        private int expireMinutes = 5;
        @Min(0)
        private int resendCooldownSeconds = 60;
        @Min(1)
        private int hourlyLimit = 8;
        @NotBlank
        private String keyPrefix = "auth:code:";
        @NotBlank
        private String limitPrefix = "auth:code:limit:";
    }

    @Getter
    @Setter
    public static class Auth {
        @Min(1)
        private int maxLoginFailures = 5;
        @Min(1)
        private int lockMinutes = 15;
    }

    @Getter
    @Setter
    public static class Mail {
        @NotBlank
        private String host = "smtp.qq.com";
        @Min(1)
        private int port = 465;
        private String username = "";
        private String password = "";
        @NotBlank
        private String from = "no-reply@campus-trade.local";
        private boolean sslEnabled = true;
        private boolean auth = true;
        private boolean starttlsEnabled = false;
        private boolean debug = false;
    }

    @Getter
    @Setter
    public static class Minio {
        @NotBlank
        private String endpoint;
        @NotBlank
        private String accessKey;
        @NotBlank
        private String secretKey;
        @NotBlank
        private String bucket;
        private String publicBaseUrl = "";
        private boolean secure = false;
        private boolean autoCreateBucket = true;
        @Min(1)
        private int presignExpiryDays = 7;
    }

    @Getter
    @Setter
    public static class Redis {
        private boolean required = true;
    }

    @Getter
    @Setter
    public static class Cache {
        @Min(1)
        private int categoryTtlMinutes = 30;
        @Min(1)
        private int goodsListTtlMinutes = 2;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Getter
    @Setter
    public static class ImageAudit {
        private String pendingPlaceholderUrl = "";
        private List<Long> reviewerUserIds = List.of();
    }
}
