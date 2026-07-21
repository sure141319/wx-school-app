package com.campustrade.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void adminAnnouncementReadRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/audit/announcement"))
                .andExpect(status().isForbidden());
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
                .andExpect(status().isForbidden());
    }
}
