package com.campustrade.platform.user.service;

import com.campustrade.platform.auth.dto.request.WechatLoginRequestDTO;
import com.campustrade.platform.auth.dto.request.BindEmailRequestDTO;
import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.auth.service.WechatSession;
import com.campustrade.platform.auth.service.WechatSessionClient;
import com.campustrade.platform.auth.service.VerificationCodeService;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.dto.request.UpdateProfileRequestDTO;
import com.campustrade.platform.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final WechatSessionClient wechatSessionClient = mock(WechatSessionClient.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
    private final UserService userService = new UserService(
            userMapper,
            uploadService,
            wechatSessionClient,
            passwordEncoder,
            verificationCodeService
    );

    @Test
    void updateProfileDoesNotResubmitAvatarReviewWhenRequestUsesEquivalentProxyUrl() {
        Long userId = 1L;
        String objectKey = "images/2026/06/avatar.jpg";
        String proxyUrl = "http://localhost:8080/api/v1/images/2026/06/avatar.jpg";
        UserDO existingUser = user(userId, "old-name", objectKey);
        UserDO updatedUser = user(userId, "new-name", objectKey);

        when(userMapper.findById(userId)).thenReturn(existingUser, updatedUser);
        when(uploadService.extractObjectKey(proxyUrl)).thenReturn(objectKey);

        UserDO result = userService.updateProfile(userId, new UpdateProfileRequestDTO(" new-name ", proxyUrl, null, null));

        assertEquals("new-name", result.getNickname());
        verify(userMapper).updateProfile(userId, "new-name", objectKey, null, null);
        verify(userMapper, never()).updateAvatarAuditStatus(anyLong(), any(), any(), any());
        verify(uploadService, never()).deleteObject(any());
    }

    @Test
    void updateProfileKeepsCurrentAvatarWhenAvatarUrlIsOmitted() {
        Long userId = 1L;
        String objectKey = "images/2026/06/avatar.jpg";
        UserDO existingUser = user(userId, "old-name", objectKey);
        UserDO updatedUser = user(userId, "new-name", objectKey);

        when(userMapper.findById(userId)).thenReturn(existingUser, updatedUser);

        UserDO result = userService.updateProfile(userId, new UpdateProfileRequestDTO("new-name", null, null, null));

        assertEquals("new-name", result.getNickname());
        verify(userMapper).updateProfile(userId, "new-name", objectKey, null, null);
        verify(userMapper, never()).updateAvatarAuditStatus(anyLong(), any(), any(), any());
        verify(uploadService, never()).deleteObject(any());
    }

    @Test
    void updateProfileTrimsCampusContactFields() {
        Long userId = 1L;
        String objectKey = "images/2026/06/avatar.jpg";
        UserDO existingUser = user(userId, "old-name", objectKey);
        UserDO updatedUser = user(userId, "new-name", objectKey);
        updatedUser.setWechatId("wx_school_231");
        updatedUser.setQq("123456789");

        when(userMapper.findById(userId)).thenReturn(existingUser, updatedUser);

        UserDO result = userService.updateProfile(
                userId,
                new UpdateProfileRequestDTO(" new-name ", null, " wx_school_231 ", " 123456789 ")
        );

        assertEquals("new-name", result.getNickname());
        assertEquals("wx_school_231", result.getWechatId());
        assertEquals("123456789", result.getQq());
        verify(userMapper).updateProfile(userId, "new-name", objectKey, "wx_school_231", "123456789");
    }

    @Test
    void updateProfileValidatesChangedAvatarAndDefersOldObjectDeletion() {
        Long userId = 1L;
        String oldObjectKey = "images/2026/06/avatar/avatar_u1_20260601010101_abc123.jpg";
        String newObjectKey = "images/2026/07/avatar/avatar_u1_20260712120000_a1b2c3.jpg";
        String newProxyUrl = "http://localhost:8080/api/v1/images/2026/07/avatar/avatar_u1_20260712120000_a1b2c3.jpg";
        UserDO existingUser = user(userId, "old-name", oldObjectKey);
        UserDO updatedUser = user(userId, "new-name", newObjectKey);

        when(userMapper.findById(userId)).thenReturn(existingUser, updatedUser);
        when(uploadService.extractObjectKey(newProxyUrl)).thenReturn(newObjectKey);
        when(uploadService.validateUploadedImageReference(newProxyUrl, "avatar", userId)).thenReturn(newObjectKey);

        UserDO result = userService.updateProfile(userId, new UpdateProfileRequestDTO("new-name", newProxyUrl, null, null));

        assertEquals("new-name", result.getNickname());
        verify(userMapper).updateProfile(userId, "new-name", newObjectKey, null, null);
        verify(userMapper).updateAvatarAuditStatus(userId, ImageAuditStatusEnum.PENDING, null, null);
        verify(uploadService).deleteObjectAfterCommit(oldObjectKey);
        verify(uploadService, never()).deleteObject(any());
    }

    @Test
    void bindWechatStoresOpenidOnCurrentEmailAccount() {
        Long userId = 1L;
        UserDO existingUser = user(userId, "old-name", "images/avatar.jpg");
        UserDO boundUser = user(userId, "old-name", "images/avatar.jpg");
        boundUser.setWechatOpenid("openid-1");

        when(userMapper.findById(userId)).thenReturn(existingUser, boundUser);
        when(wechatSessionClient.exchange("wx-code")).thenReturn(new WechatSession("openid-1", "session-key", null));
        when(userMapper.findByWechatOpenid("openid-1")).thenReturn(null);

        UserDO result = userService.bindWechat(userId, new WechatLoginRequestDTO("wx-code"));

        assertEquals("openid-1", result.getWechatOpenid());
        verify(userMapper).updateWechatOpenid(userId, "openid-1");
    }

    @Test
    void bindWechatRejectsOpenidAlreadyBoundToAnotherAccount() {
        Long userId = 1L;
        UserDO existingUser = user(userId, "old-name", "images/avatar.jpg");
        UserDO otherUser = user(2L, "other-name", "images/other.jpg");
        otherUser.setWechatOpenid("openid-1");

        when(userMapper.findById(userId)).thenReturn(existingUser);
        when(wechatSessionClient.exchange("wx-code")).thenReturn(new WechatSession("openid-1", "session-key", null));
        when(userMapper.findByWechatOpenid("openid-1")).thenReturn(otherUser);

        AppException ex = assertThrows(
                AppException.class,
                () -> userService.bindWechat(userId, new WechatLoginRequestDTO("wx-code"))
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("该微信已绑定其他账号", ex.getMessage());
        verify(userMapper, never()).updateWechatOpenid(anyLong(), any());
    }

    @Test
    void bindEmailStoresVerifiedQqEmailAndPasswordOnCurrentWechatAccount() {
        Long userId = 1L;
        UserDO existingUser = wechatOnlyUser(userId, "微信用户abcd");
        UserDO boundUser = wechatOnlyUser(userId, "微信用户abcd");
        boundUser.setEmail("student@qq.com");
        boundUser.setPasswordHash("encoded-password");

        when(userMapper.findById(userId)).thenReturn(existingUser, boundUser);
        when(userMapper.findByEmail("student@qq.com")).thenReturn(null);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");

        UserDO result = userService.bindEmail(
                userId,
                new BindEmailRequestDTO(" Student@qq.com ", "123456", "secret123")
        );

        assertEquals("student@qq.com", result.getEmail());
        verify(userMapper).updateEmailAndPassword(userId, "student@qq.com", "encoded-password", 0, null);
        verify(verificationCodeService).validateCode(
                "student@qq.com",
                "123456",
                VerificationPurposeEnum.BIND_EMAIL
        );
    }

    @Test
    void bindEmailRejectsRegisteredEmailAndGuidesAccountMerge() {
        Long userId = 1L;
        UserDO existingUser = wechatOnlyUser(userId, "微信用户abcd");
        UserDO emailUser = user(2L, "软工-231", "images/avatar.jpg");
        emailUser.setEmail("student@qq.com");

        when(userMapper.findById(userId)).thenReturn(existingUser);
        when(userMapper.findByEmail("student@qq.com")).thenReturn(emailUser);

        AppException ex = assertThrows(
                AppException.class,
                () -> userService.bindEmail(userId, new BindEmailRequestDTO("student@qq.com", "123456", "secret123"))
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("该邮箱已注册，请使用账号合并", ex.getMessage());
        verify(userMapper, never()).updateEmailAndPassword(anyLong(), any(), any(), anyInt(), any());
    }

    @Test
    void unbindWechatKeepsEmailLoginAndClearsOpenid() {
        Long userId = 1L;
        UserDO existingUser = user(userId, "软工-231", null);
        existingUser.setWechatOpenid("openid-1");
        UserDO updatedUser = user(userId, "软工-231", null);

        when(userMapper.findById(userId)).thenReturn(existingUser, updatedUser);

        UserDO result = userService.unbindWechat(userId);

        assertNull(result.getWechatOpenid());
        verify(userMapper).updateWechatOpenid(userId, null);
    }

    @Test
    void unbindWechatRejectsRemovingTheOnlyLoginMethod() {
        Long userId = 1L;
        UserDO existingUser = wechatOnlyUser(userId, "微信用户abcd");
        when(userMapper.findById(userId)).thenReturn(existingUser);

        AppException ex = assertThrows(AppException.class, () -> userService.unbindWechat(userId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("请先绑定邮箱，再解绑微信", ex.getMessage());
        verify(userMapper, never()).updateWechatOpenid(anyLong(), any());
    }

    @Test
    void unbindEmailKeepsWechatLoginAndClearsPassword() {
        Long userId = 1L;
        UserDO existingUser = user(userId, "软工-231", null);
        existingUser.setWechatOpenid("openid-1");
        UserDO updatedUser = wechatOnlyUser(userId, "软工-231");

        when(userMapper.findById(userId)).thenReturn(existingUser, updatedUser);

        UserDO result = userService.unbindEmail(userId);

        assertNull(result.getEmail());
        assertNull(result.getPasswordHash());
        verify(userMapper).updateEmailAndPassword(userId, null, null, 0, null);
    }

    @Test
    void unbindEmailRejectsRemovingTheOnlyLoginMethod() {
        Long userId = 1L;
        UserDO existingUser = user(userId, "软工-231", null);
        when(userMapper.findById(userId)).thenReturn(existingUser);

        AppException ex = assertThrows(AppException.class, () -> userService.unbindEmail(userId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("请先绑定微信，再解绑邮箱", ex.getMessage());
        verify(userMapper, never()).updateEmailAndPassword(anyLong(), any(), any(), anyInt(), any());
    }

    private UserDO user(Long id, String nickname, String avatarUrl) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setAvatarAuditStatus(ImageAuditStatusEnum.APPROVED);
        return user;
    }

    private UserDO wechatOnlyUser(Long id, String nickname) {
        UserDO user = user(id, nickname, null);
        user.setEmail(null);
        user.setPasswordHash(null);
        user.setWechatOpenid("openid-" + id);
        return user;
    }
}
