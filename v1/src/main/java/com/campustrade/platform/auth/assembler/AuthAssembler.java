package com.campustrade.platform.auth.assembler;

import com.campustrade.platform.auth.dto.response.AuthResponseDTO;
import com.campustrade.platform.user.assembler.UserProfileAssembler;
import com.campustrade.platform.user.dataobject.UserDO;
import org.springframework.stereotype.Component;

@Component
public class AuthAssembler {

    private final UserProfileAssembler userProfileAssembler;

    public AuthAssembler(UserProfileAssembler userProfileAssembler) {
        this.userProfileAssembler = userProfileAssembler;
    }

    public AuthResponseDTO toAuthResponse(String token, UserDO user) {
        return new AuthResponseDTO(token, userProfileAssembler.toResponse(user));
    }
}

