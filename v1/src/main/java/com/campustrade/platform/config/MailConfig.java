package com.campustrade.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(AppProperties appProperties) {
        AppProperties.Mail mail = appProperties.getMail();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.getHost());
        sender.setPort(mail.getPort());
        sender.setUsername(mail.getUsername());
        sender.setPassword(mail.getPassword());
        sender.setProtocol("smtp");
        sender.setDefaultEncoding("UTF-8");

        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(mail.isAuth()));
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(mail.isSslEnabled()));
        properties.setProperty("mail.smtp.ssl.required", "false");
        if (mail.isSslEnabled()) {
            properties.setProperty("mail.smtp.ssl.trust", mail.getHost());
        }
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(mail.isStarttlsEnabled()));
        properties.setProperty("mail.debug", Boolean.toString(mail.isDebug()));
        return sender;
    }
}
