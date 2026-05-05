package com.campustrade.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
        "com.campustrade.platform.audit.mapper",
        "com.campustrade.platform.user.mapper",
        "com.campustrade.platform.category.mapper",
        "com.campustrade.platform.goods.mapper",
        "com.campustrade.platform.message.mapper"
})
public class CampusTradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusTradeApplication.class, args);
    }
}

