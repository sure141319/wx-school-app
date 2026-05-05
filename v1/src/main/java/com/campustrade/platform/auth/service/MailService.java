package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;

public interface MailService {
    boolean sendVerificationCode(String email, String code, VerificationPurposeEnum purpose);
}

