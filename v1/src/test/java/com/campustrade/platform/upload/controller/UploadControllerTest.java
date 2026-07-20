package com.campustrade.platform.upload.controller;

import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.security.UserPrincipal;
import com.campustrade.platform.upload.dto.response.UploadResponseDTO;
import com.campustrade.platform.upload.service.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadControllerTest {

    @Test
    void uploadSuccessMessageUsesFriendlyChineseCopyAndPassesUsageWithCurrentUser() {
        UploadService uploadService = mock(UploadService.class);
        UploadController controller = new UploadController(uploadService);
        MockMultipartFile file = new MockMultipartFile("file", "demo.png", "image/png", new byte[]{1});
        UploadResponseDTO payload = new UploadResponseDTO(
                "/images/demo.png",
                "demo.png",
                "/images/thumbs/demo.webp",
                "thumbs/demo.webp"
        );

        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(new UserPrincipal(12L, "demo@qq.com"), null));
        when(uploadService.storeImage(file, "avatar", 12L)).thenReturn(payload);

        try {
            ApiResponse<UploadResponseDTO> response = controller.uploadImage(file, "avatar");

            assertEquals("图片上传成功", response.message());
            assertEquals(payload, response.data());
            verify(uploadService).storeImage(file, "avatar", 12L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deletesOnlyThroughCurrentUsersStagedUploadPath() {
        UploadService uploadService = mock(UploadService.class);
        UploadController controller = new UploadController(uploadService);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(new UserPrincipal(12L, "demo@qq.com"), null));

        try {
            ApiResponse<Void> response = controller.deleteStagedImage("images/2026/07/goods/demo.jpg");

            assertEquals("暂存图片已删除", response.message());
            verify(uploadService).deleteStagedUpload(12L, "images/2026/07/goods/demo.jpg");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
