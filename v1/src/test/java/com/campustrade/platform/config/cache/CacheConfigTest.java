package com.campustrade.platform.config.cache;

import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.goods.dto.response.GoodsListItemResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheConfigTest {

    @Test
    void redisSerializerKeepsPageResponseType() {
        GenericJackson2JsonRedisSerializer serializer = CacheConfig.redisValueSerializer();

        PageResponse<GoodsListItemResponseDTO> original = PageResponse.of(
                List.of(new GoodsListItemResponseDTO(
                        1L,
                        "MacBook Air",
                        BigDecimal.valueOf(4999),
                        "9成新",
                        "Main Campus",
                        GoodsStatusEnum.ON_SALE,
                        null,
                        null,
                        "/static/auditing.png",
                        LocalDateTime.of(2026, 4, 2, 18, 0)
                )),
                1,
                0,
                10
        );

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertInstanceOf(PageResponse.class, restored);
        PageResponse<?> response = (PageResponse<?>) restored;
        assertEquals(1, response.total());
        assertEquals(1, response.items().size());
        assertInstanceOf(GoodsListItemResponseDTO.class, response.items().get(0));
    }

    @Test
    void redisSerializerRejectsTypesOutsideCacheAllowlist() {
        GenericJackson2JsonRedisSerializer serializer = CacheConfig.redisValueSerializer();
        byte[] payload = """
                {"@class":"java.io.File","path":"application.yml"}
                """.getBytes(StandardCharsets.UTF_8);

        assertThrows(SerializationException.class, () -> serializer.deserialize(payload));
    }
}
