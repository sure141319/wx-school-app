package com.campustrade.platform.auth.dto.response;

import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;

public record AuthResponseDTO(
        String token,
        UserProfileResponseDTO user
) {
}

