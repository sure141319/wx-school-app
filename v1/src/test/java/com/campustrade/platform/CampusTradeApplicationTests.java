package com.campustrade.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class CampusTradeApplicationTests {

    @Autowired
    private DataSource dataSource;

    @MockBean
    private JavaMailSender javaMailSender;

    @Test
    void contextLoads() {
    }

    @Test
    void legacyMessageTablesAreRemoved() throws SQLException {
        assertFalse(tableExists("MESSAGES"));
        assertFalse(tableExists("CONVERSATION_DO"));
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(
                     connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
