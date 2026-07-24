package com.campustrade.platform.auth.store;

import java.time.Duration;

public interface VerificationCodeStore {

    IssueResult tryIssue(String codeKey,
                         String limitKey,
                         String attemptKey,
                         String storedValue,
                         Duration codeTtl,
                         Duration limitWindow,
                         long cooldownThresholdSeconds,
                         int hourlyLimit);

    boolean rollbackIssue(String codeKey,
                          String limitKey,
                          String attemptKey,
                          String expectedStoredValue);

    ValidationResult validateAndConsume(String codeKey,
                                        String attemptKey,
                                        String inputCode,
                                        int maxAttempts,
                                        Duration attemptTtl);

    enum IssueResult {
        ISSUED,
        COOLDOWN,
        HOURLY_LIMIT
    }

    enum ValidationResult {
        VALID,
        MISSING,
        INVALID,
        TOO_MANY_ATTEMPTS
    }
}
