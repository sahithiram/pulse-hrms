package com.mentis.hrms.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class PasswordResetMailService {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetMailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public PasswordResetMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Async so the reply time does not reveal whether the account exists. Never logs the link. */
    @Async
    public void sendResetLink(String toEmail, String name, String link) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Reset your HRMS password");
            String safeName = HtmlUtils.htmlEscape(name == null ? "" : name);
            String html = """
                    <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;padding:20px;line-height:1.6">
                      <h2>Mentis IT Solutions</h2>
                      <p>Hello %s,</p>
                      <p>We received a request to reset your HRMS password. This link works for 60 minutes and can be used once:</p>
                      <p><a href="%s" style="background:#6366f1;color:#fff;padding:10px 18px;border-radius:6px;text-decoration:none">Reset password</a></p>
                      <p>If the button does not work, copy this address into your browser:<br>%s</p>
                      <p>If you did not ask for this, ignore this email. Your password stays the same.</p>
                    </div>
                    """.formatted(safeName, link, link);
            helper.setText(html, true);
            mailSender.send(message);
            logger.info("Password reset email sent");
        } catch (Exception e) {
            logger.error("Failed to send password reset email: {}", e.getMessage());
        }
    }
}