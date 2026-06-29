package com.af.novadesk.api.finance.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security configuration for the Finance module.
 *
 * <p>Defines a {@link SecurityFilterChain} with {@code @Order(1)} that applies
 * <strong>only</strong> to requests matching {@code /api/v1/finance/**} and
 * {@code /api/v1/legal-entities/**}. The latter prefix is used by the
 * {@link com.af.novadesk.api.finance.controller.LegalEntityController
 * LegalEntityController}, which belongs to the Finance module. All other
 * requests fall through to the catch-all infrastructure chain defined in
 * {@link com.af.novadesk.api.common.security.MasterSecurityConfig MasterSecurityConfig}.</p>
 *
 * <h3>Security controls</h3>
 * <ul>
 *   <li><b>JWT authentication</b> — validates the {@code aud} claim against
 *       {@code app.jwt.audience} (see {@code application.yml}) and enforces the
 *       issuer from {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.</li>
 *   <li><b>Stateless sessions</b> — Finance endpoints do not maintain HTTP sessions.</li>
 *   <li><b>Shadow user sync</b> — a {@link FinanceJwtAuthenticationFilter} runs after
 *       JWT validation to lazily upsert {@code ShadowUser} records.</li>
 *   <li><b>CORS</b> — allows credentials and configurable allowed origins via
 *       {@code app.cors.allowed-origins} (defaults to {@code http://localhost:4200}).</li>
 * </ul>
 *
 * <h3>Module isolation rationale</h3>
 * <p>Using a module-specific security filter chain instead of a global
 * {@code .anyRequest().authenticated()} in the master config allows the Finance
 * module to evolve its auth rules independently. Future additions — such as
 * per-endpoint permission checks or entity-scoped access validation — are
 * contained within this class without touching the infrastructure-level chain.</p>
 *
 * @see FinanceJwtAuthenticationFilter
 * @see com.af.novadesk.api.common.security.MasterSecurityConfig
 */
@Configuration
@RequiredArgsConstructor
public class FinanceSecurityConfig {

    /**
     * The expected audience value, injected from {@code app.jwt.audience}.
     * Matching the {@code aud} claim in the JWT prevents token replay attacks
     * where a token issued for another service is presented to NovaDesk API.
     */
    @Value("${app.jwt.audience}")
    private String jwtAudience;

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    private final JwtDecoder jwtDecoder;

    /**
     * Finance-specific security filter chain.
     *
     * <p>Ordered at {@code 1} so it takes precedence over the catch-all
     * infrastructure chain (which typically has {@code @Order} not set,
     * defaulting to {@code Integer.MAX_VALUE}).</p>
     *
     * <p>Covers both {@code /api/v1/finance/**} and {@code /api/v1/legal-entities/**}
     * — the latter is the URL prefix for {@link com.af.novadesk.api.finance.controller.LegalEntityController
     * LegalEntityController}, which resides in the Finance module.</p>
     *
     * @param http the {@link HttpSecurity} to configure
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    @Order(1)
    public SecurityFilterChain financeSecurityFilterChain(HttpSecurity http,
                                                          FinanceJwtAuthenticationFilter financeJwtFilter) throws Exception {
        http
                .securityMatcher("/api/v1/finance/**", "/api/v1/legal-entities/**",
                        "/api/v1/expense/**", "/api/v1/assets/**",
                        "/api/v1/employees/**", "/api/v1/payroll/**",
                        "/api/v1/bank-reconciliation/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoderWithAudienceValidation())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                // FinanceJwtAuthenticationFilter runs AFTER JWT validation
                // (UsernamePasswordAuthenticationFilter is the standard position
                // for post-auth filters in a JWT-only setup).
                .addFilterAfter(financeJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(401);
                            response.getWriter().write(
                                    "{\"errorCode\":\"FIN_AUTH_001\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(403);
                            response.getWriter().write(
                                    "{\"errorCode\":\"FIN_AUTH_002\",\"message\":\"Access denied\"}");
                        })
                );

        return http.build();
    }

    /**
     * Custom {@link JwtAuthenticationConverter} that maps the
     * {@code permissions} claim from the AuthHub JWT directly to
     * Spring Security {@link GrantedAuthority} objects.
     *
     * <p>AuthHub JWTs carry a {@code permissions} claim — an array of
     * permission strings, e.g. {@code ["organizations:write", "organizations:read", ...]}.
     * This converter extracts those strings as-is (no {@code ROLE_} or
     * {@code SCOPE_} prefix), so that {@code @PreAuthorize("hasAuthority('organizations:write')")}
     * works out of the box.</p>
     *
     * <p>If the JWT has a {@code scope} or {@code scp} claim, those are
     * <strong>not</strong> processed (the Finance module authorises solely via
     * the {@code permissions} claim).</p>
     *
     * @return a configured {@link JwtAuthenticationConverter}
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> permissions = jwt.getClaim("permissions");

            if (permissions == null) {
                return List.of();
            }

            return permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });

        return converter;
    }

    /**
     * Creates a {@link CorsConfigurationSource} for the Finance module.
     *
     * <p>Allows the origins defined in {@code app.cors.allowed-origins}
     * (comma-separated list in {@code application.yml}). Supports credentials
     * (cookies / {@code Authorization} header) and all standard HTTP methods
     * and headers.</p>
     */
    private CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();

        // When set to "*", Spring CORS interprets this as "allow all origins"
        // per the CORS specification. In that case, we must NOT set
        // allowCredentials(true) because the spec forbids combining
        // Access-Control-Allow-Origin: * with Access-Control-Allow-Credentials: true.
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
        // Register specific API paths
        source.registerCorsConfiguration("/api/v1/finance/**", configuration);
        source.registerCorsConfiguration("/api/v1/legal-entities/**", configuration);
        source.registerCorsConfiguration("/api/v1/expense/**", configuration);
        source.registerCorsConfiguration("/api/v1/assets/**", configuration);
        source.registerCorsConfiguration("/api/v1/employees/**", configuration);
        source.registerCorsConfiguration("/api/v1/payroll/**", configuration);
        source.registerCorsConfiguration("/api/v1/bank-reconciliation/**", configuration);
        // Register catch-all to handle requests with context-path prefix
        // (e.g. /novadesk-api/api/v1/legal-entities/...).
        // Without this, UrlBasedCorsConfigurationSource cannot match the
        // request URI because it includes the context-path that the
        // specific patterns above lack.
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Creates a {@link JwtDecoder} decorated with audience claim validation.
     *
     * <p>Uses the auto-configured {@link JwtDecoder} (backed by
     * {@code spring.security.oauth2.resourceserver.jwt.*} properties from
     * {@code application.yml}) and wraps it with a custom
     * {@link OAuth2TokenValidator} that checks the {@code aud} claim matches
     * {@link #jwtAudience}.</p>
     *
     * <p>The {@code iss} claim is already validated by the default decoder
     * against {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.</p>
     *
     * @return a {@link JwtDecoder} with audience validation
     */
    private JwtDecoder jwtDecoderWithAudienceValidation() {
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtAudienceValidator(jwtAudience);

        OAuth2TokenValidator<Jwt> defaultValidators =
                JwtValidators.createDefaultWithIssuer(issuerUri);

        OAuth2TokenValidator<Jwt> delegatingValidator =
                new DelegatingOAuth2TokenValidator<>(List.of(defaultValidators, audienceValidator));

        if (jwtDecoder instanceof NimbusJwtDecoder nimbusDecoder) {
            nimbusDecoder.setJwtValidator(delegatingValidator);
            return nimbusDecoder;
        }

        // Fallback: wrap the decoder with a validator-aware decorator.
        // This path is unlikely but keeps the code robust.
        return new JwtDecoderDecorator(jwtDecoder, delegatingValidator);
    }

    // =========================================================================
    // Inner helpers
    // =========================================================================

    /**
     * Validates that the JWT's {@code aud} claim contains the expected audience.
     *
     * <p>The {@code aud} claim in an AuthHub JWT is an array of strings.
     * This validator checks if the configured audience value is present
     * in that array.</p>
     */
    private static class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

        private final String expectedAudience;

        JwtAudienceValidator(String expectedAudience) {
            this.expectedAudience = expectedAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> audience = jwt.getAudience();
            if (audience != null && audience.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "The required audience '" + expectedAudience + "' is missing",
                            null
                    )
            );
        }
    }

    /**
     * Decorator that applies a custom {@link OAuth2TokenValidator} to tokens
     * decoded by the injected {@link JwtDecoder}. Used as a fallback when the
     * decoder is not a {@link NimbusJwtDecoder}.
     */
    private static class JwtDecoderDecorator implements JwtDecoder {

        private final JwtDecoder               delegate;
        private final OAuth2TokenValidator<Jwt> validator;

        JwtDecoderDecorator(JwtDecoder delegate, OAuth2TokenValidator<Jwt> validator) {
            this.delegate  = delegate;
            this.validator = validator;
        }

        @Override
        public Jwt decode(String token) throws JwtException {
            Jwt jwt = delegate.decode(token);
            OAuth2TokenValidatorResult result = validator.validate(jwt);
            if (result.hasErrors()) {
                throw new JwtValidationException(
                        "JWT validation failed", result.getErrors());
            }
            return jwt;
        }
    }
}
