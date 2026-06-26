package com.campustrade.platform.user.assembler;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;
import com.campustrade.platform.user.dto.response.UserSummaryResponseDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserProfileAssemblerTest {

    @Test
    void approvedUploadedAvatarWinsOverQqAvatar() {
        UploadService uploadService = mock(UploadService.class);
        UserProfileAssembler assembler = new UserProfileAssembler(uploadService);
        UserDO user = user("软工-231", "123456", "images/2026/06/avatar.jpg", ImageAuditStatusEnum.APPROVED);
        when(uploadService.getProxyUrl("images/2026/06/avatar.jpg"))
                .thenReturn("https://cdn.example.com/avatar.jpg");

        UserProfileResponseDTO response = assembler.toResponse(user);

        assertEquals("https://cdn.example.com/avatar.jpg", response.avatarUrl());
        assertEquals("UPLOADED", response.avatarSource());
        verify(uploadService).getProxyUrl("images/2026/06/avatar.jpg");
    }

    @Test
    void pendingAvatarFallsBackToQqAvatar() {
        UploadService uploadService = mock(UploadService.class);
        UserProfileAssembler assembler = new UserProfileAssembler(uploadService);
        UserDO user = user("软工-231", "123456", "images/2026/06/avatar.jpg", ImageAuditStatusEnum.PENDING);

        UserProfileResponseDTO response = assembler.toResponse(user);

        assertEquals("https://q1.qlogo.cn/g?b=qq&nk=123456&s=640", response.avatarUrl());
        assertEquals("QQ", response.avatarSource());
        verify(uploadService, never()).getProxyUrl("images/2026/06/avatar.jpg");
    }

    @Test
    void missingAvatarFallsBackToQqAvatarInSummary() {
        UploadService uploadService = mock(UploadService.class);
        UserProfileAssembler assembler = new UserProfileAssembler(uploadService);
        UserDO user = user("软工-231", "123456", null, ImageAuditStatusEnum.APPROVED);

        UserSummaryResponseDTO response = assembler.toSummaryResponse(user);

        assertEquals("https://q1.qlogo.cn/g?b=qq&nk=123456&s=640", response.avatarUrl());
        assertEquals("QQ", response.avatarSource());
        verifyNoInteractions(uploadService);
    }

    @Test
    void missingAvatarAndQqLeavesAvatarEmptyForInitialFallback() {
        UploadService uploadService = mock(UploadService.class);
        UserProfileAssembler assembler = new UserProfileAssembler(uploadService);
        UserDO user = user("软工-231", "", null, ImageAuditStatusEnum.APPROVED);

        UserProfileResponseDTO response = assembler.toResponse(user);

        assertEquals("", response.avatarUrl());
        assertEquals("INITIAL", response.avatarSource());
        verifyNoInteractions(uploadService);
    }

    private UserDO user(String nickname, String qq, String avatarUrl, ImageAuditStatusEnum avatarAuditStatus) {
        UserDO user = new UserDO();
        user.setId(1L);
        user.setNickname(nickname);
        user.setQq(qq);
        user.setAvatarUrl(avatarUrl);
        user.setAvatarAuditStatus(avatarAuditStatus);
        return user;
    }
}
