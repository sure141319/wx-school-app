package com.campustrade.platform.common;

import com.campustrade.platform.auth.dto.request.LoginRequestDTO;
import com.campustrade.platform.goods.dto.request.GoodsSaveRequestDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidationMessageTest {

    @Test
    void loginValidationUsesFriendlyChineseMessages() {
        Set<String> messages = validate(new LoginRequestDTO("", "1"));

        assertTrue(messages.contains("QQ邮箱不能为空"));
        assertTrue(messages.contains("密码需为6-64位"));
    }

    @Test
    void goodsValidationUsesActionableMessages() {
        GoodsSaveRequestDTO request = new GoodsSaveRequestDTO(
                "",
                "",
                BigDecimal.ZERO,
                "",
                "",
                null,
                List.of(),
                null
        );

        Set<String> messages = validate(request);

        assertTrue(messages.contains("商品标题不能为空"));
        assertTrue(messages.contains("商品描述不能为空"));
        assertTrue(messages.contains("商品价格必须大于0"));
        assertTrue(messages.contains("商品成色不能为空"));
        assertTrue(messages.contains("交易地点不能为空"));
        assertTrue(messages.contains("请至少上传一张商品图片"));
    }

    private Set<String> validate(Object request) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(request).stream()
                    .map(violation -> violation.getMessage())
                    .collect(Collectors.toSet());
        }
    }
}
