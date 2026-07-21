package com.campustrade.platform.announcement.controller;

import com.campustrade.platform.announcement.dto.request.UpdateAnnouncementRequestDTO;
import com.campustrade.platform.announcement.dto.response.AnnouncementAdminResponseDTO;
import com.campustrade.platform.announcement.service.AnnouncementService;
import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.security.AuthUtils;
import com.campustrade.platform.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit/announcement")
public class AnnouncementAdminController {
    private final AnnouncementService announcementService;

    public AnnouncementAdminController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public ApiResponse<AnnouncementAdminResponseDTO> current() {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok(announcementService.getCurrentForAdmin(principal.userId()));
    }

    @PutMapping
    public ApiResponse<AnnouncementAdminResponseDTO> update(
            @Valid @RequestBody UpdateAnnouncementRequestDTO request) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok("公告已保存", announcementService.update(principal.userId(), request));
    }
}
