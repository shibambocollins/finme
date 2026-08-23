package com.finme.backend.service;

import com.finme.backend.entity.User;
import com.finme.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sends the weekly spend analysis on a schedule (FR-1.8.2).
 * <p>
 * Two deliberate properties, both about a batch job's real failure modes:
 * <ul>
 *   <li><b>Per-user isolation.</b> Every user is wrapped individually, so one failure - a
 *       rate-limited AI call, a rejected address - costs that user their email and nobody
 *       else's. An unguarded loop would let the first failure cancel everyone after it, and the
 *       users affected would be whoever happened to sort late.</li>
 *   <li><b>It reports what it did.</b> A scheduled job nobody watches is indistinguishable from
 *       one that silently stopped running, so each pass logs sent, skipped and failed counts.</li>
 * </ul>
 * <p>
 * This assumes a single running instance. Two instances would each fire the schedule and every
 * user would get the email twice - if this is ever scaled out, the send needs a shared lock
 * (e.g. ShedLock) rather than a bare {@code @Scheduled}.
 */
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

    /**
     * Monday morning by default, configurable via {@code app.weekly-analysis.cron}. Setting that
     * property to "-" disables the job entirely, which is how it stays switched off in
     * development without commenting out code.
     */
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
                // Deliberately broad: a mail server rejection, a provider outage and a malformed
                // address all surface as different exception types, and none of them is a reason
                // to abandon the remaining users.
                failed++;
                log.warn("Weekly analysis failed for user {}: {}", user.getId(), ex.getMessage());
            }
        }

        log.info("Weekly analysis run complete: {} sent, {} skipped (no spending), {} failed",
                sent, skipped, failed);
    }
}
