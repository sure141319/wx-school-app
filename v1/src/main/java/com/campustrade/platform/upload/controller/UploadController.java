package com.campustrade.platform.upload.controller;

import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.upload.dto.request.PresignRequestDTO;
import com.campustrade.platform.upload.dto.response.UploadResponseDTO;
import com.campustrade.platform.upload.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponseDTO> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Upload success", uploadService.storeImage(file));
    }

    @PostMapping("/presign/batch")
    public ApiResponse<Map<String, String>> presignBatch(@Valid @RequestBody PresignRequestDTO request) {
        return ApiResponse.ok(uploadService.presignUrls(request.urls()));
    }
}
