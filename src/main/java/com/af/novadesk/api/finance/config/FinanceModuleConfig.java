package com.af.novadesk.api.finance.config;



import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Optional;

/**
 * Module-level Spring configuration for the Finance bounded context.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Enables JPA auditing so {@code @CreatedDate} / {@code @LastModifiedDate}
 *       on {@code AbstractEntity} are auto-populated.</li>
 *   <li>Provides an {@link AuditorAware} implementation that resolves the current
 *       auditor from the JWT {@code sub} claim.</li>
 *   <li>Registers a correctly configured {@link ObjectMapper} for JSON payload
 *       serialisation in the outbox services.</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableTransactionManagement
@EnableJpaAuditing(auditorAwareRef = "financeAuditorAware")
public class FinanceModuleConfig {

    /**
     * Resolves the current auditor (user performing the action) from the
     * JWT {@code sub} claim stored in Spring Security's context.
     * Used by {@code @CreatedBy} / {@code @LastModifiedBy} if added in future.
     */
    @Bean
    public AuditorAware<String> financeAuditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                return Optional.ofNullable(jwt.getSubject());
            }
            return Optional.of("system");
        };
    }

    /**
     * Shared {@link ObjectMapper} for outbox payload serialisation.
     * Configured with:
     * <ul>
     *   <li>{@link JavaTimeModule} — serialises {@code LocalDate} / {@code LocalDateTime}
     *       as ISO-8601 strings, not timestamp arrays.</li>
     *   <li>{@code WRITE_DATES_AS_TIMESTAMPS = false} — forces string representation.</li>
     * </ul>
     */
    @Bean
    public ObjectMapper financeObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}