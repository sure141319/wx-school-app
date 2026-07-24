package com.campustrade.platform.common;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final ApiResponseCode code;

    public AppException(HttpStatus status, String message) {
        this(status, ApiResponseCode.fromStatus(status), message, null);
    }

    public AppException(HttpStatus status, String message, Throwable cause) {
        this(status, ApiResponseCode.fromStatus(status), message, cause);
    }

    public AppException(HttpStatus status, ApiResponseCode code, String message) {
        this(status, code, message, null);
    }

    public AppException(HttpStatus status, ApiResponseCode code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ApiResponseCode getCode() {
        return code;
    }
}

