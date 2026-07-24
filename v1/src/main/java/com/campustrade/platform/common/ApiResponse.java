package com.campustrade.platform.common;

public record ApiResponse<T>(boolean success, ApiResponseCode code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ApiResponseCode.OK, "操作成功", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, ApiResponseCode.OK, message, data);
    }

    public static <T> ApiResponse<T> fail(String message, T data) {
        return fail(ApiResponseCode.OPERATION_FAILED, message, data);
    }

    public static <T> ApiResponse<T> fail(ApiResponseCode code, String message, T data) {
        return new ApiResponse<>(false, code, message, data);
    }
}

