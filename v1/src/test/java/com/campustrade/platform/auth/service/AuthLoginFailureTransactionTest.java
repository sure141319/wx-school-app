package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.dto.request.LoginRequestDTO;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class AuthLoginFailureTransactionTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JavaMailSender javaMailSender;

    private String email;

    @BeforeEach
    void insertUser() {
        email = "login-lock-" + UUID.randomUUID().toString().replace("-", "") + "@qq.com";
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, nickname, failed_login_count, created_at, updated_at)
                VALUES (?, ?, ?, 0, NOW(), NOW())
                """,
                email,
                passwordEncoder.encode("correct-password"),
                "登录锁定测试用户"
        );
    }

    @AfterEach
    void deleteUser() {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
    }

    @Test
    void failedLoginStateCommitsEvenThoughAuthenticationExceptionRollsBack() {
        int maxFailures = appProperties.getAuth().getMaxLoginFailures();
        LoginRequestDTO request = new LoginRequestDTO(email, "wrong-password");

        for (int attempt = 0; attempt < maxFailures; attempt++) {
            AppException exception = assertThrows(AppException.class, () -> authService.login(request));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        }

        Integer failedLoginCount = jdbcTemplate.queryForObject(
                "SELECT failed_login_count FROM users WHERE email = ?",
                Integer.class,
                email
        );
        LocalDateTime lockedUntil = jdbcTemplate.queryForObject(
                "SELECT locked_until FROM users WHERE email = ?",
                LocalDateTime.class,
                email
        );
        assertEquals(0, failedLoginCount);
        assertNotNull(lockedUntil);

        AppException lockedException = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(HttpStatus.LOCKED, lockedException.getStatus());
    }
}
