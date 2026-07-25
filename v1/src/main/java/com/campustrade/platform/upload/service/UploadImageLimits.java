package com.campustrade.platform.upload.service;

import com.campustrade.platform.common.AppException;
import org.springframework.http.HttpStatus;

final class UploadImageLimits {

    private static final int MAX_IMAGE_WIDTH = 10_000;
    private static final int MAX_IMAGE_HEIGHT = 10_000;
    private static final long MAX_IMAGE_PIXELS = 50_000_000L;

    private UploadImageLimits() {
    }

    static void validateDimensions(int width, int height) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0
                || width > MAX_IMAGE_WIDTH
                || height > MAX_IMAGE_HEIGHT
                || pixels > MAX_IMAGE_PIXELS) {
            throw new AppException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "图片分辨率过大，最大支持 10000×10000 且不超过 5000 万像素"
            );
        }
    }
}
