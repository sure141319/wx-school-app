package com.campustrade.platform.auth.service;

public record WechatSession(
        String openid,
        String sessionKey,
        String unionid
) {
}
