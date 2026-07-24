package com.campustrade.platform.common;

import org.springframework.http.HttpStatus;

public enum ApiResponseCode {
    OK,
    OPERATION_FAILED,
    REQUEST_INVALID,
    VALIDATION_FAILED,
    REQUEST_BODY_INVALID,
    AUTHENTICATION_FAILED,
    AUTH_LOGIN_REQUIRED,
    AUTH_TOKEN_EXPIRED,
    AUTH_TOKEN_INVALID,
    AUTH_ACCESS_DENIED,
    RESOURCE_NOT_FOUND,
    RESOURCE_CONFLICT,
    UPLOAD_TOO_LARGE,
    UPLOAD_REQUEST_INVALID,
    UPLOAD_TYPE_NOT_ALLOWED,
    ACCOUNT_LOCKED,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    UPSTREAM_SERVICE_ERROR,
    INTERNAL_ERROR;

    public static ApiResponseCode fromStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> REQUEST_INVALID;
            case UNAUTHORIZED -> AUTHENTICATION_FAILED;
            case FORBIDDEN -> AUTH_ACCESS_DENIED;
            case NOT_FOUND -> RESOURCE_NOT_FOUND;
            case CONFLICT -> RESOURCE_CONFLICT;
            case PAYLOAD_TOO_LARGE -> UPLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> UPLOAD_TYPE_NOT_ALLOWED;
            case LOCKED -> ACCOUNT_LOCKED;
            case TOO_MANY_REQUESTS -> RATE_LIMITED;
            case BAD_GATEWAY -> UPSTREAM_SERVICE_ERROR;
            case SERVICE_UNAVAILABLE -> SERVICE_UNAVAILABLE;
            case INTERNAL_SERVER_ERROR -> INTERNAL_ERROR;
            default -> status.is5xxServerError() ? INTERNAL_ERROR : OPERATION_FAILED;
        };
    }
}
