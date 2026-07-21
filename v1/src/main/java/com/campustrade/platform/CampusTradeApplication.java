package com.campustrade.platform;

import com.campustrade.platform.common.time.BeijingTime;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({
        "com.campustrade.platform.announcement.mapper",
        "com.campustrade.platform.audit.mapper",
        "com.campustrade.platform.user.mapper",
        "com.campustrade.platform.category.mapper",
        "com.campustrade.platform.goods.mapper",
        "com.campustrade.platform.upload.mapper"
})
public class CampusTradeApplication {

    static {
        BeijingTime.configureJvmDefault();
    }

    public static void main(String[] args) {
        SpringApplication.run(CampusTradeApplication.class, args);
    }
}

