package com.campustrade.platform.message.dataobject;

import com.campustrade.platform.user.dataobject.UserDO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MessageDO {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;

    private ConversationDO conversation;
    private UserDO sender;
}
