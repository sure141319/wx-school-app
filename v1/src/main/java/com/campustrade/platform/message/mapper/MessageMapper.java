package com.campustrade.platform.message.mapper;

import com.campustrade.platform.message.dataobject.MessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {

    int insert(MessageDO message);

    MessageDO findById(@Param("id") Long id);

    List<MessageDO> listByConversationId(@Param("conversationId") Long conversationId,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    long countByConversationId(@Param("conversationId") Long conversationId);
}
