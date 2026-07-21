package com.campustrade.platform.common.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BeijingTimeTest {

    @Test
    void nowDoesNotDependOnJvmDefaultTimeZone() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime before = LocalDateTime.now(BeijingTime.ZONE_ID).minusSeconds(1);
            LocalDateTime actual = BeijingTime.now();
            LocalDateTime after = LocalDateTime.now(BeijingTime.ZONE_ID).plusSeconds(1);

            assertFalse(actual.isBefore(before));
            assertFalse(actual.isAfter(after));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void configureJvmDefaultUsesAsiaShanghai() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            BeijingTime.configureJvmDefault();

            assertEquals(ZoneId.of("Asia/Shanghai"), TimeZone.getDefault().toZoneId());
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
