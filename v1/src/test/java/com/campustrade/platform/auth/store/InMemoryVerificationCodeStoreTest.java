package com.campustrade.platform.auth.store;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryVerificationCodeStoreTest {

    private static final String CODE_KEY = "code";
    private static final String LIMIT_KEY = "limit";
    private static final String ATTEMPT_KEY = "attempt";

    @Test
    void concurrentIssueAllowsOnlyOneReservationDuringCooldown() throws Exception {
        InMemoryVerificationCodeStore store = new InMemoryVerificationCodeStore();

        List<VerificationCodeStore.IssueResult> results = runConcurrently(
                8,
                () -> store.tryIssue(
                        CODE_KEY,
                        LIMIT_KEY,
                        ATTEMPT_KEY,
                        Thread.currentThread().getName() + ":123456",
                        Duration.ofMinutes(5),
                        Duration.ofHours(1),
                        240,
                        10
                )
        );

        assertEquals(1, results.stream().filter(result -> result == VerificationCodeStore.IssueResult.ISSUED).count());
        assertEquals(7, results.stream().filter(result -> result == VerificationCodeStore.IssueResult.COOLDOWN).count());
    }

    @Test
    void concurrentValidationConsumesCodeOnlyOnce() throws Exception {
        InMemoryVerificationCodeStore store = new InMemoryVerificationCodeStore();
        store.tryIssue(
                CODE_KEY,
                LIMIT_KEY,
                ATTEMPT_KEY,
                "reservation:123456",
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                240,
                10
        );

        List<VerificationCodeStore.ValidationResult> results = runConcurrently(
                8,
                () -> store.validateAndConsume(
                        CODE_KEY,
                        ATTEMPT_KEY,
                        "123456",
                        5,
                        Duration.ofMinutes(5)
                )
        );

        assertEquals(1, results.stream().filter(result -> result == VerificationCodeStore.ValidationResult.VALID).count());
        assertEquals(7, results.stream().filter(result -> result == VerificationCodeStore.ValidationResult.MISSING).count());
    }

    private <T> List<T> runConcurrently(int taskCount, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
