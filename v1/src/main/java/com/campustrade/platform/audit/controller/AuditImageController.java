package com.campustrade.platform.audit.controller;

import com.campustrade.platform.audit.dto.request.ImageRejectRequestDTO;
import com.campustrade.platform.audit.dto.response.AuditImageResponseDTO;
import com.campustrade.platform.audit.dto.response.AvatarAuditResponseDTO;
import com.campustrade.platform.audit.service.AuditImageService;
import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.security.AuthUtils;
import com.campustrade.platform.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/audit/images")
public class AuditImageController {

    private final AuditImageService auditImageService;

    public AuditImageController(AuditImageService auditImageService) {
        this.auditImageService = auditImageService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditImageResponseDTO>> list(
            @RequestParam(required = false) ImageAuditStatusEnum status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok(auditImageService.list(principal.userId(), status, page, size));
    }

    @PostMapping("/{imageId}/approve")
    public ApiResponse<AuditImageResponseDTO> approve(@PathVariable Long imageId) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok("Image approved", auditImageService.approve(principal.userId(), imageId));
    }

    @PostMapping("/{imageId}/reject")
    public ApiResponse<AuditImageResponseDTO> reject(@PathVariable Long imageId,
                                                     @Valid @RequestBody(required = false) ImageRejectRequestDTO request) {
        UserPrincipal principal = AuthUtils.currentUser();
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok("Image rejected", auditImageService.reject(principal.userId(), imageId, remark));
    }

    // ==================== 头像审核接口 ====================

    @GetMapping("/avatars")
    public ApiResponse<PageResponse<AvatarAuditResponseDTO>> listAvatars(
            @RequestParam(required = false) ImageAuditStatusEnum status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok(auditImageService.listAvatars(principal.userId(), status, page, size));
    }

    @PostMapping("/avatars/{userId}/approve")
    public ApiResponse<AvatarAuditResponseDTO> approveAvatar(@PathVariable Long userId) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok("Avatar approved", auditImageService.approveAvatar(principal.userId(), userId));
    }

    @PostMapping("/avatars/{userId}/reject")
    public ApiResponse<AvatarAuditResponseDTO> rejectAvatar(@PathVariable Long userId,
                                                            @Valid @RequestBody(required = false) ImageRejectRequestDTO request) {
        UserPrincipal principal = AuthUtils.currentUser();
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok("Avatar rejected", auditImageService.rejectAvatar(principal.userId(), userId, remark));
    }
}
