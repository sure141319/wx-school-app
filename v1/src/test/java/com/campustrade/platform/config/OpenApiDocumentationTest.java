package com.campustrade.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JavaMailSender javaMailSender;

    @Test
    void openApiJsonIsPublicAndUsesConfiguredPath() throws Exception {
        mockMvc.perform(get("/api/v1/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title", is("Campus Trade API")))
                .andExpect(jsonPath("$.components.schemas.GoodsDetailResponseDTO").exists())
                .andExpect(jsonPath("$.components.schemas.GoodsDetailResponseDTO.properties.imageKeys").exists())
                .andExpect(jsonPath("$.components.schemas.PublicGoodsResponseDTO").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/uploads/presign/batch']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/messages/conversations']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/messages/conversations/{conversationId}/messages']")
                        .doesNotExist());
    }

    @Test
    void swaggerUiEntryAssetsAndRemoteConfigArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/v1/swagger-ui/index.html"));

        mockMvc.perform(get("/api/v1/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/openapi.json/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", is("/api/v1/openapi.json")));
    }
}
