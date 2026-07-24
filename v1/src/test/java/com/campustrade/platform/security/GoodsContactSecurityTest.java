package com.campustrade.platform.security;

import com.campustrade.platform.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GoodsContactSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private AppProperties appProperties;

    @Test
    void contactEmailEligibilityIsNotCoveredByPublicGoodsGetRule() throws Exception {
        mockMvc.perform(get("/api/v1/goods/1/contact-email-eligibility"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void invalidBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/goods/1/contact-email-eligibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("登录状态无效，请重新登录"));
    }

    @Test
    void expiredBearerTokenReturnsUnauthorized() throws Exception {
        int originalExpirationMinutes = appProperties.getJwtExpirationMinutes();
        String expiredToken;
        try {
            appProperties.setJwtExpirationMinutes(-1);
            expiredToken = tokenProvider.createToken(1L, "student@qq.com");
        } finally {
            appProperties.setJwtExpirationMinutes(originalExpirationMinutes);
        }

        mockMvc.perform(get("/api/v1/goods/1/contact-email-eligibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("登录已过期，请重新登录"));
    }
}
