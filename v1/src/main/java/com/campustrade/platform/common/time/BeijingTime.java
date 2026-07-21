package com.campustrade.platform.common.time;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

public final class BeijingTime {

    public static final String ZONE_ID_VALUE = "Asia/Shanghai";
    public static final ZoneId ZONE_ID = ZoneId.of(ZONE_ID_VALUE);

    private BeijingTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE_ID);
    }

    public static void configureJvmDefault() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE_ID));
    }
}
