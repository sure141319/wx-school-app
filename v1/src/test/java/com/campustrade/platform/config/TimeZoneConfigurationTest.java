package com.campustrade.platform.config;

import com.campustrade.platform.common.time.BeijingTime;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

    @Autowired
    private HikariConfig hikariConfig;

    @Autowired
    private HikariDataSource dataSource;

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

    @Test
    void hikariPropertiesAreBoundBeforeThePoolStarts() {
        assertEquals("SET TIME ZONE '+08:00'", hikariConfig.getConnectionInitSql());
        assertEquals(hikariConfig.getConnectionInitSql(), dataSource.getConnectionInitSql());
        assertEquals(hikariConfig.getJdbcUrl(), dataSource.getJdbcUrl());
    }
}
