package com.finme.backend.service;

import com.finme.backend.entity.User;
import com.finme.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WeeklyAnalysisScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyAnalysisScheduler.class);

    private final UserRepository userRepository;
    private final WeeklySpendAnalysisService weeklySpendAnalysisService;
    private final EmailService emailService;

    public WeeklyAnalysisScheduler(UserRepository userRepository,
                                   WeeklySpendAnalysisService weeklySpendAnalysisService,
                                   EmailService emailService) {
        this.userRepository = userRepository;
        this.weeklySpendAnalysisService = weeklySpendAnalysisService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.weekly-analysis.cron:0 0 7 * * MON}", zone = "${app.weekly-analysis.zone:Africa/Johannesburg}")
    public void sendWeeklyAnalyses() {
        List<User> recipients = userRepository.findByEmailVerifiedTrue();
        int sent = 0;
        int skipped = 0;
        int failed = 0;

        for (User user : recipients) {
            try {
                var analysis = weeklySpendAnalysisService.composeFor(user.getId());
                if (analysis.isEmpty()) {
                    skipped++;
                    continue;
                }
                emailService.sendWeeklySpendAnalysis(
                        user.getEmail(), analysis.get().subject(), analysis.get().body());
                sent++;
            } catch (Exception ex) {
                failed++;
                log.warn("Weekly analysis failed for user {}: {}", user.getId(), ex.getMessage());
            }
        }

        log.info("Weekly analysis run complete: {} sent, {} skipped (no spending), {} failed",
                sent, skipped, failed);
    }
}
