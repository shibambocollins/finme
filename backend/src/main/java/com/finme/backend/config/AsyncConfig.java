package com.finme.backend.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Pool for statement extraction. Small on purpose: every task in it spends most of its life
     * waiting on shared, rate-limited AI providers, so running more of them concurrently does
     * not extract anything faster - it just splits one 8000-tokens-per-minute budget more ways
     * and makes each upload hit the rate limiter more often.
     * <p>
     * The queue is bounded rather than unlimited so a burst of uploads is refused up front,
     * instead of being accepted and left sitting invisibly behind an hour of other work.
     */
    @Bean("statementExecutor")
    public TaskExecutor statementExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder
                .corePoolSize(2)
                .maxPoolSize(2)
                .queueCapacity(20)
                .threadNamePrefix("statement-")
                .build();
    }
}
