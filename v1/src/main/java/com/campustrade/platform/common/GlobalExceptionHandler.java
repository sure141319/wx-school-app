package com.campustrade.platform.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR_MESSAGE = "服务器开小差了，请稍后重试";

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleAppException(AppException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Application error: status={}, message={}", ex.getStatus(), ex.getMessage(), ex);
        } else if (ex.getStatus() == HttpStatus.NOT_FOUND) {
            log.debug("Application error: status={}, message={}", ex.getStatus(), ex.getMessage());
        } else {
            log.warn("Application error: status={}, message={}", ex.getStatus(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.fail(ex.getCode(), publicMessage(ex), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        ApiResponseCode.VALIDATION_FAILED,
                        firstValidationMessage(errors),
                        errors
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraint(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath().toString();
            int lastSeparator = propertyPath.lastIndexOf('.');
            String field = lastSeparator >= 0 ? propertyPath.substring(lastSeparator + 1) : propertyPath;
            errors.putIfAbsent(field, violation.getMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        ApiResponseCode.VALIDATION_FAILED,
                        firstValidationMessage(errors),
                        errors
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ApiResponseCode.REQUEST_BODY_INVALID, "请求参数格式错误", null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = readableParameterName(ex.getName()) + "格式错误";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ApiResponseCode.REQUEST_INVALID, message, null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = switch (ex.getParameterName()) {
            case "objectKey" -> "图片标识不能为空";
            default -> "缺少必填参数：" + ex.getParameterName();
        };
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ApiResponseCode.REQUEST_INVALID, message, null));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(MissingServletRequestPartException ex) {
        String message = "file".equals(ex.getRequestPartName())
                ? "请选择要上传的图片"
                : "缺少必填上传内容：" + ex.getRequestPartName();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ApiResponseCode.UPLOAD_REQUEST_INVALID, message, null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.fail(ApiResponseCode.REQUEST_INVALID, "请求格式不支持", null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail(ApiResponseCode.UPLOAD_TOO_LARGE, "上传图片不能超过 10MB", null));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException ex) {
        log.warn("Multipart request error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        ApiResponseCode.UPLOAD_REQUEST_INVALID,
                        "图片上传请求无效，请检查文件大小和格式",
                        null
                ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ApiResponseCode.AUTH_LOGIN_REQUIRED, "请先登录", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ApiResponseCode.AUTH_ACCESS_DENIED, "无权进行此操作", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ApiResponseCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE, null));
    }

    private String publicMessage(AppException ex) {
        if (ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
            return INTERNAL_ERROR_MESSAGE;
        }
        return ex.getMessage();
    }

    private String firstValidationMessage(Map<String, String> errors) {
        return errors.values().stream()
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse("请检查填写内容");
    }

    private String readableParameterName(String parameterName) {
        return switch (parameterName) {
            case "page" -> "页码";
            case "size" -> "每页数量";
            case "status" -> "状态参数";
            case "categoryId" -> "商品分类参数";
            case "id" -> "商品编号";
            case "imageId" -> "图片编号";
            case "userId" -> "用户编号";
            default -> "请求参数“" + parameterName + "”";
        };
    }
}
