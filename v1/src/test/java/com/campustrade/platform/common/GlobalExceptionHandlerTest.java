package com.campustrade.platform.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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
        assertEquals("请检查填写内容", body.message());
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
}
