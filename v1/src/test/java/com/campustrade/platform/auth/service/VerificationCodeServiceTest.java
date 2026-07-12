package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.auth.store.VerificationCodeStore;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeServiceTest {

    private static final String EMAIL = "student@qq.com";
    private static final VerificationPurposeEnum PURPOSE = VerificationPurposeEnum.REGISTER;
    private static final String CODE_KEY = "auth:code:REGISTER:student@qq.com";
    private static final String ATTEMPT_KEY = "auth:code:attempt:REGISTER:student@qq.com";

    private final VerificationCodeStore store = mock(VerificationCodeStore.class);
    private final AppProperties appProperties = new AppProperties();
    private final VerificationCodeService service = new VerificationCodeService(store, appProperties);

    @Test
    void ensureCanSendPreservesTooManyRequestsForCooldown() {
        when(store.getExpire(CODE_KEY)).thenReturn(250L);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.ensureCanSend(EMAIL, PURPOSE)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    @Test
    void saveCodeWrapsStoreFailureAsServiceUnavailable() {
        when(store.getExpire(CODE_KEY)).thenThrow(new IllegalStateException("redis down"));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.ensureCanSend(EMAIL, PURPOSE)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    void validateCodeCountsWrongAttemptsAndDeletesCodeAtLimit() {
        appProperties.getVerificationCode().setMaxAttempts(3);
        when(store.get(CODE_KEY)).thenReturn("123456");
        when(store.get(ATTEMPT_KEY)).thenReturn("2");
        when(store.increment(eq(ATTEMPT_KEY), any(Duration.class))).thenReturn(3L);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.validateCode(EMAIL, "000000", PURPOSE)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        verify(store).delete(CODE_KEY);
    }

    @Test
    void validateCodeRejectsWhenAttemptsAlreadyExceeded() {
        appProperties.getVerificationCode().setMaxAttempts(3);
        when(store.get(CODE_KEY)).thenReturn("123456");
        when(store.get(ATTEMPT_KEY)).thenReturn("3");

        AppException exception = assertThrows(
                AppException.class,
                () -> service.validateCode(EMAIL, "123456", PURPOSE)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        verify(store, never()).increment(eq(ATTEMPT_KEY), any(Duration.class));
    }

    @Test
    void validateCodeDeletesCodeAndAttemptsOnSuccess() {
        when(store.get(CODE_KEY)).thenReturn("123456");
        when(store.get(ATTEMPT_KEY)).thenReturn("2");

        service.validateCode(EMAIL, "123456", PURPOSE);

        verify(store).delete(CODE_KEY);
        verify(store).delete(ATTEMPT_KEY);
    }
}
