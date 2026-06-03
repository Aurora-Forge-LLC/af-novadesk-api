package com.af.novadesk.api.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Master Security Configuration — the <strong>only</strong> {@code @EnableWebSecurity}
 * class in the entire application.
 *
 * <p>Provides the catch-all filter chain for infrastructure endpoints
 * (Swagger, Actuator, etc.) and global CORS configuration for all paths
 * not covered by module-specific filter chains (e.g. {@code /api/v1/payroll/**}).</p>
 *
 * <h3>CORS</h3>
 * <ul>
 *   <li>Allows credentials and configurable allowed origins via
 *       {@code app.cors.allowed-origins} (defaults to {@code *}).</li>
 *   <li>When set to {@code *}, credentials are disabled per the CORS specification.</li>
 * </ul>
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

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * Catch-all filter chain — runs <strong>last</strong>.
     * Permits public infrastructure endpoints; requires authentication for
     * everything else. Applies CORS configuration for all paths not covered
     * by module-specific security chains (e.g. payroll, identity).
     */
    @Bean
    @Order(Integer.MAX_VALUE)   // always evaluated last
    public SecurityFilterChain infrastructureFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(request -> true)   // explicit catch-all
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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

    /**
     * Creates a global {@link org.springframework.web.cors.CorsConfigurationSource}
     * for all paths not covered by module-specific security chains.
     *
     * <p>Allows the origins defined in {@code app.cors.allowed-origins}
     * (comma-separated list in {@code application.yml}). Supports credentials
     * and all standard HTTP methods and headers.</p>
     */
    private UrlBasedCorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();

        boolean allowAll = "*".equals(allowedOrigins);
        if (allowAll) {
            configuration.addAllowedOriginPattern("*");
        } else {
            configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(!allowAll);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
