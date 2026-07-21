package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.assembler.AuthAssembler;
import com.campustrade.platform.auth.dto.request.LoginRequestDTO;
import com.campustrade.platform.auth.dto.request.RegisterRequestDTO;
import com.campustrade.platform.auth.dto.request.ResetPasswordRequestDTO;
import com.campustrade.platform.auth.dto.request.SendCodeRequestDTO;
import com.campustrade.platform.auth.dto.request.WechatLoginRequestDTO;
import com.campustrade.platform.auth.dto.response.AuthResponseDTO;
import com.campustrade.platform.auth.dto.response.SendCodeResponseDTO;
import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.common.time.BeijingTime;
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
    private final VerificationCodeService verificationCodeService;
    private final WechatSessionClient wechatSessionClient;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       MailService mailService,
                       AppProperties appProperties,
                       AuthAssembler authAssembler,
                       UserProfileAssembler userProfileAssembler,
                       VerificationCodeService verificationCodeService,
                       WechatSessionClient wechatSessionClient) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.mailService = mailService;
        this.appProperties = appProperties;
        this.authAssembler = authAssembler;
        this.userProfileAssembler = userProfileAssembler;
        this.verificationCodeService = verificationCodeService;
        this.wechatSessionClient = wechatSessionClient;
    }

    @Transactional
    public SendCodeResponseDTO sendVerificationCode(SendCodeRequestDTO request) {
        VerificationPurposeEnum purpose = request.purpose() == null ? VerificationPurposeEnum.REGISTER : request.purpose();
        String email = normalizeEmail(request.email());

        if (purpose == VerificationPurposeEnum.REGISTER && existsByEmail(email)) {
            throw new AppException(HttpStatus.CONFLICT, "邮箱已注册");
        }
        if (purpose == VerificationPurposeEnum.BIND_EMAIL && existsByEmail(email)) {
            throw new AppException(HttpStatus.CONFLICT, "该邮箱已注册，请使用账号合并");
        }
        if (purpose == VerificationPurposeEnum.RESET_PASSWORD && !existsByEmail(email)) {
            throw new AppException(HttpStatus.NOT_FOUND, "邮箱未注册");
        }

        verificationCodeService.ensureCanSend(email, purpose);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        verificationCodeService.saveCode(email, purpose, code);

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

        verificationCodeService.validateCode(email, request.code(), VerificationPurposeEnum.REGISTER);

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

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(BeijingTime.now())) {
            throw new AppException(HttpStatus.LOCKED, "账号已被暂时锁定");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int nextFailCount = user.getFailedLoginCount() + 1;
            LocalDateTime lockedUntil = user.getLockedUntil();
            if (nextFailCount >= appProperties.getAuth().getMaxLoginFailures()) {
                lockedUntil = BeijingTime.now().plusMinutes(appProperties.getAuth().getLockMinutes());
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
    public AuthResponseDTO wechatLogin(WechatLoginRequestDTO request) {
        WechatSession session = wechatSessionClient.exchange(request.code().trim());
        UserDO user = userMapper.findByWechatOpenid(session.openid());
        if (user == null) {
            user = new UserDO();
            user.setWechatOpenid(session.openid());
            user.setNickname(defaultWechatNickname(session.openid()));
            user.setFailedLoginCount(0);
            userMapper.insert(user);
            user = userMapper.findById(user.getId());
        }

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

        verificationCodeService.validateCode(email, request.code(), VerificationPurposeEnum.RESET_PASSWORD);

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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean existsByEmail(String email) {
        return userMapper.countByEmail(email) > 0;
    }

    private String defaultWechatNickname(String openid) {
        int suffixLength = Math.min(4, openid.length());
        String suffix = openid.substring(openid.length() - suffixLength);
        return "微信用户" + suffix;
    }
}
