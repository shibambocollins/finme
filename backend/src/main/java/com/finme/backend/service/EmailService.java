package com.finme.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around JavaMailSender (auto-configured once spring.mail.* is set - Brevo's SMTP
 * relay, see docs/07-tech-stack.md). Plain text throughout - at two message types there is
 * still no reason for templating machinery.
 * <p>
 * fromAddress reads app.mail.from-address, deliberately not spring.mail.username: the latter is
 * the SMTP login credential, and a provider's verified "From:" sender is not guaranteed to be
 * the same value - conflating them worked by coincidence with Gmail, where they usually are.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String backendBaseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from-address}") String fromAddress,
            @Value("${app.backend-base-url}") String backendBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.backendBaseUrl = backendBaseUrl;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verificationLink = backendBaseUrl + "/api/auth/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Verify your FinMe account");
        message.setText(
                "Welcome to FinMe!\n\n"
                        + "Click the link below to verify your email and activate your account:\n\n"
                        + verificationLink
                        + "\n\nThis link expires in 24 hours. If you didn't create a FinMe account, "
                        + "you can ignore this email.");

        mailSender.send(message);
    }

    /**
     * Sends the weekly spend analysis (FR-1.8.2). Subject and body arrive already composed by
     * WeeklySpendAnalysisService - this class stays a transport, with no opinion about content.
     */
    public void sendWeeklySpendAnalysis(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}
