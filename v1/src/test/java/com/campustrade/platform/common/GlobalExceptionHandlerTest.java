package com.campustrade.platform.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationFailureReturnsStableCodeAndFieldDetails() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "title", "商品标题不能为空"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(exception);
        ApiResponse<Map<String, String>> body = response.getBody();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(body.success());
        assertEquals(ApiResponseCode.VALIDATION_FAILED, body.code());
        assertEquals("商品标题不能为空", body.message());
        assertEquals(Map.of("title", "商品标题不能为空"), body.data());
    }

    @Test
    void appExceptionKeepsSpecificMessageAndMapsStatusToCode() {
        AppException exception = new AppException(HttpStatus.NOT_FOUND, "商品不存在或已下架");

        var response = handler.handleAppException(exception);
        ApiResponse<Map<String, String>> body = response.getBody();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(body.success());
        assertEquals(ApiResponseCode.RESOURCE_NOT_FOUND, body.code());
        assertEquals("商品不存在或已下架", body.message());
    }

    @Test
    void internalAppExceptionHidesImplementationDetails() {
        AppException exception = new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "MinIO 存储桶不存在");

        var response = handler.handleAppException(exception);
        ApiResponse<Map<String, String>> body = response.getBody();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(body.success());
        assertEquals(ApiResponseCode.INTERNAL_ERROR, body.code());
        assertEquals("服务器开小差了，请稍后重试", body.message());
    }

    @Test
    void typeMismatchReturnsReadableBadRequestMessage() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc",
                Integer.class,
                "page",
                null,
                new NumberFormatException("abc")
        );

        var response = handler.handleTypeMismatch(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ApiResponseCode.REQUEST_INVALID, response.getBody().code());
        assertEquals("页码格式错误", response.getBody().message());
    }

    @Test
    void missingUploadParametersReturnActionableMessages() {
        var missingObjectKey = handler.handleMissingParameter(
                new MissingServletRequestParameterException("objectKey", "String")
        );
        var missingFile = handler.handleMissingRequestPart(
                new MissingServletRequestPartException("file")
        );

        assertEquals("图片标识不能为空", missingObjectKey.getBody().message());
        assertEquals("请选择要上传的图片", missingFile.getBody().message());
    }
}
