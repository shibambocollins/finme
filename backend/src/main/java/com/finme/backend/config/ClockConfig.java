package com.finme.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /**
     * The application's clock, injected rather than read via LocalDate.now() at call sites.
     * <p>
     * Manual entry resolves relative dates - "yesterday", "last Friday" - against today, so
     * "today" is an input to that logic and needs to be substitutable in tests. Calling
     * LocalDate.now() directly would leave those tests either untestable or quietly dependent
     * on when the suite happens to run.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
