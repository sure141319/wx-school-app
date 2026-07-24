package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.auth.store.VerificationCodeStore;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static com.campustrade.platform.auth.store.VerificationCodeStore.IssueResult.COOLDOWN;
import static com.campustrade.platform.auth.store.VerificationCodeStore.IssueResult.ISSUED;
import static com.campustrade.platform.auth.store.VerificationCodeStore.ValidationResult.TOO_MANY_ATTEMPTS;
import static com.campustrade.platform.auth.store.VerificationCodeStore.ValidationResult.VALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeServiceTest {

    private static final String EMAIL = "student@qq.com";
    private static final VerificationPurposeEnum PURPOSE = VerificationPurposeEnum.REGISTER;
    private static final String CODE_KEY = "auth:code:REGISTER:student@qq.com";
    private static final String LIMIT_KEY = "auth:code:limit:REGISTER:student@qq.com";
    private static final String ATTEMPT_KEY = "auth:code:attempt:REGISTER:student@qq.com";

    private final VerificationCodeStore store = mock(VerificationCodeStore.class);
    private final AppProperties appProperties = new AppProperties();
    private final VerificationCodeService service = new VerificationCodeService(store, appProperties);

    @Test
    void reserveCodePreservesTooManyRequestsForCooldown() {
        when(store.tryIssue(
                eq(CODE_KEY), eq(LIMIT_KEY), eq(ATTEMPT_KEY), any(), any(), any(), any(Long.class), any(Integer.class)
        )).thenReturn(COOLDOWN);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.reserveCode(EMAIL, PURPOSE, "123456")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    @Test
    void reserveCodeWrapsStoreFailureAsServiceUnavailableWithoutLeakingCause() {
        when(store.tryIssue(
                eq(CODE_KEY), eq(LIMIT_KEY), eq(ATTEMPT_KEY), any(), any(), any(), any(Long.class), any(Integer.class)
        )).thenThrow(new IllegalStateException("redis down"));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.reserveCode(EMAIL, PURPOSE, "123456")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals("验证码服务暂时不可用", exception.getMessage());
    }

    @Test
    void validateCodeMapsTooManyAttempts() {
        appProperties.getVerificationCode().setMaxAttempts(3);
        when(store.validateAndConsume(eq(CODE_KEY), eq(ATTEMPT_KEY), eq("000000"), eq(3), any()))
                .thenReturn(TOO_MANY_ATTEMPTS);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.validateCode(EMAIL, "000000", PURPOSE)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    @Test
    void validateCodeAcceptsAtomicSuccess() {
        when(store.validateAndConsume(eq(CODE_KEY), eq(ATTEMPT_KEY), eq("123456"), any(Integer.class), any()))
                .thenReturn(VALID);

        assertDoesNotThrow(() -> service.validateCode(EMAIL, "123456", PURPOSE));
    }

    @Test
    void rollbackReservationUsesExactStoredValue() {
        when(store.tryIssue(
                eq(CODE_KEY), eq(LIMIT_KEY), eq(ATTEMPT_KEY), any(), any(), any(), any(Long.class), any(Integer.class)
        )).thenReturn(ISSUED);
        VerificationCodeService.CodeReservation reservation =
                service.reserveCode(EMAIL, PURPOSE, "123456");

        service.rollbackReservation(reservation);

        verify(store).rollbackIssue(CODE_KEY, LIMIT_KEY, ATTEMPT_KEY, reservation.storedValue());
    }
}
