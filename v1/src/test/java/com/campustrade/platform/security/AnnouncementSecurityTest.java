package com.campustrade.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnnouncementSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void currentAnnouncementIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/announcements/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void adminAnnouncementReadRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/audit/announcement"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void adminAnnouncementUpdateRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/audit/announcement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "通知",
                                  "content": "测试公告",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_REQUIRED"));
    }

    @Test
    void authenticatedNonReviewerIsForbidden() throws Exception {
        var nonReviewer = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(999L, "student@qq.com"),
                null,
                Collections.emptyList()
        );

        mockMvc.perform(get("/api/v1/audit/announcement")
                        .with(authentication(nonReviewer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("无权管理公告"));
    }
}
