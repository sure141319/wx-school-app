package com.campustrade.platform.goods.mapper;

import com.campustrade.platform.common.time.BeijingTime;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dto.request.GoodsSaveRequestDTO;
import com.campustrade.platform.goods.dto.response.GoodsResponseDTO;
import com.campustrade.platform.goods.service.GoodsService;
import com.campustrade.platform.upload.service.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class GoodsTimestampTimezoneTest {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private UploadService uploadService;

    @Test
    void createdAtUsesCurrentBeijingTimeWhenGoodsIsInserted() {
        Long sellerId = insertSeller();
        String imageKey = "images/2026/07/goods/goods_u" + sellerId + "_20260712120000_a1b2c3.jpg";
        String thumbnailKey = "images/2026/07/goods/thumbs/goods_u" + sellerId + "_20260712120000_a1b2c3_thumb.jpg";
        when(uploadService.validateUploadedImageReference(imageKey, "goods", sellerId)).thenReturn(imageKey);
        when(uploadService.bindUploadedImageToGoods(eq(imageKey), eq(sellerId), anyLong()))
                .thenReturn(new UploadService.ImageVariantKeys(thumbnailKey, null, null));

        LocalDateTime beforeBeijingNow = BeijingTime.now().minusSeconds(2);
        GoodsResponseDTO created = goodsService.create(sellerId, new GoodsSaveRequestDTO(
                "北京时间测试商品",
                "用于验证商品发布时间时区",
                BigDecimal.valueOf(12.34),
                "全新",
                "校内",
                null,
                List.of(imageKey),
                List.of(thumbnailKey)
        ));
        GoodsDO saved = goodsMapper.findById(created.id());
        LocalDateTime afterBeijingNow = BeijingTime.now().plusSeconds(2);

        assertNotNull(saved);
        assertNotNull(saved.getCreatedAt());
        assertFalse(
                saved.getCreatedAt().isBefore(beforeBeijingNow),
                "商品 createdAt 不应早于插入前的北京时间窗口: " + saved.getCreatedAt()
        );
        assertFalse(
                saved.getCreatedAt().isAfter(afterBeijingNow),
                "商品 createdAt 不应晚于插入后的北京时间窗口: " + saved.getCreatedAt()
        );
    }

    private Long insertSeller() {
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, nickname, failed_login_count, created_at, updated_at)
                VALUES (?, ?, ?, 0, NOW(), NOW())
                """, "goods-timezone-test@qq.com", "hash", "测试卖家");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                "goods-timezone-test@qq.com"
        );
    }
}
