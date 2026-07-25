package com.campustrade.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPropertiesBindingTest {

    @Test
    void emptyReviewerConfigurationBindsToNoReviewers() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.image-audit.reviewer-user-ids", "");

        AppProperties properties = Binder.get(environment)
                .bind("app", Bindable.of(AppProperties.class))
                .orElseGet(AppProperties::new);

        assertTrue(properties.getImageAudit().getReviewerUserIds().isEmpty());
    }

    @Test
    void sharedJavaDefaultsMatchRuntimeYamlFallbacks() throws Exception {
        AppProperties properties = new AppProperties();
        PropertySourcesPropertyResolver runtimeDefaults = loadRuntimeDefaults();

        Map<String, Object> sharedDefaults = Map.ofEntries(
                Map.entry("app.jwt-expiration-minutes", properties.getJwtExpirationMinutes()),
                Map.entry("app.redis.required", properties.getRedis().isRequired()),
                Map.entry("app.cache.category-ttl-minutes", properties.getCache().getCategoryTtlMinutes()),
                Map.entry("app.cache.goods-list-ttl-minutes", properties.getCache().getGoodsListTtlMinutes()),
                Map.entry("app.verification-code.expire-minutes", properties.getVerificationCode().getExpireMinutes()),
                Map.entry("app.verification-code.resend-cooldown-seconds", properties.getVerificationCode().getResendCooldownSeconds()),
                Map.entry("app.verification-code.hourly-limit", properties.getVerificationCode().getHourlyLimit()),
                Map.entry("app.verification-code.max-attempts", properties.getVerificationCode().getMaxAttempts()),
                Map.entry("app.verification-code.key-prefix", properties.getVerificationCode().getKeyPrefix()),
                Map.entry("app.verification-code.limit-prefix", properties.getVerificationCode().getLimitPrefix()),
                Map.entry("app.verification-code.attempt-prefix", properties.getVerificationCode().getAttemptPrefix()),
                Map.entry("app.auth.max-login-failures", properties.getAuth().getMaxLoginFailures()),
                Map.entry("app.auth.lock-minutes", properties.getAuth().getLockMinutes()),
                Map.entry("app.contact-email.cooldown-hours", properties.getContactEmail().getCooldownHours()),
                Map.entry("app.contact-email.hourly-limit", properties.getContactEmail().getHourlyLimit()),
                Map.entry("app.contact-email.hourly-window-minutes", properties.getContactEmail().getHourlyWindowMinutes()),
                Map.entry("app.contact-email.key-prefix", properties.getContactEmail().getKeyPrefix()),
                Map.entry("app.mail.host", properties.getMail().getHost()),
                Map.entry("app.mail.port", properties.getMail().getPort()),
                Map.entry("app.mail.ssl-enabled", properties.getMail().isSslEnabled()),
                Map.entry("app.mail.auth", properties.getMail().isAuth()),
                Map.entry("app.mail.starttls-enabled", properties.getMail().isStarttlsEnabled()),
                Map.entry("app.mail.debug", properties.getMail().isDebug()),
                Map.entry("app.minio.secure", properties.getMinio().isSecure()),
                Map.entry("app.minio.auto-create-bucket", properties.getMinio().isAutoCreateBucket()),
                Map.entry("app.upload.max-files-per-user", properties.getUpload().getMaxFilesPerUser()),
                Map.entry("app.upload.max-bytes-per-user", properties.getUpload().getMaxBytesPerUser()),
                Map.entry("app.upload.max-staged-files-per-user", properties.getUpload().getMaxStagedFilesPerUser()),
                Map.entry("app.upload.max-staged-bytes-per-user", properties.getUpload().getMaxStagedBytesPerUser()),
                Map.entry("app.upload.staged-ttl-hours", properties.getUpload().getStagedTtlHours()),
                Map.entry("app.upload.cleanup-interval-ms", properties.getUpload().getCleanupIntervalMs()),
                Map.entry("app.upload.cleanup-batch-size", properties.getUpload().getCleanupBatchSize()),
                Map.entry("app.monitoring.goods-list-slow-threshold-ms", properties.getMonitoring().getGoodsListSlowThresholdMs()),
                Map.entry("app.wechat.code2-session-url", properties.getWechat().getCode2SessionUrl()),
                Map.entry("app.wechat.connect-timeout-ms", properties.getWechat().getConnectTimeoutMs()),
                Map.entry("app.wechat.read-timeout-ms", properties.getWechat().getReadTimeoutMs())
        );

        sharedDefaults.forEach((key, expected) ->
                assertEquals(String.valueOf(expected), runtimeDefaults.getRequiredProperty(key), key));
    }

    private PropertySourcesPropertyResolver loadRuntimeDefaults() throws Exception {
        Path mainClassesDirectory = Path.of(
                AppProperties.class.getProtectionDomain().getCodeSource().getLocation().toURI()
        );
        Resource applicationYaml = new FileSystemResource(mainClassesDirectory.resolve("application.yml"));
        assertTrue(applicationYaml.exists(), "main application.yml must be available in the classes directory");

        MutablePropertySources propertySources = new MutablePropertySources();
        new YamlPropertySourceLoader()
                .load("runtime-application", applicationYaml)
                .forEach(propertySources::addLast);
        return new PropertySourcesPropertyResolver(propertySources);
    }
}
