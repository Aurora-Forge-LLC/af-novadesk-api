package com.af.novadesk.api.payroll.config;

import com.af.novadesk.api.identity.security.IdentityJwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security configuration for the Payroll module.
 *
 * <p>Defines a {@link SecurityFilterChain} with {@code @Order(2)} that applies
 * <strong>only</strong> to requests matching {@code /api/v1/payroll/**}.
 * Payroll requests were previously handled by the catch-all infrastructure chain
 * ({@link com.af.novadesk.api.common.security.MasterSecurityConfig MasterSecurityConfig}),
 * which did not configure OAuth2 Resource Server JWT support — causing all payroll
 * requests to be rejected with 403 Forbidden.</p>
 *
 * <h3>Security controls</h3>
 * <ul>
 *   <li><b>JWT authentication</b> — validates the {@code aud} claim against
 *       {@code app.jwt.audience} (see {@code application.yml}) and enforces the
 *       issuer from {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.</li>
 *   <li><b>Stateless sessions</b> — Payroll endpoints do not maintain HTTP sessions.</li>
 *   <li><b>Shadow user sync</b> — the {@link IdentityJwtAuthenticationFilter} runs after
 *       JWT validation to lazily upsert {@code ShadowUser} records.</li>
 *   <li><b>CORS</b> — allows credentials and configurable allowed origins via
 *       {@code app.cors.allowed-origins} (defaults to {@code http://localhost:4200}).</li>
 *   <li><b>Method-level authorization</b> — endpoints use {@code @PreAuthorize} annotations
 *       (enabled globally via {@link com.af.novadesk.api.common.config.CommonModuleConfig}).</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class PayrollSecurityConfig {

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

    private final IdentityJwtAuthenticationFilter identityJwtAuthenticationFilter;

    /**
     * Payroll-specific security filter chain.
     *
     * <p>Ordered at {@code 2} so it takes precedence over the catch-all
     * infrastructure chain ({@code @Order(Integer.MAX_VALUE)}) but runs after
     * the Finance chain ({@code @Order(1)}).</p>
     *
     * @param http the {@link HttpSecurity} to configure
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    @Order(2)
    public SecurityFilterChain payrollSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/payroll/**")
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
                // IdentityJwtAuthenticationFilter runs AFTER JWT validation
                .addFilterAfter(identityJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(401);
                            response.getWriter().write(
                                    "{\"errorCode\":\"PAYROLL_AUTH_001\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(403);
                            response.getWriter().write(
                                    "{\"errorCode\":\"PAYROLL_AUTH_002\",\"message\":\"Access denied\"}");
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
     * <strong>not</strong> processed (the Payroll module authorises solely via
     * the {@code permissions} claim).</p>
     *
     * @return a configured {@link JwtAuthenticationConverter}
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new java.util.ArrayList<>();

            // Extract permissions (existing behavior)
            List<String> permissions = jwt.getClaim("permissions");
            if (permissions != null) {
                permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }

            // Extract roles and prefix with ROLE_ (matching authhub's authority mapping)
            List<String> roles = jwt.getClaim("roles");
            if (roles != null) {
                roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .forEach(authorities::add);
            }

            return authorities;
        });

        return converter;
    }

    /**
     * Creates a {@link CorsConfigurationSource} for the Payroll module.
     *
     * <p>Allows the origins defined in {@code app.cors.allowed-origins}
     * (comma-separated list in {@code application.yml}). Supports credentials
     * (cookies / {@code Authorization} header) and all standard HTTP methods
     * and headers.</p>
     */
    private CorsConfigurationSource corsConfigurationSource() {
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
        source.registerCorsConfiguration("/api/v1/payroll/**", configuration);
        return source;
    }

    /**
     * Creates a {@link org.springframework.security.oauth2.jwt.JwtDecoder} decorated with
     * audience claim validation.
     *
     * <p>Uses the auto-configured {@link JwtDecoder} (backed by
     * {@code spring.security.oauth2.resourceserver.jwt.*} properties from
     * {@code application.yml}) and wraps it with a custom
     * {@link OAuth2TokenValidator} that checks the {@code aud} claim matches
     * the configured audience.</p>
     *
     * <p>The {@code iss} claim is already validated by the default decoder
     * against {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.</p>
     *
     * @return a {@link org.springframework.security.oauth2.jwt.JwtDecoder} with audience validation
     */
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoderWithAudienceValidation() {
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtAudienceValidator(jwtAudience);

        OAuth2TokenValidator<Jwt> defaultValidators =
                JwtValidators.createDefaultWithIssuer(issuerUri);

        OAuth2TokenValidator<Jwt> delegatingValidator =
                new DelegatingOAuth2TokenValidator<>(List.of(defaultValidators, audienceValidator));

        if (jwtDecoder instanceof NimbusJwtDecoder nimbusDecoder) {
            nimbusDecoder.setJwtValidator(delegatingValidator);
            return nimbusDecoder;
        }

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
    private static class JwtDecoderDecorator implements org.springframework.security.oauth2.jwt.JwtDecoder {

        private final org.springframework.security.oauth2.jwt.JwtDecoder delegate;
        private final OAuth2TokenValidator<Jwt> validator;

        JwtDecoderDecorator(org.springframework.security.oauth2.jwt.JwtDecoder delegate,
                            OAuth2TokenValidator<Jwt> validator) {
            this.delegate = delegate;
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
