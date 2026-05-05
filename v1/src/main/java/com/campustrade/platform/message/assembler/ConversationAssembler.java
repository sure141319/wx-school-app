package com.campustrade.platform.message.assembler;

import com.campustrade.platform.message.dataobject.ConversationDO;
import com.campustrade.platform.message.dto.response.ConversationResponseDTO;
import com.campustrade.platform.user.assembler.UserProfileAssembler;
import org.springframework.stereotype.Component;

@Component
public class ConversationAssembler {

    private final UserProfileAssembler userProfileAssembler;

    public ConversationAssembler(UserProfileAssembler userProfileAssembler) {
        this.userProfileAssembler = userProfileAssembler;
    }

    public ConversationResponseDTO toResponse(ConversationDO conversation) {
        return new ConversationResponseDTO(
                conversation.getId(),
                conversation.getGoods().getId(),
                conversation.getGoods().getTitle(),
                conversation.getGoodsCoverImage(),
                userProfileAssembler.toResponse(conversation.getBuyer()),
                userProfileAssembler.toResponse(conversation.getSeller()),
                conversation.getLastMessageAt()
        );
    }
}
