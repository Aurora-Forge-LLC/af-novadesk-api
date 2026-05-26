package com.af.novadesk.api.organization.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security configuration for the Organization module.
 *
 * <p>Defines a {@link SecurityFilterChain} with {@code @Order(2)} that applies
 * <strong>only</strong> to requests matching {@code /api/v1/organization/**}.
 * Requests that do not match this pattern fall through to lower-order chains.</p>
 *
 * <h3>Security controls</h3>
 * <ul>
 *   <li><b>JWT authentication</b> — validates JWT and maps the {@code permissions}
 *       claim to Spring Security {@code GrantedAuthority} objects.</li>
 *   <li><b>Stateless sessions</b> — no HTTP sessions maintained.</li>
 *   <li><b>Endpoint-level authorization</b> — enforced via {@code @PreAuthorize}
 *       annotations on the API interface (e.g. {@code hasAuthority('Super_admin')}).</li>
 * </ul>
 */
@Configuration
public class OrganizationSecurityConfig {

    /**
     * Organization-specific security filter chain.
     *
     * <p>Ordered at {@code 2} so the Finance module (order 1) takes precedence
     * for its paths, and this chain handles organization paths before falling
     * through to the catch-all infrastructure chain.</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain organizationSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/organization/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * Custom {@link JwtAuthenticationConverter} that maps the
     * {@code permissions} claim from the AuthHub JWT directly to
     * Spring Security {@code GrantedAuthority} objects.
     *
     * <p>AuthHub JWTs carry a {@code permissions} claim — an array of
     * permission strings, e.g. {@code ["Super_admin", "organizations:read", ...]}.
     * This converter extracts those strings as-is so that
     * {@code @PreAuthorize("hasAuthority('Super_admin')")} works out of the box.</p>
     */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            java.util.Collection<org.springframework.security.core.GrantedAuthority> authorities =
                    new java.util.ArrayList<>();

            // Extract permissions claim (e.g. "organizations:write", "roles:read")
            List<String> permissions = jwt.getClaim("permissions");
            if (permissions != null) {
                permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(a -> authorities.add(a));
            }

            // Extract roles claim (e.g. "SUPER_ADMIN") so that
            // @PreAuthorize("hasAuthority('SUPER_ADMIN')") works.
            List<String> roles = jwt.getClaim("roles");
            if (roles != null) {
                roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(a -> authorities.add(a));
            }

            return authorities;
        });
        return converter;
    }
}
