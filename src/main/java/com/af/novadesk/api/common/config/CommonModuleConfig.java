package com.af.novadesk.api.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Optional;

/**
 * Shared Spring configuration for all bounded contexts (Finance, Payroll, etc.).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Enables JPA auditing so {@code @CreatedDate} / {@code @LastModifiedDate}
 *       on {@link com.af.novadesk.api.common.entity.AbstractEntity} are auto-populated
 *       across all modules.</li>
 *   <li>Enables Spring Method Security so that {@code @PreAuthorize} annotations
 *       on API interfaces (e.g. {@code PayrollBatchApi}) are enforced.</li>
 *   <li>Provides an {@link AuditorAware} implementation that resolves the current
 *       auditor from the JWT {@code sub} claim.</li>
 *   <li>Registers a correctly configured {@link ObjectMapper} for JSON payload
 *       serialisation in outbox services across all modules.</li>
 * </ul>
 *
 * <p><strong>Note:</strong> These annotations must only be declared once per
 * Spring context. Previously they were duplicated in FinanceModuleConfig and
 * PayrollModuleConfig, causing bean-definition conflicts.
 * </p>
 */
@Configuration
@EnableTransactionManagement
@EnableJpaAuditing(auditorAwareRef = "commonAuditorAware")
@EnableMethodSecurity
public class CommonModuleConfig {

    /**
     * Resolves the current auditor (user performing the action) from the
     * JWT {@code sub} claim stored in Spring Security's context.
     * Shared across all modules.
     */
    @Bean
    public AuditorAware<String> commonAuditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                return Optional.ofNullable(jwt.getSubject());
            }
            return Optional.of("system");
        };
    }

    /**
     * Shared {@link ObjectMapper} for outbox payload serialisation across all modules.
     * Configured with:
     * <ul>
     *   <li>{@link JavaTimeModule} — serialises {@code LocalDate} / {@code LocalDateTime}
     *       as ISO-8601 strings, not timestamp arrays.</li>
     *   <li>{@code WRITE_DATES_AS_TIMESTAMPS = false} — forces string representation.</li>
     * </ul>
     */
    @Bean
    public ObjectMapper commonObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
