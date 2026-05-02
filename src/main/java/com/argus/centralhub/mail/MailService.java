package com.argus.centralhub.mail;

import com.argus.centralhub.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public MailService(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Async
    public void sendHtmlMail(String subject, String htmlContent) {
        if (mailProperties.getTo() == null || mailProperties.getTo().isBlank()) {
            log.warn("邮件接收者未配置，跳过邮件发送");
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, 
                    true, 
                    StandardCharsets.UTF_8.name()
            );
            
            helper.setFrom(mailProperties.getUsername());
            helper.setTo(mailProperties.getTo());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("邮件发送成功，接收者: {}", mailProperties.getTo());
            
        } catch (MessagingException e) {
            log.error("邮件发送失败", e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    @Async
    public void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, 
                    true, 
                    StandardCharsets.UTF_8.name()
            );
            
            helper.setFrom(mailProperties.getUsername());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("邮件发送成功，接收者: {}", to);
            
        } catch (MessagingException e) {
            log.error("邮件发送失败", e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }
}
