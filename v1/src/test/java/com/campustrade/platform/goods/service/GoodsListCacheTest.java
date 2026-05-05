package com.campustrade.platform.goods.service;

import com.campustrade.platform.audit.dataobject.AuditImageRecordDO;
import com.campustrade.platform.audit.dto.response.AuditImageResponseDTO;
import com.campustrade.platform.audit.mapper.AuditImageMapper;
import com.campustrade.platform.audit.service.AuditImageService;
import com.campustrade.platform.category.service.CategoryService;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.config.cache.CacheConfig;
import com.campustrade.platform.goods.assembler.GoodsAssembler;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.dto.response.GoodsResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.goods.mapper.GoodsMapper;
import com.campustrade.platform.message.mapper.ConversationMapper;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = GoodsListCacheTest.TestConfig.class)
@TestPropertySource(properties = {
        "app.jwt-secret=test-secret-that-is-definitely-long-enough-and-not-base64!!!",
        "app.redis.required=false",
        "app.image-audit.reviewer-user-ids=1"
})
class GoodsListCacheTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    @Import({CacheConfig.class, GoodsService.class, AuditImageService.class})
    static class TestConfig {
    }

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private AuditImageService auditImageService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private GoodsMapper goodsMapper;

    @MockBean
    private com.campustrade.platform.user.mapper.UserMapper userMapper;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private UserService userService;

    @MockBean
    private ConversationMapper conversationMapper;

    @MockBean
    private GoodsAssembler goodsAssembler;

    @MockBean
    private AuditImageMapper auditImageMapper;

    @MockBean
    private UploadService uploadService;

    private GoodsResponseDTO cachedResponse;

    @BeforeEach
    void setUp() {
        cachedResponse = new GoodsResponseDTO(
                1L,
                "MacBook Air",
                "Lightly used",
                BigDecimal.valueOf(4999),
                "9成新",
                "Main Campus",
                GoodsStatusEnum.ON_SALE,
                null,
                null,
                List.of("/static/auditing.png"),
                        List.of("images/2026/04/auditing.png"),
                        null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    void listUsesCacheForSameQuery() {
        GoodsDO goods = new GoodsDO();
        goods.setId(1L);
        goods.setTitle("MacBook Air");

        when(goodsMapper.search(eq("mac"), eq(2L), eq(GoodsStatusEnum.ON_SALE), eq(10), eq(0)))
                .thenReturn(List.of(goods));
        when(goodsMapper.countSearch("mac", 2L, GoodsStatusEnum.ON_SALE)).thenReturn(1L);
        when(goodsMapper.findImagesByGoodsIds(anyList())).thenReturn(List.of());
        when(goodsAssembler.toResponse(goods)).thenReturn(cachedResponse);

        PageResponse<GoodsResponseDTO> first = goodsService.list("  mac  ", 2L, GoodsStatusEnum.ON_SALE, 0, 10);
        PageResponse<GoodsResponseDTO> second = goodsService.list("mac", 2L, GoodsStatusEnum.ON_SALE, 0, 10);

        assertSame(first, second);
        verify(goodsMapper, times(1)).search("mac", 2L, GoodsStatusEnum.ON_SALE, 10, 0);
        verify(goodsMapper, times(1)).countSearch("mac", 2L, GoodsStatusEnum.ON_SALE);
        verify(goodsMapper, times(1)).findImagesByGoodsIds(anyList());
        verify(goodsAssembler, times(1)).toResponse(goods);
    }

    @Test
    void approveEvictsGoodsListCache() {
        Cache cache = cacheManager.getCache("goods:list");
        assertNotNull(cache);
        cache.put("manual-key", PageResponse.of(List.of(cachedResponse), 1, 0, 10));

        GoodsImageDO image = new GoodsImageDO();
        image.setId(3L);

        AuditImageRecordDO updatedRecord = new AuditImageRecordDO();
        updatedRecord.setImageId(3L);
        updatedRecord.setGoodsId(1L);
        updatedRecord.setGoodsTitle("MacBook Air");
        updatedRecord.setSellerId(2L);
        updatedRecord.setSellerNickname("seller");
        updatedRecord.setImageUrl("images/approved.jpg");
        updatedRecord.setSortOrder(0);
        updatedRecord.setAuditStatus(ImageAuditStatusEnum.APPROVED);
        updatedRecord.setAuditedBy(1L);
        updatedRecord.setAuditedAt(LocalDateTime.now());
        updatedRecord.setCreatedAt(LocalDateTime.now());

        when(goodsMapper.findImageById(3L)).thenReturn(image);
        when(auditImageMapper.findByImageId(3L)).thenReturn(updatedRecord);
        when(uploadService.getProxyUrl("images/approved.jpg")).thenReturn("http://localhost/images/approved.jpg");

        AuditImageResponseDTO response = auditImageService.approve(1L, 3L);

        assertNotNull(response);
        assertNull(cache.get("manual-key"));
        verify(goodsMapper).updateImageAudit(3L, ImageAuditStatusEnum.APPROVED, null, 1L);
    }
}
