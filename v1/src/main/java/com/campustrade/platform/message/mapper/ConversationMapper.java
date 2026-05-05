package com.campustrade.platform.message.mapper;

import com.campustrade.platform.message.dataobject.ConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ConversationMapper {

    ConversationDO findById(@Param("id") Long id);

    ConversationDO findByGoodsIdAndBuyerId(@Param("goodsId") Long goodsId, @Param("buyerId") Long buyerId);

    int insert(ConversationDO conversation);

    int updateLastMessageAt(@Param("id") Long id, @Param("lastMessageAt") LocalDateTime lastMessageAt);

    int deleteByGoodsId(@Param("goodsId") Long goodsId);

    List<ConversationDO> listByBuyerOrSeller(@Param("userId") Long userId,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    long countByBuyerOrSeller(@Param("userId") Long userId);

    List<Map<String, Object>> findCoverImagesByGoodsIds(@Param("goodsIds") List<Long> goodsIds);
}
