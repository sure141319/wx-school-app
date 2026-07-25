package com.campustrade.platform.auth.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DefaultWechatSessionClient implements WechatSessionClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultWechatSessionClient.class);
    private static final String WECHAT_LOGIN_UNAVAILABLE_MESSAGE = "微信登录服务暂时不可用，请稍后重试";

    private final AppProperties appProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DefaultWechatSessionClient(AppProperties appProperties) {
        this.appProperties = appProperties;
        AppProperties.Wechat wechat = appProperties.getWechat();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(wechat.getConnectTimeoutMs());
        requestFactory.setReadTimeout(wechat.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public WechatSession exchange(String code) {
        AppProperties.Wechat wechat = appProperties.getWechat();
        if (!StringUtils.hasText(wechat.getAppId()) || !StringUtils.hasText(wechat.getAppSecret())) {
            log.error("Wechat login is not configured");
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, WECHAT_LOGIN_UNAVAILABLE_MESSAGE);
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(wechat.getCode2SessionUrl())
                .queryParam("appid", wechat.getAppId().trim())
                .queryParam("secret", wechat.getAppSecret().trim())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        String responseBody;
        try {
            responseBody = restClient.get().uri(url).retrieve().body(String.class);
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, WECHAT_LOGIN_UNAVAILABLE_MESSAGE, ex);
        }

        if (!StringUtils.hasText(responseBody)) {
            log.warn("Wechat code2session returned an empty response");
            throw new AppException(HttpStatus.BAD_GATEWAY, WECHAT_LOGIN_UNAVAILABLE_MESSAGE);
        }

        WechatCode2SessionResponse response;
        try {
            response = objectMapper.readValue(responseBody, WechatCode2SessionResponse.class);
        } catch (JsonProcessingException ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, WECHAT_LOGIN_UNAVAILABLE_MESSAGE, ex);
        }

        if (response == null) {
            log.warn("Wechat code2session response was null");
            throw new AppException(HttpStatus.BAD_GATEWAY, WECHAT_LOGIN_UNAVAILABLE_MESSAGE);
        }
        if (response.errcode() != null && response.errcode() != 0) {
            log.warn("Wechat code2session failed: errcode={}, errmsg={}", response.errcode(), response.errmsg());
            throw new AppException(HttpStatus.BAD_GATEWAY, WECHAT_LOGIN_UNAVAILABLE_MESSAGE);
        }
        if (!StringUtils.hasText(response.openid())) {
            log.warn("Wechat code2session response did not contain openid");
            throw new AppException(HttpStatus.BAD_GATEWAY, WECHAT_LOGIN_UNAVAILABLE_MESSAGE);
        }
        return new WechatSession(response.openid(), response.sessionKey(), response.unionid());
    }

    private record WechatCode2SessionResponse(
            String openid,
            @JsonProperty("session_key") String sessionKey,
            String unionid,
            Integer errcode,
            String errmsg
    ) {
    }
}
