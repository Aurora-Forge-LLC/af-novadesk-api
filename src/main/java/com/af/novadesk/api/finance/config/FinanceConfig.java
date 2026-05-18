package com.af.novadesk.api.finance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Spring configuration for the finance sub-domain.
 *
 * <p>Exposes a {@link Clock} bean fixed to {@link ZoneOffset#UTC} so that
 * date-sensitive logic in services (e.g. {@code validateFundingDate}) evaluates
 * dates deterministically and is fully testable without time-of-day
 * dependencies (M2).</p>
 *
 * <p>Tests that need to control time simply inject a custom {@link Clock}
 * via constructor injection or Mockito instead of relying on the system clock.</p>
 */
@Configuration
public class FinanceConfig {

    /**
     * Application-wide UTC clock for the finance domain.
     * All date-based comparisons in the capital-injection workflow use this bean.
     */
    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}

