package com.campustrade.platform.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    @Test
    void defaultSuccessMessageUsesFriendlyChineseCopy() {
        ApiResponse<String> response = ApiResponse.ok("data");

        assertTrue(response.success());
        assertEquals(ApiResponseCode.OK, response.code());
        assertEquals("操作成功", response.message());
        assertEquals("data", response.data());
    }

    @Test
    void failureResponseCarriesStableCode() {
        ApiResponse<Void> response = ApiResponse.fail(
                ApiResponseCode.AUTH_TOKEN_EXPIRED,
                "登录已过期，请重新登录",
                null
        );

        assertEquals(ApiResponseCode.AUTH_TOKEN_EXPIRED, response.code());
        assertEquals("登录已过期，请重新登录", response.message());
    }
}
