package com.af.novadesk.api.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Master Security Configuration — the <strong>only</strong> {@code @EnableWebSecurity}
 * class in the entire application.
 *
 * <p>Provides the catch-all filter chain for infrastructure endpoints
 * (Swagger, Actuator, etc.). Module-specific filter chains are registered
 * as plain {@code @Bean} methods in each module's config class
 * (e.g. {@code IdentitySecurityConfig.globalSecurityFilterChain}) —
 * those do <strong>not</strong> need {@code @EnableWebSecurity}.</p>
 *
 * <h3>Modular Monolith Guidelines</h3>
 * <ul>
 *   <li>Each module defines its own {@code SecurityFilterChain} bean.</li>
 *   <li>Use {@code @Order} on module chains to control precedence.</li>
 *   <li>Module chains MUST use {@code http.securityMatcher()} to scope to their paths.</li>
 *   <li>This master config provides the catch-all (last chain).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class MasterSecurityConfig {

    // ── Infrastructure endpoints that must always be publicly accessible ─────
    private static final String[] PUBLIC_PATHS = {
            // OpenAPI / Swagger UI
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**",
            // Actuator — health probes (used by Docker, Traefik, and SBA)
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus",
            "/actuator/metrics",
            "/actuator/metrics/**",
    };

    /**
     * Catch-all filter chain — runs <strong>last</strong>.
     * Permits public infrastructure endpoints; requires authentication for
     * everything else.
     */
    @Bean
    @Order(Integer.MAX_VALUE)   // always evaluated last
    public SecurityFilterChain infrastructureFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(request -> true)   // explicit catch-all
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
