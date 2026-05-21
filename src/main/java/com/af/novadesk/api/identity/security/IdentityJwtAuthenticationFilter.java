package com.af.novadesk.api.identity.security;

import com.af.novadesk.api.identity.service.ShadowUserSyncService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that runs the shadow-user lazy upsert on every authenticated request.
 *
 * <p>Executes <em>after</em> Spring Security's JWT filter has validated the token
 * and populated the {@link SecurityContextHolder}. Extracts {@code sub},
 * {@code organizationId}, {@code email}, and optional {@code name} claims from
 * the already-verified {@link Jwt} principal and delegates to
 * {@link ShadowUserSyncService#upsert}.</p>
 *
 * <p>If the principal is not a JWT (unauthenticated request reaching a public
 * endpoint), the filter is a no-op.</p>
 *
 * <p>Sync failures are logged as warnings but do not block the request — the
 * JWT is still valid and the request proceeds. This prevents a transient DB
 * hiccup from taking down the entire API.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityJwtAuthenticationFilter extends OncePerRequestFilter {

    private final ShadowUserSyncService shadowUserSyncService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                UUID authUserId     = UUID.fromString(jwt.getSubject());
                String orgIdClaim   = jwt.getClaimAsString("organizationId");
                String email        = jwt.getClaimAsString("email");
                String displayName  = jwt.getClaimAsString("name");

                if (orgIdClaim != null && email != null) {
                    UUID organizationId = UUID.fromString(orgIdClaim);
                    shadowUserSyncService.upsert(authUserId, organizationId, email, displayName);
                } else {
                    log.warn("JWT missing 'organizationId' or 'email' claim — shadow sync skipped for sub={}",
                            jwt.getSubject());
                }

            } catch (Exception e) {
                // Non-fatal: log and continue. The JWT itself is still valid.
                log.warn("Shadow user sync failed for sub={}: {}", jwt.getSubject(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
