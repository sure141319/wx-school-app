package com.campustrade.platform.announcement.controller;

import com.campustrade.platform.announcement.dto.response.AnnouncementPublicResponseDTO;
import com.campustrade.platform.announcement.service.AnnouncementService;
import com.campustrade.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {
    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/current")
    public ApiResponse<AnnouncementPublicResponseDTO> current() {
        return ApiResponse.ok(announcementService.getCurrentPublic());
    }
}
