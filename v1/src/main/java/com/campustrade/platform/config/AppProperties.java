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
    private ContactEmail contactEmail = new ContactEmail();
    private Mail mail = new Mail();
    private Minio minio = new Minio();
    private Upload upload = new Upload();
    private Redis redis = new Redis();
    private Cache cache = new Cache();
    private Cors cors = new Cors();
    private ImageAudit imageAudit = new ImageAudit();
    private Monitoring monitoring = new Monitoring();
    private Wechat wechat = new Wechat();

    @Getter
    @Setter
    public static class VerificationCode {
        @Min(1)
        private int expireMinutes = 5;
        @Min(0)
        private int resendCooldownSeconds = 60;
        @Min(1)
        private int hourlyLimit = 8;
        @Min(1)
        private int maxAttempts = 5;
        @NotBlank
        private String keyPrefix = "auth:code:";
        @NotBlank
        private String limitPrefix = "auth:code:limit:";
        @NotBlank
        private String attemptPrefix = "auth:code:attempt:";
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
    public static class ContactEmail {
        @Min(1)
        private int cooldownHours = 24;
        @Min(1)
        private int hourlyLimit = 6;
        @Min(1)
        private int hourlyWindowMinutes = 60;
        @NotBlank
        private String keyPrefix = "contact:email:";
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
    public static class Upload {
        @Min(1)
        private int maxFilesPerUser = 200;
        @Min(1)
        private long maxBytesPerUser = 1024L * 1024L * 1024L;
        @Min(1)
        private int maxStagedFilesPerUser = 20;
        @Min(1)
        private long maxStagedBytesPerUser = 200L * 1024L * 1024L;
        @Min(1)
        private int stagedTtlHours = 24;
        @Min(1)
        private long cleanupIntervalMs = 60L * 60L * 1000L;
        @Min(1)
        private int cleanupBatchSize = 100;
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

    @Getter
    @Setter
    public static class Monitoring {
        @Min(1)
        private long goodsListSlowThresholdMs = 300;
    }

    @Getter
    @Setter
    public static class Wechat {
        private String appId = "";
        private String appSecret = "";
        @NotBlank
        private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    }
}
