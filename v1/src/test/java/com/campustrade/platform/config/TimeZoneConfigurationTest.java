package com.campustrade.platform.config;

import com.campustrade.platform.common.time.BeijingTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class TimeZoneConfigurationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Test
    void applicationAndDatabaseSessionUseBeijingTime() {
        OffsetDateTime databaseNow = jdbcTemplate.queryForObject(
                "SELECT CURRENT_TIMESTAMP",
                OffsetDateTime.class
        );

        assertEquals(BeijingTime.ZONE_ID, TimeZone.getDefault().toZoneId());
        assertEquals(ZoneOffset.ofHours(8), databaseNow.getOffset());
        assertEquals("Asia/Shanghai", environment.getProperty("spring.jackson.time-zone"));
    }
}
