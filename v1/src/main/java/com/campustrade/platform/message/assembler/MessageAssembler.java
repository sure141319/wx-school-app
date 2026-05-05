package com.campustrade.platform.message.assembler;

import com.campustrade.platform.message.dataobject.MessageDO;
import com.campustrade.platform.message.dto.response.MessageResponseDTO;
import com.campustrade.platform.user.assembler.UserProfileAssembler;
import org.springframework.stereotype.Component;

@Component
public class MessageAssembler {

    private final UserProfileAssembler userProfileAssembler;

    public MessageAssembler(UserProfileAssembler userProfileAssembler) {
        this.userProfileAssembler = userProfileAssembler;
    }

    public MessageResponseDTO toResponse(MessageDO message) {
        return new MessageResponseDTO(
                message.getId(),
                message.getConversation().getId(),
                userProfileAssembler.toResponse(message.getSender()),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}

