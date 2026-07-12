package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.assembler.AuthAssembler;
import com.campustrade.platform.auth.dto.request.SendCodeRequestDTO;
import com.campustrade.platform.auth.dto.request.WechatLoginRequestDTO;
import com.campustrade.platform.auth.dto.response.AuthResponseDTO;
import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.security.JwtTokenProvider;
import com.campustrade.platform.user.assembler.UserProfileAssembler;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;
import com.campustrade.platform.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final MailService mailService = mock(MailService.class);
    private final AppProperties appProperties = new AppProperties();
    private final AuthAssembler authAssembler = mock(AuthAssembler.class);
    private final UserProfileAssembler userProfileAssembler = mock(UserProfileAssembler.class);
    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
    private final WechatSessionClient wechatSessionClient = mock(WechatSessionClient.class);
    private final AuthService authService = new AuthService(
            userMapper,
            passwordEncoder,
            tokenProvider,
            mailService,
            appProperties,
            authAssembler,
            userProfileAssembler,
            verificationCodeService,
            wechatSessionClient
    );

    @Test
    void sendVerificationCodePreservesRateLimitStatus() {
        doThrow(new AppException(HttpStatus.TOO_MANY_REQUESTS, "请求验证码过于频繁，请稍后再试"))
                .when(verificationCodeService)
                .ensureCanSend("student@qq.com", VerificationPurposeEnum.REGISTER);

        AppException exception = assertThrows(
                AppException.class,
                () -> authService.sendVerificationCode(new SendCodeRequestDTO(" Student@qq.com ", VerificationPurposeEnum.REGISTER))
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        verify(mailService, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void wechatLoginCreatesUserWhenOpenidIsNew() {
        WechatSession session = new WechatSession("openid-1", "session-key", "union-1");
        when(wechatSessionClient.exchange("wx-code")).thenReturn(session);
        when(userMapper.findByWechatOpenid("openid-1")).thenReturn(null);
        doAnswer(invocation -> {
            UserDO user = invocation.getArgument(0);
            user.setId(99L);
            return 1;
        }).when(userMapper).insert(any(UserDO.class));

        UserDO saved = wechatUser(99L, "openid-1", "微信用户0001");
        when(userMapper.findById(99L)).thenReturn(saved);
        when(tokenProvider.createToken(99L, null)).thenReturn("jwt-token");
        when(authAssembler.toAuthResponse("jwt-token", saved)).thenReturn(authResponse("jwt-token", saved));

        AuthResponseDTO response = authService.wechatLogin(new WechatLoginRequestDTO("wx-code"));

        assertEquals("jwt-token", response.token());
        assertEquals("openid-1", response.user().wechatOpenid());
        assertEquals("微信用户0001", response.user().nickname());

        ArgumentCaptor<UserDO> captor = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insert(captor.capture());
        UserDO inserted = captor.getValue();
        assertEquals("openid-1", inserted.getWechatOpenid());
        assertNull(inserted.getEmail());
        assertNull(inserted.getPasswordHash());
        assertTrue(inserted.getNickname().startsWith("微信用户"));
    }

    @Test
    void wechatLoginReusesExistingUserForSameOpenid() {
        WechatSession session = new WechatSession("openid-1", "session-key", null);
        UserDO existing = wechatUser(5L, "openid-1", "软工-231");

        when(wechatSessionClient.exchange("wx-code")).thenReturn(session);
        when(userMapper.findByWechatOpenid("openid-1")).thenReturn(existing);
        when(tokenProvider.createToken(5L, null)).thenReturn("jwt-existing");
        when(authAssembler.toAuthResponse("jwt-existing", existing)).thenReturn(authResponse("jwt-existing", existing));

        AuthResponseDTO response = authService.wechatLogin(new WechatLoginRequestDTO("wx-code"));

        assertEquals("jwt-existing", response.token());
        assertEquals("软工-231", response.user().nickname());
        verify(userMapper, never()).insert(any(UserDO.class));
    }

    private AuthResponseDTO authResponse(String token, UserDO user) {
        return new AuthResponseDTO(
                token,
                new UserProfileResponseDTO(
                        user.getId(),
                        user.getEmail(),
                        user.getNickname(),
                        user.getAvatarUrl(),
                        user.getWechatOpenid(),
                        user.getWechatId(),
                        user.getQq(),
                        "INITIAL"
                )
        );
    }

    private UserDO wechatUser(Long id, String openid, String nickname) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setWechatOpenid(openid);
        user.setNickname(nickname);
        user.setFailedLoginCount(0);
        return user;
    }
}
