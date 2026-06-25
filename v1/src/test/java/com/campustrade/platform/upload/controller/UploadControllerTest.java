package com.campustrade.platform.upload.controller;

import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.upload.dto.response.UploadResponseDTO;
import com.campustrade.platform.upload.service.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadControllerTest {

    @Test
    void uploadSuccessMessageUsesFriendlyChineseCopy() {
        UploadService uploadService = mock(UploadService.class);
        UploadController controller = new UploadController(uploadService);
        MockMultipartFile file = new MockMultipartFile("file", "demo.png", "image/png", new byte[]{1});
        UploadResponseDTO payload = new UploadResponseDTO(
                "/images/demo.png",
                "demo.png",
                "/images/thumbs/demo.webp",
                "thumbs/demo.webp"
        );
        when(uploadService.storeImage(file)).thenReturn(payload);

        ApiResponse<UploadResponseDTO> response = controller.uploadImage(file);

        assertEquals("图片上传成功", response.message());
        assertEquals(payload, response.data());
    }
}
