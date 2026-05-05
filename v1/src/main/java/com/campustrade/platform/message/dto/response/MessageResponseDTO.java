package com.campustrade.platform.message.dto.response;

import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;
import java.time.LocalDateTime;

public record MessageResponseDTO(
        Long id,
        Long conversationId,
        UserProfileResponseDTO sender,
        String content,
        LocalDateTime createdAt
) {
}

