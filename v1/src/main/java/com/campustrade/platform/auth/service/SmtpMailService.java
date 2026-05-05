package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class SmtpMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);
    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public SmtpMailService(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    @Override
    public boolean sendVerificationCode(String email, String code, VerificationPurposeEnum purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(resolveFromAddress());
            helper.setTo(email);
            helper.setSubject(buildSubject(purpose));
            helper.setText(buildHtmlContent(code, purpose), true);
            mailSender.send(message);
            log.info("Verification mail sent successfully to {} for purpose {}", email, purpose);
            return true;
        } catch (MessagingException | RuntimeException ex) {
            log.error("Failed to send mail to {}: {}", email, ex.getMessage(), ex);
            return false;
        }
    }

    private String resolveFromAddress() {
        String from = appProperties.getMail().getFrom();
        if (from == null || from.isBlank()) {
            return appProperties.getMail().getUsername();
        }
        return from;
    }

    private String buildSubject(VerificationPurposeEnum purpose) {
        return switch (purpose) {
            case REGISTER -> "校园交易平台 - 注册验证码";
            case RESET_PASSWORD -> "校园交易平台 - 重置密码验证码";
        };
    }

    private String buildHtmlContent(String code, VerificationPurposeEnum purpose) {
        String purposeText = switch (purpose) {
            case REGISTER -> "注册账号";
            case RESET_PASSWORD -> "重置密码";
        };
        int expireMinutes = appProperties.getVerificationCode().getExpireMinutes();
        return """
                <div style="font-family: Arial, Helvetica, sans-serif; line-height: 1.6; color: #222;">
                  <h2 style="margin-bottom: 12px;">校园交易平台验证码</h2>
                  <p>你正在进行 <strong>%s</strong> 操作。</p>
                  <p>本次验证码为：</p>
                  <div style="display:inline-block;padding:12px 20px;font-size:28px;font-weight:700;letter-spacing:6px;background:#f5f7fa;border-radius:8px;color:#1f2937;">
                    %s
                  </div>
                  <p style="margin-top:16px;">验证码 <strong>%d 分钟</strong> 内有效，请勿泄露给他人。</p>
                  <p style="color:#666;font-size:12px;">如果这不是你的操作，请忽略此邮件。</p>
                </div>
                """.formatted(purposeText, code, expireMinutes);
    }
}
