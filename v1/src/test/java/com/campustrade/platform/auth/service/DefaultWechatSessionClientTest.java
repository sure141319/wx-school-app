package com.campustrade.platform.auth.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DefaultWechatSessionClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void exchangeParsesWechatTextPlainJsonResponse() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/jscode2session", exchange -> {
            byte[] body = """
                    {"openid":"openid-123","session_key":"session-123","unionid":"union-123"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        AppProperties properties = new AppProperties();
        properties.getWechat().setAppId("app-id");
        properties.getWechat().setAppSecret("app-secret");
        properties.getWechat().setCode2SessionUrl("http://localhost:" + server.getAddress().getPort() + "/jscode2session");

        WechatSession session = new DefaultWechatSessionClient(properties).exchange("wx-code");

        assertEquals("openid-123", session.openid());
        assertEquals("session-123", session.sessionKey());
        assertEquals("union-123", session.unionid());
    }

    @Test
    void exchangeReturnsSafeMessageWhenWechatRejectsCode() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/jscode2session", exchange -> {
            byte[] body = """
                    {"errcode":40029,"errmsg":"invalid code"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        AppProperties properties = configuredProperties();
        AppException exception = assertThrows(
                AppException.class,
                () -> new DefaultWechatSessionClient(properties).exchange("invalid-code")
        );

        assertEquals("微信登录服务暂时不可用，请稍后重试", exception.getMessage());
    }

    @Test
    void exchangeReturnsSafeMessageWhenWechatIsNotConfigured() {
        AppException exception = assertThrows(
                AppException.class,
                () -> new DefaultWechatSessionClient(new AppProperties()).exchange("wx-code")
        );

        assertEquals("微信登录服务暂时不可用，请稍后重试", exception.getMessage());
    }

    @Test
    void exchangeStopsWaitingWhenWechatResponseExceedsReadTimeout() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/jscode2session", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        AppProperties properties = configuredProperties();
        properties.getWechat().setConnectTimeoutMs(200);
        properties.getWechat().setReadTimeoutMs(50);

        AppException exception = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> assertThrows(
                        AppException.class,
                        () -> new DefaultWechatSessionClient(properties).exchange("wx-code")
                )
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("微信登录服务暂时不可用，请稍后重试", exception.getMessage());
    }

    private AppProperties configuredProperties() {
        AppProperties properties = new AppProperties();
        properties.getWechat().setAppId("app-id");
        properties.getWechat().setAppSecret("app-secret");
        properties.getWechat().setCode2SessionUrl(
                "http://localhost:" + server.getAddress().getPort() + "/jscode2session"
        );
        return properties;
    }
}
