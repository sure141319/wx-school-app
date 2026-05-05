package com.campustrade.platform.auth.dataobject;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EmailVerificationCodeDO {

    private Long id;
    private String email;
    private String code;
    private VerificationPurposeEnum purpose;
    private LocalDateTime expiresAt;
    private Boolean used = false;
    private LocalDateTime createdAt;
}
