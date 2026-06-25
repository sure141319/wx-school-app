package com.campustrade.platform.user.service;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.dto.request.UpdateProfileRequestDTO;
import com.campustrade.platform.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final UserService userService = new UserService(userMapper, uploadService);

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

    private UserDO user(Long id, String nickname, String avatarUrl) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setAvatarAuditStatus(ImageAuditStatusEnum.APPROVED);
        return user;
    }
}
