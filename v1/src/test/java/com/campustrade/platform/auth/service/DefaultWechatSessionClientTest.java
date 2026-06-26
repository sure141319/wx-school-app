package com.campustrade.platform.auth.service;

import com.campustrade.platform.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
