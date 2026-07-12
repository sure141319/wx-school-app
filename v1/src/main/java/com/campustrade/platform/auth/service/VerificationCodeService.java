package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.auth.store.VerificationCodeStore;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class VerificationCodeService {

    private final VerificationCodeStore verificationCodeStore;
    private final AppProperties appProperties;

    public VerificationCodeService(VerificationCodeStore verificationCodeStore, AppProperties appProperties) {
        this.verificationCodeStore = verificationCodeStore;
        this.appProperties = appProperties;
    }

    public void ensureCanSend(String email, VerificationPurposeEnum purpose) {
        checkCooldown(email, purpose);
        checkHourlyLimit(email, purpose);
    }

    public void saveCode(String email, VerificationPurposeEnum purpose, String code) {
        storeSet(codeKey(email, purpose), code, codeTtl());
        storeIncrement(limitKey(email, purpose), Duration.ofHours(1));
        storeDelete(attemptKey(email, purpose));
    }

    public void validateCode(String email, String inputCode, VerificationPurposeEnum purpose) {
        String key = codeKey(email, purpose);
        String code = storeGet(key);
        if (code == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码不存在或已过期");
        }
        if (currentAttemptCount(email, purpose) >= maxAttempts()) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "验证码错误次数过多，请重新获取");
        }
        if (!code.equals(inputCode)) {
            long attempts = storeIncrement(attemptKey(email, purpose), codeTtl());
            if (attempts >= maxAttempts()) {
                storeDelete(key);
                throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "验证码错误次数过多，请重新获取");
            }
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码错误");
        }
        storeDelete(key);
        storeDelete(attemptKey(email, purpose));
    }

    private void checkCooldown(String email, VerificationPurposeEnum purpose) {
        Long ttl = storeGetExpire(codeKey(email, purpose));
        if (ttl != null && ttl > cooldownSeconds()) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "请求验证码过于频繁，请稍后再试");
        }
    }

    private void checkHourlyLimit(String email, VerificationPurposeEnum purpose) {
        String value = storeGet(limitKey(email, purpose));
        int count;
        if (value == null) {
            count = 0;
        } else {
            try {
                count = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                storeDelete(limitKey(email, purpose));
                count = 0;
            }
        }
        if (count >= appProperties.getVerificationCode().getHourlyLimit()) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "验证码请求次数已超过每小时上限");
        }
    }

    private int currentAttemptCount(String email, VerificationPurposeEnum purpose) {
        String value = storeGet(attemptKey(email, purpose));
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            storeDelete(attemptKey(email, purpose));
            return 0;
        }
    }

    private Duration codeTtl() {
        return Duration.ofMinutes(appProperties.getVerificationCode().getExpireMinutes());
    }

    private long cooldownSeconds() {
        long ttl = codeTtl().getSeconds();
        long cooldown = appProperties.getVerificationCode().getResendCooldownSeconds();
        return Math.max(ttl - cooldown, 0);
    }

    private int maxAttempts() {
        return appProperties.getVerificationCode().getMaxAttempts();
    }

    private String codeKey(String email, VerificationPurposeEnum purpose) {
        return appProperties.getVerificationCode().getKeyPrefix() + purpose.name() + ":" + email;
    }

    private String limitKey(String email, VerificationPurposeEnum purpose) {
        return appProperties.getVerificationCode().getLimitPrefix() + purpose.name() + ":" + email;
    }

    private String attemptKey(String email, VerificationPurposeEnum purpose) {
        return appProperties.getVerificationCode().getAttemptPrefix() + purpose.name() + ":" + email;
    }

    private String storeGet(String key) {
        try {
            return verificationCodeStore.get(key);
        } catch (RuntimeException ex) {
            throw temporarilyUnavailable(ex);
        }
    }

    private void storeSet(String key, String value, Duration ttl) {
        try {
            verificationCodeStore.set(key, value, ttl);
        } catch (RuntimeException ex) {
            throw temporarilyUnavailable(ex);
        }
    }

    private void storeDelete(String key) {
        try {
            verificationCodeStore.delete(key);
        } catch (RuntimeException ex) {
            throw temporarilyUnavailable(ex);
        }
    }

    private Long storeGetExpire(String key) {
        try {
            return verificationCodeStore.getExpire(key);
        } catch (RuntimeException ex) {
            throw temporarilyUnavailable(ex);
        }
    }

    private long storeIncrement(String key, Duration ttlOnCreate) {
        try {
            Long count = verificationCodeStore.increment(key, ttlOnCreate);
            return count == null ? 1L : count;
        } catch (RuntimeException ex) {
            throw temporarilyUnavailable(ex);
        }
    }

    private AppException temporarilyUnavailable(RuntimeException ex) {
        return new AppException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "验证码服务暂时不可用: " + rootCauseMessage(ex),
                ex);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
