package com.finme.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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

    public void sendWeeklySpendAnalysis(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}
