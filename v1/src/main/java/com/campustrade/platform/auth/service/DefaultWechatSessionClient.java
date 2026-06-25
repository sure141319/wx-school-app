package com.campustrade.platform.auth.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DefaultWechatSessionClient implements WechatSessionClient {

    private final AppProperties appProperties;
    private final RestClient restClient;

    public DefaultWechatSessionClient(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.restClient = RestClient.create();
    }

    @Override
    public WechatSession exchange(String code) {
        AppProperties.Wechat wechat = appProperties.getWechat();
        if (!StringUtils.hasText(wechat.getAppId()) || !StringUtils.hasText(wechat.getAppSecret())) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录暂未配置");
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(wechat.getCode2SessionUrl())
                .queryParam("appid", wechat.getAppId().trim())
                .queryParam("secret", wechat.getAppSecret().trim())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        WechatCode2SessionResponse response;
        try {
            response = restClient.get().uri(url).retrieve().body(WechatCode2SessionResponse.class);
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "微信登录服务暂时不可用", ex);
        }

        if (response == null) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "微信登录服务返回为空");
        }
        if (response.errcode() != null && response.errcode() != 0) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "微信登录失败: " + response.errmsg());
        }
        if (!StringUtils.hasText(response.openid())) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "微信登录未返回 OpenID");
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
