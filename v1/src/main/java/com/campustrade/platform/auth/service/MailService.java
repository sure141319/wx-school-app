package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;

import java.math.BigDecimal;

public interface MailService {
    boolean sendVerificationCode(String email, String code, VerificationPurposeEnum purpose);

    boolean sendGoodsContactNotification(String sellerEmail,
                                         String buyerEmail,
                                         String buyerNickname,
                                         String goodsTitle,
                                         BigDecimal goodsPrice);
}

