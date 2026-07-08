package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.config.AppProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpMailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final AppProperties appProperties = new AppProperties();
    private final SmtpMailService mailService = new SmtpMailService(mailSender, appProperties);

    @Test
    void sendVerificationCodeUsesBrandedRegisterSubjectAndHtmlContent() throws Exception {
        MimeMessage message = send(VerificationPurposeEnum.REGISTER, "882184");

        assertEquals("安工大二手闲置平台 - 注册验证码", message.getSubject());

        String html = message.getContent().toString();
        assertTrue(html.contains("安工大二手闲置平台"));
        assertTrue(html.contains("882184"));
        assertTrue(html.contains("注册账号"));
        assertTrue(html.contains("5 分钟"));
        assertTrue(html.contains("验证码仅用于本次操作，请勿转发或泄露给他人。"));
        assertFalse(html.contains("校园交易平台验证码"));
    }

    @Test
    void sendVerificationCodeUsesMiniProgramSenderDisplayName() throws Exception {
        MimeMessage message = send(VerificationPurposeEnum.REGISTER, "882184");

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertEquals("no-reply@campus-trade.local", from.getAddress());
        assertEquals("安工大二手闲置小程序", from.getPersonal());
    }

    @Test
    void sendVerificationCodeUsesPurposeSpecificResetPasswordCopy() throws Exception {
        MimeMessage message = send(VerificationPurposeEnum.RESET_PASSWORD, "135790");

        assertEquals("安工大二手闲置平台 - 重置密码验证码", message.getSubject());
        String html = message.getContent().toString();
        assertTrue(html.contains("重置密码"));
        assertTrue(html.contains("135790"));
    }

    @Test
    void sendVerificationCodeUsesPurposeSpecificBindEmailCopy() throws Exception {
        MimeMessage message = send(VerificationPurposeEnum.BIND_EMAIL, "246801");

        assertEquals("安工大二手闲置平台 - 绑定邮箱验证码", message.getSubject());
        String html = message.getContent().toString();
        assertTrue(html.contains("绑定 QQ 邮箱"));
        assertTrue(html.contains("246801"));
    }

    private MimeMessage send(VerificationPurposeEnum purpose, String code) throws Exception {
        appProperties.getVerificationCode().setExpireMinutes(5);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        assertTrue(mailService.sendVerificationCode("student@qq.com", code, purpose));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
