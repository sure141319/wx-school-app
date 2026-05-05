package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.assembler.AuthAssembler;
import com.campustrade.platform.auth.dto.request.LoginRequestDTO;
import com.campustrade.platform.auth.dto.request.RegisterRequestDTO;
import com.campustrade.platform.auth.dto.request.ResetPasswordRequestDTO;
import com.campustrade.platform.auth.dto.request.SendCodeRequestDTO;
import com.campustrade.platform.auth.dto.response.AuthResponseDTO;
import com.campustrade.platform.auth.dto.response.SendCodeResponseDTO;
import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.auth.store.VerificationCodeStore;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.security.JwtTokenProvider;
import com.campustrade.platform.security.UserPrincipal;
import com.campustrade.platform.user.assembler.UserProfileAssembler;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;
import com.campustrade.platform.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final MailService mailService;
    private final AppProperties appProperties;
    private final AuthAssembler authAssembler;
    private final UserProfileAssembler userProfileAssembler;
    private final VerificationCodeStore verificationCodeStore;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       MailService mailService,
                       AppProperties appProperties,
                       AuthAssembler authAssembler,
                       UserProfileAssembler userProfileAssembler,
                       VerificationCodeStore verificationCodeStore) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.mailService = mailService;
        this.appProperties = appProperties;
        this.authAssembler = authAssembler;
        this.userProfileAssembler = userProfileAssembler;
        this.verificationCodeStore = verificationCodeStore;
    }

    @Transactional
    public SendCodeResponseDTO sendVerificationCode(SendCodeRequestDTO request) {
        VerificationPurposeEnum purpose = request.purpose() == null ? VerificationPurposeEnum.REGISTER : request.purpose();
        String email = normalizeEmail(request.email());

        if (purpose == VerificationPurposeEnum.REGISTER && existsByEmail(email)) {
            throw new AppException(HttpStatus.CONFLICT, "邮箱已注册");
        }
        if (purpose == VerificationPurposeEnum.RESET_PASSWORD && !existsByEmail(email)) {
            throw new AppException(HttpStatus.NOT_FOUND, "邮箱未注册");
        }

        try {
            checkCooldown(email, purpose);
            checkHourlyLimit(email, purpose);
        } catch (RuntimeException ex) {
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "验证码服务暂时不可用: " + rootCauseMessage(ex),
                    ex);
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            saveCode(email, purpose, code);
        } catch (RuntimeException ex) {
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "验证码服务暂时不可用: " + rootCauseMessage(ex),
                    ex);
        }

        boolean delivered = mailService.sendVerificationCode(email, code, purpose);
        if (!delivered) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "邮件服务暂时不可用");
        }
        return new SendCodeResponseDTO(delivered);
    }

    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        String email = normalizeEmail(request.email());
        if (existsByEmail(email)) {
            throw new AppException(HttpStatus.CONFLICT, "邮箱已注册");
        }

        validateCode(email, request.code(), VerificationPurposeEnum.REGISTER);

        UserDO user = new UserDO();
        user.setEmail(email);
        user.setNickname(request.nickname().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFailedLoginCount(0);
        userMapper.insert(user);

        UserDO saved = userMapper.findById(user.getId());
        String token = tokenProvider.createToken(saved.getId(), saved.getEmail());
        return authAssembler.toAuthResponse(token, saved);
    }

    @Transactional
    public AuthResponseDTO login(LoginRequestDTO request) {
        String email = normalizeEmail(request.email());
        UserDO user = userMapper.findByEmail(email);
        if (user == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AppException(HttpStatus.LOCKED, "账号已被暂时锁定");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int nextFailCount = user.getFailedLoginCount() + 1;
            LocalDateTime lockedUntil = user.getLockedUntil();
            if (nextFailCount >= appProperties.getAuth().getMaxLoginFailures()) {
                lockedUntil = LocalDateTime.now().plusMinutes(appProperties.getAuth().getLockMinutes());
                nextFailCount = 0;
            }
            userMapper.updateAuthState(user.getId(), nextFailCount, lockedUntil);
            throw new AppException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误");
        }

        userMapper.updateAuthState(user.getId(), 0, null);
        String token = tokenProvider.createToken(user.getId(), user.getEmail());
        return authAssembler.toAuthResponse(token, user);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequestDTO request) {
        String email = normalizeEmail(request.email());
        UserDO user = userMapper.findByEmail(email);
        if (user == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "邮箱未注册");
        }

        validateCode(email, request.code(), VerificationPurposeEnum.RESET_PASSWORD);

        userMapper.updatePasswordAndUnlock(user.getId(), passwordEncoder.encode(request.newPassword()), 0, null);
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDTO me(UserPrincipal principal) {
        UserDO user = userMapper.findById(principal.userId());
        if (user == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return userProfileAssembler.toResponse(user);
    }

    private void validateCode(String email, String inputCode, VerificationPurposeEnum purpose) {
        String key = codeKey(email, purpose);
        String code = verificationCodeStore.get(key);
        if (code == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码不存在或已过期");
        }
        if (!code.equals(inputCode)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码错误");
        }
        verificationCodeStore.delete(key);
    }

    private void checkCooldown(String email, VerificationPurposeEnum purpose) {
        Long ttl = verificationCodeStore.getExpire(codeKey(email, purpose));
        if (ttl != null && ttl > cooldownSeconds()) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "请求验证码过于频繁，请稍后再试");
        }
    }

    private void checkHourlyLimit(String email, VerificationPurposeEnum purpose) {
        String value = verificationCodeStore.get(limitKey(email, purpose));
        int count;
        if (value == null) {
            count = 0;
        } else {
            try {
                count = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                verificationCodeStore.delete(limitKey(email, purpose));
                count = 0;
            }
        }
        if (count >= appProperties.getVerificationCode().getHourlyLimit()) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "验证码请求次数已超过每小时上限");
        }
    }

    private void saveCode(String email, VerificationPurposeEnum purpose, String code) {
        verificationCodeStore.set(codeKey(email, purpose), code, codeTtl());
        verificationCodeStore.increment(limitKey(email, purpose), Duration.ofHours(1));
    }

    private Duration codeTtl() {
        return Duration.ofMinutes(appProperties.getVerificationCode().getExpireMinutes());
    }

    private long cooldownSeconds() {
        long ttl = codeTtl().getSeconds();
        long cooldown = appProperties.getVerificationCode().getResendCooldownSeconds();
        return Math.max(ttl - cooldown, 0);
    }

    private String codeKey(String email, VerificationPurposeEnum purpose) {
        return appProperties.getVerificationCode().getKeyPrefix() + purpose.name() + ":" + email;
    }

    private String limitKey(String email, VerificationPurposeEnum purpose) {
        return appProperties.getVerificationCode().getLimitPrefix() + purpose.name() + ":" + email;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean existsByEmail(String email) {
        return userMapper.countByEmail(email) > 0;
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
