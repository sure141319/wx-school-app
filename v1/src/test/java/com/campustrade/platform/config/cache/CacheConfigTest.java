package com.campustrade.platform.config.cache;

import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.goods.dto.response.GoodsListItemResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CacheConfigTest {

    @Test
    void redisSerializerKeepsPageResponseType() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

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
}
