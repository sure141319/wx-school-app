package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.auth.store.VerificationCodeStore;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class VerificationCodeService {

    private final VerificationCodeStore verificationCodeStore;
    private final AppProperties appProperties;

    public VerificationCodeService(VerificationCodeStore verificationCodeStore, AppProperties appProperties) {
        this.verificationCodeStore = verificationCodeStore;
        this.appProperties = appProperties;
    }

    public CodeReservation reserveCode(String email, VerificationPurposeEnum purpose, String code) {
        String storedValue = UUID.randomUUID() + ":" + code;
        VerificationCodeStore.IssueResult result;
        try {
            result = verificationCodeStore.tryIssue(
                    codeKey(email, purpose),
                    limitKey(email, purpose),
                    attemptKey(email, purpose),
                    storedValue,
                    codeTtl(),
                    Duration.ofHours(1),
                    cooldownThresholdSeconds(),
                    appProperties.getVerificationCode().getHourlyLimit()
            );
        } catch (RuntimeException ex) {
            throw temporarilyUnavailable(ex);
        }
        if (result == null) {
            throw temporarilyUnavailable(new IllegalStateException("Verification-code store returned no issue result"));
        }

        if (result == VerificationCodeStore.IssueResult.COOLDOWN) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "请求验证码过于频繁，请稍后再试");
        }
        if (result == VerificationCodeStore.IssueResult.HOURLY_LIMIT) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "验证码请求次数已超过每小时上限");
        }
        return new CodeReservation(
                codeKey(email, purpose),
                limitKey(email, purpose),
                attemptKey(email, purpose),
                storedValue
        );
    }

    public void rollbackReservation(CodeReservation reservation) {
        try {
            verificationCodeStore.rollbackIssue(
                    reservation.codeKey(),
                    reservation.limitKey(),
                    reservation.attemptKey(),
                    reservation.storedValue()
            );
        } catch (RuntimeException ex) {
            throw temporarilyUnavailable(ex);
        }
    }

    public void validateCode(String email, String inputCode, VerificationPurposeEnum purpose) {
        VerificationCodeStore.ValidationResult result;
        try {
            result = verificationCodeStore.validateAndConsume(
                    codeKey(email, purpose),
                    attemptKey(email, purpose),
                    inputCode,
                    maxAttempts(),
                    codeTtl()
            );
        } catch (RuntimeException ex) {
            throw temporarilyUnavailable(ex);
        }
        if (result == null) {
            throw temporarilyUnavailable(
                    new IllegalStateException("Verification-code store returned no validation result")
            );
        }

        if (result == VerificationCodeStore.ValidationResult.MISSING) {
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码不存在或已过期");
        }
        if (result == VerificationCodeStore.ValidationResult.INVALID) {
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码错误");
        }
        if (result == VerificationCodeStore.ValidationResult.TOO_MANY_ATTEMPTS) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "验证码错误次数过多，请重新获取");
        }
    }

    private Duration codeTtl() {
        return Duration.ofMinutes(appProperties.getVerificationCode().getExpireMinutes());
    }

    private long cooldownThresholdSeconds() {
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

    private AppException temporarilyUnavailable(RuntimeException ex) {
        return new AppException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "验证码服务暂时不可用",
                ex);
    }

    public record CodeReservation(String codeKey,
                                  String limitKey,
                                  String attemptKey,
                                  String storedValue) {
    }
}
