package com.campustrade.platform.audit.controller;

import com.campustrade.platform.audit.dto.request.ImageRejectRequestDTO;
import com.campustrade.platform.audit.dto.response.ThumbnailBackfillResponseDTO;
import com.campustrade.platform.audit.service.AuditImageService;
import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditImageControllerTest {

    private final AuditImageService auditImageService = mock(AuditImageService.class);
    private final AuditImageController controller = new AuditImageController(auditImageService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditSuccessMessagesUseFriendlyChineseCopy() {
        authenticateReviewer();

        assertEquals("图片审核通过", controller.approve(1L).message());
        assertEquals("图片已驳回", controller.reject(1L, null).message());
        assertEquals("头像审核通过", controller.approveAvatar(1L).message());
        assertEquals("头像已驳回", controller.rejectAvatar(1L, null).message());
    }

    @Test
    void backfillThumbnailsUsesCurrentReviewerAndRequestedBatchSize() {
        authenticateReviewer();
        ThumbnailBackfillResponseDTO payload = new ThumbnailBackfillResponseDTO(2, 1, 1, 0, 3);
        when(auditImageService.backfillMissingThumbnails(1L, 20)).thenReturn(payload);

        ApiResponse<ThumbnailBackfillResponseDTO> response = controller.backfillThumbnails(20);

        assertEquals("历史缩略图回填完成", response.message());
        assertEquals(payload, response.data());
        verify(auditImageService).backfillMissingThumbnails(1L, 20);
    }

    @Test
    void approveAllPendingUsesCurrentReviewerAndConfirmation() {
        authenticateReviewer();
        ImageRejectRequestDTO request = new ImageRejectRequestDTO(null, "APPROVE_ALL_PENDING");
        when(auditImageService.approveAllPending(1L, "APPROVE_ALL_PENDING")).thenReturn(3);

        ApiResponse<Integer> response = controller.approveAllPending(request);

        assertEquals("已通过 3 张图片", response.message());
        assertEquals(3, response.data());
        verify(auditImageService).approveAllPending(1L, "APPROVE_ALL_PENDING");
    }

    private void authenticateReviewer() {
        UserPrincipal principal = new UserPrincipal(1L, "reviewer@qq.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList())
        );
    }
}
