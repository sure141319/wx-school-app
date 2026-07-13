package com.campustrade.platform.auth.service;

import com.campustrade.platform.auth.enums.VerificationPurposeEnum;
import com.campustrade.platform.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Service
public class SmtpMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);
    private static final String PLATFORM_NAME = "安工大二手闲置平台";
    private static final String SENDER_DISPLAY_NAME = "安工大二手闲置小程序";
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
            helper.setFrom(new InternetAddress(resolveFromAddress(), SENDER_DISPLAY_NAME, StandardCharsets.UTF_8.name()));
            helper.setTo(email);
            helper.setSubject(buildSubject(purpose));
            helper.setText(buildHtmlContent(code, purpose), true);
            mailSender.send(message);
            log.info("Verification mail sent successfully to {} for purpose {}", email, purpose);
            return true;
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException ex) {
            log.error("Failed to send mail to {}: {}", email, ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public boolean sendGoodsContactNotification(String sellerEmail,
                                                String buyerEmail,
                                                String buyerNickname,
                                                String goodsTitle,
                                                BigDecimal goodsPrice) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(resolveFromAddress(), SENDER_DISPLAY_NAME, StandardCharsets.UTF_8.name()));
            helper.setTo(sellerEmail);
            helper.setReplyTo(buyerEmail);
            helper.setSubject(PLATFORM_NAME + " - 有买家想联系你");
            helper.setText(buildGoodsContactHtml(buyerEmail, buyerNickname, goodsTitle, goodsPrice), true);
            mailSender.send(message);
            log.info("Goods contact notification sent to seller for goods {}", sanitizeLogValue(goodsTitle));
            return true;
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException ex) {
            log.error("Failed to send goods contact notification: {}", ex.getMessage(), ex);
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
        String action = switch (purpose) {
            case REGISTER -> "注册验证码";
            case RESET_PASSWORD -> "重置密码验证码";
            case BIND_EMAIL -> "绑定邮箱验证码";
        };
        return PLATFORM_NAME + " - " + action;
    }

    private String buildHtmlContent(String code, VerificationPurposeEnum purpose) {
        String purposeText = switch (purpose) {
            case REGISTER -> "注册账号";
            case RESET_PASSWORD -> "重置密码";
            case BIND_EMAIL -> "绑定 QQ 邮箱";
        };
        int expireMinutes = appProperties.getVerificationCode().getExpireMinutes();
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f3f6f4;font-family:Arial,'Microsoft YaHei','PingFang SC',sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background:#f3f6f4;padding:32px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:600px;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                          <tr>
                            <td style="padding:0;background:#16a34a;height:6px;line-height:6px;font-size:0;">&nbsp;</td>
                          </tr>
                          <tr>
                            <td style="padding:28px 32px 8px 32px;">
                              <div style="font-size:14px;font-weight:700;letter-spacing:0;color:#16a34a;">%s</div>
                              <h1 style="margin:12px 0 0 0;font-size:24px;line-height:1.35;font-weight:700;color:#111827;">邮箱验证码</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 32px 0 32px;">
                              <p style="margin:0;font-size:16px;line-height:1.7;color:#374151;">
                                你正在进行 <span style="display:inline-block;padding:2px 8px;border-radius:999px;background:#eaf8ef;color:#15803d;font-weight:700;">%s</span> 操作。
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px 32px 8px 32px;">
                              <div style="font-size:14px;line-height:1.6;color:#6b7280;margin-bottom:10px;">本次验证码为</div>
                              <div style="display:inline-block;min-width:260px;text-align:center;padding:18px 24px;font-family:'Courier New',Consolas,monospace;font-size:34px;line-height:1.2;font-weight:700;letter-spacing:8px;background:#f8fafc;border:1px solid #e5e7eb;border-radius:10px;color:#111827;">
                                %s
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px 0 32px;">
                              <p style="margin:0;font-size:15px;line-height:1.7;color:#374151;">
                                验证码 <strong style="color:#111827;">%d 分钟</strong> 内有效，请尽快完成验证。
                              </p>
                              <p style="margin:10px 0 0 0;font-size:14px;line-height:1.7;color:#6b7280;">
                                验证码仅用于本次操作，请勿转发或泄露给他人。
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px 32px 28px 32px;">
                              <div style="padding:14px 16px;background:#f9fafb;border:1px solid #edf0f3;border-radius:8px;font-size:13px;line-height:1.7;color:#6b7280;">
                                如果不是你本人操作，请忽略此邮件。此邮件由 %s 自动发送，请勿直接回复。
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(PLATFORM_NAME, purposeText, code, expireMinutes, PLATFORM_NAME);
    }

    private String buildGoodsContactHtml(String buyerEmail,
                                         String buyerNickname,
                                         String goodsTitle,
                                         BigDecimal goodsPrice) {
        String safeBuyerEmail = HtmlUtils.htmlEscape(buyerEmail);
        String safeBuyerNickname = HtmlUtils.htmlEscape(buyerNickname);
        String safeGoodsTitle = HtmlUtils.htmlEscape(goodsTitle);
        String safeGoodsPrice = HtmlUtils.htmlEscape(goodsPrice == null ? "面议" : "¥" + goodsPrice.stripTrailingZeros().toPlainString());
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f3f6f4;font-family:Arial,'Microsoft YaHei','PingFang SC',sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background:#f3f6f4;padding:32px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:600px;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                          <tr><td style="height:6px;background:#2e9669;font-size:0;line-height:6px;">&nbsp;</td></tr>
                          <tr>
                            <td style="padding:28px 32px 8px;">
                              <div style="font-size:14px;font-weight:700;color:#2e9669;">%s</div>
                              <h1 style="margin:12px 0 0;font-size:24px;line-height:1.35;color:#111827;">有买家想联系你</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:12px 32px 0;font-size:15px;line-height:1.8;color:#374151;">
                              你发布的商品收到了一条购买意向，买家希望通过邮箱与你联系。
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:22px 32px 0;">
                              <div style="padding:18px 20px;background:#f8faf9;border:1px solid #e5ebe8;border-radius:10px;font-size:15px;line-height:1.9;color:#374151;">
                                <div><strong style="color:#111827;">商品：</strong>%s</div>
                                <div><strong style="color:#111827;">价格：</strong>%s</div>
                                <div><strong style="color:#111827;">买家：</strong>%s</div>
                                <div><strong style="color:#111827;">联系邮箱：</strong>%s</div>
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 32px 28px;font-size:14px;line-height:1.8;color:#6b7280;">
                              直接回复本邮件即可联系买家。交易时请优先选择校内公共场所，并注意核对商品与付款信息。
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(PLATFORM_NAME, safeGoodsTitle, safeGoodsPrice, safeBuyerNickname, safeBuyerEmail);
    }

    private String sanitizeLogValue(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
