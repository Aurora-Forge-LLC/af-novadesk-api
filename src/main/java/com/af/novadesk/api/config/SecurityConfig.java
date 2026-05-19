package com.af.novadesk.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Security configuration for af-novadesk-api.
 *
 * Spring Security is present on the classpath via the af-spring-parent BOM.
 * Without this config, Boot's auto-configuration would lock every endpoint
 * behind form-login — redirecting Swagger UI and Actuator to /login.
 *
 * Current posture: stateless REST API, no authentication mechanism yet.
 * Infrastructure/observability endpoints are explicitly permitted.
 * All other requests are permitted for now — tighten this when JWT auth
 * is introduced (replace the final anyRequest().permitAll() with
 * anyRequest().authenticated() and add the JWT filter chain).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ── Infrastructure endpoints that must always be publicly accessible ─────
    private static final String[] PUBLIC_PATHS = {
            // OpenAPI / Swagger UI
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**",
            // Actuator — health probes (used by Docker, Traefik, and Spring Boot Admin)
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus",
            "/actuator/metrics",
            "/actuator/metrics/**",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless REST API — no session, no CSRF token needed
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)

                // Disable Spring Boot's default form-login and HTTP Basic pop-up
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                .authorizeHttpRequests(auth -> auth
                        // Always permit infrastructure / observability endpoints
                        .requestMatchers(PUBLIC_PATHS).permitAll()

                        .anyRequest().authenticated()
                );

        return http.build();
    }
}

