package com.campustrade.platform.auth.service;

public interface WechatSessionClient {

    WechatSession exchange(String code);
}
