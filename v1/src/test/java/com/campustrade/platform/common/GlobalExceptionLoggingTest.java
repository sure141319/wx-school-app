package com.campustrade.platform.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionLoggingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void notFoundWithCauseLogsAtDebugWithoutStackTrace() {
        handler.handleAppException(new AppException(
                HttpStatus.NOT_FOUND,
                "图片不存在",
                new IllegalStateException("missing object")
        ));

        ILoggingEvent event = onlyEvent();
        assertEquals(Level.DEBUG, event.getLevel());
        assertNull(event.getThrowableProxy());
    }

    @Test
    void serverFailureLogsAtErrorWithStackTrace() {
        handler.handleAppException(new AppException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "对象存储暂时不可用",
                new IllegalStateException("minio down")
        ));

        ILoggingEvent event = onlyEvent();
        assertEquals(Level.ERROR, event.getLevel());
        assertNotNull(event.getThrowableProxy());
    }

    private ILoggingEvent onlyEvent() {
        assertEquals(1, appender.list.size());
        return appender.list.get(0);
    }
}
