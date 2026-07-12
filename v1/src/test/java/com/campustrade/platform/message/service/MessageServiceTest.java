package com.campustrade.platform.message.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.service.GoodsService;
import com.campustrade.platform.message.assembler.ConversationAssembler;
import com.campustrade.platform.message.assembler.MessageAssembler;
import com.campustrade.platform.message.mapper.ConversationMapper;
import com.campustrade.platform.message.mapper.MessageMapper;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    @Test
    void startConversationRejectsGoodsThatAreNotOnSale() {
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        GoodsService goodsService = mock(GoodsService.class);
        UserService userService = mock(UserService.class);
        MessageService service = new MessageService(
                conversationMapper,
                mock(MessageMapper.class),
                goodsService,
                userService,
                mock(ConversationAssembler.class),
                mock(MessageAssembler.class)
        );

        UserDO buyer = new UserDO();
        buyer.setId(12L);
        GoodsDO goods = new GoodsDO();
        goods.setId(5L);
        goods.setStatus(GoodsStatusEnum.PENDING_REVIEW);

        when(userService.getById(12L)).thenReturn(buyer);
        when(goodsService.getById(5L)).thenReturn(goods);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.startConversation(12L, 5L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(conversationMapper, never()).findByGoodsIdAndBuyerId(5L, 12L);
    }
}
