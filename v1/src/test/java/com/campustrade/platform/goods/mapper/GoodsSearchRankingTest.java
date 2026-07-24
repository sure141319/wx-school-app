package com.campustrade.platform.goods.mapper;

import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class GoodsSearchRankingTest {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void titleMatchesRankBeforeDescriptionMatchesAndSameRankUsesNewestFirst() {
        Long sellerId = insertSeller();
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 1, 12, 0);
        insertGoods(sellerId, "旧版高等数学教材", "公共课教材", baseTime);
        insertGoods(sellerId, "新版高数教材", "几乎全新", baseTime.plusDays(1));
        insertGoods(sellerId, "公共课教材合集", "包含高数教材", baseTime.plusDays(2));

        List<GoodsDO> result = goodsMapper.searchList(
                List.of("高数教材", "高等数学教材"),
                null,
                GoodsStatusEnum.ON_SALE,
                10,
                0
        );

        assertEquals(
                List.of("新版高数教材", "旧版高等数学教材", "公共课教材合集"),
                result.stream().map(GoodsDO::getTitle).toList()
        );
    }

    private Long insertSeller() {
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, nickname, failed_login_count, created_at, updated_at)
                VALUES (?, ?, ?, 0, NOW(), NOW())
                """, "goods-search-ranking@qq.com", "hash", "搜索测试卖家");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                "goods-search-ranking@qq.com"
        );
    }

    private void insertGoods(Long sellerId, String title, String description, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO goods_do (
                    seller_id, category_id, title, description, price, condition_level,
                    campus_location, status, created_at, updated_at
                )
                VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sellerId,
                title,
                description,
                BigDecimal.TEN,
                "九成新",
                "校内",
                GoodsStatusEnum.ON_SALE.name(),
                createdAt,
                createdAt
        );
    }
}
