package com.campustrade.platform.goods.service;

import com.campustrade.platform.category.service.CategoryService;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.config.cache.GoodsListCacheInvalidator;
import com.campustrade.platform.goods.assembler.GoodsAssembler;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dto.response.MyGoodsListItemResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.mapper.GoodsMapper;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.service.UserService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsServiceMyGoodsTest {

    @Test
    void returnsLeanItemsWithoutLoadingImages() {
        GoodsMapper goodsMapper = mock(GoodsMapper.class);
        GoodsAssembler goodsAssembler = mock(GoodsAssembler.class);
        UserService userService = mock(UserService.class);
        GoodsDO goods = new GoodsDO();
        goods.setId(10L);
        MyGoodsListItemResponseDTO item = new MyGoodsListItemResponseDTO(
                10L,
                "Desk lamp",
                BigDecimal.TEN,
                GoodsStatusEnum.ON_SALE,
                null
        );
        when(goodsMapper.findBySellerId(7L, 20, 0)).thenReturn(List.of(goods));
        when(goodsMapper.countBySellerId(7L)).thenReturn(1L);
        when(goodsAssembler.toMyGoodsListItemResponse(goods)).thenReturn(item);
        GoodsService service = new GoodsService(
                goodsMapper,
                mock(CategoryService.class),
                userService,
                goodsAssembler,
                mock(UploadService.class),
                new AppProperties(),
                mock(GoodsListCacheInvalidator.class)
        );

        var response = service.myGoods(7L, 0, 20);

        assertEquals(List.of(item), response.items());
        verify(userService).getById(7L);
        verify(goodsMapper, never()).findImagesByGoodsIds(List.of(10L));
    }
}
