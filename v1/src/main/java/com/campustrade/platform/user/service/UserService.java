package com.campustrade.platform.user.service;

import com.campustrade.platform.auth.dto.request.BindEmailRequestDTO;
import com.campustrade.platform.auth.dto.request.WechatLoginRequestDTO;
import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.auth.service.WechatSession;
import com.campustrade.platform.auth.service.WechatSessionClient;
import com.campustrade.platform.auth.store.VerificationCodeStore;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.dto.request.UpdateProfileRequestDTO;
import com.campustrade.platform.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UploadService uploadService;
    private final WechatSessionClient wechatSessionClient;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeStore verificationCodeStore;
    private final AppProperties appProperties;

    public UserService(UserMapper userMapper,
                       UploadService uploadService,
                       WechatSessionClient wechatSessionClient,
                       PasswordEncoder passwordEncoder,
                       VerificationCodeStore verificationCodeStore,
                       AppProperties appProperties) {
        this.userMapper = userMapper;
        this.uploadService = uploadService;
        this.wechatSessionClient = wechatSessionClient;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeStore = verificationCodeStore;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public UserDO getById(Long userId) {
        UserDO user = userMapper.findById(userId);
        if (user == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    @Transactional
    public UserDO updateProfile(Long userId, UpdateProfileRequestDTO request) {
        UserDO user = getById(userId);
        String requestedAvatarUrl = request.avatarUrl() == null ? null : request.avatarUrl().trim();
        boolean avatarProvided = StringUtils.hasText(requestedAvatarUrl);
        String newAvatarUrl = avatarProvided ? normalizeAvatarObjectKey(requestedAvatarUrl) : user.getAvatarUrl();

        boolean avatarChanged = avatarProvided && !newAvatarUrl.equals(user.getAvatarUrl());
        if (avatarChanged) {
            newAvatarUrl = uploadService.validateUploadedImageReference(requestedAvatarUrl, "avatar", userId);
            uploadService.deleteObjectAfterCommit(user.getAvatarUrl());
        }

        String wechatId = normalizeOptional(request.wechatId(), user.getWechatId());
        String qq = normalizeOptional(request.qq(), user.getQq());

        userMapper.updateProfile(
                userId,
                request.nickname().trim(),
                newAvatarUrl,
                wechatId,
                qq);

        if (avatarChanged) {
            userMapper.updateAvatarAuditStatus(userId, ImageAuditStatusEnum.PENDING, null, null);
        }

        return getById(userId);
    }

    @Transactional
    public UserDO bindWechat(Long userId, WechatLoginRequestDTO request) {
        UserDO currentUser = getById(userId);
        WechatSession session = wechatSessionClient.exchange(request.code().trim());
        String openid = session.openid();

        if (StringUtils.hasText(currentUser.getWechatOpenid())) {
            if (currentUser.getWechatOpenid().equals(openid)) {
                return currentUser;
            }
            throw new AppException(HttpStatus.CONFLICT, "当前账号已绑定其他微信");
        }

        UserDO boundUser = userMapper.findByWechatOpenid(openid);
        if (boundUser != null && !boundUser.getId().equals(userId)) {
            throw new AppException(HttpStatus.CONFLICT, "该微信已绑定其他账号");
        }

        userMapper.updateWechatOpenid(userId, openid);
        return getById(userId);
    }

    @Transactional
    public UserDO bindEmail(Long userId, BindEmailRequestDTO request) {
        UserDO currentUser = getById(userId);
        if (StringUtils.hasText(currentUser.getEmail())) {
            throw new AppException(HttpStatus.CONFLICT, "当前账号已绑定邮箱");
        }

        String email = normalizeEmail(request.email());
        UserDO emailUser = userMapper.findByEmail(email);
        if (emailUser != null && !emailUser.getId().equals(userId)) {
            throw new AppException(HttpStatus.CONFLICT, "该邮箱已注册，请使用账号合并");
        }

        validateBindEmailCode(email, request.code());
        String passwordHash = passwordEncoder.encode(request.password());
        userMapper.updateEmailAndPassword(userId, email, passwordHash, 0, null);
        return getById(userId);
    }

    private String normalizeAvatarObjectKey(String avatarUrl) {
        String objectKey = uploadService.extractObjectKey(avatarUrl);
        return objectKey == null ? avatarUrl : objectKey;
    }

    private void validateBindEmailCode(String email, String inputCode) {
        String key = codeKey(email, VerificationPurposeEnum.BIND_EMAIL);
        String code = verificationCodeStore.get(key);
        if (code == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码不存在或已过期");
        }
        if (!code.equals(inputCode)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码错误");
        }
        verificationCodeStore.delete(key);
    }

    private String codeKey(String email, VerificationPurposeEnum purpose) {
        return appProperties.getVerificationCode().getKeyPrefix() + purpose.name() + ":" + email;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value, String currentValue) {
        if (value == null) {
            return currentValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
