package com.af.novadesk.api.finance.security;

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
 * Servlet filter that runs after JWT validation on Finance module requests.
 *
 * <p>Performs two tasks:
 * <ol>
 *   <li><b>Shadow user sync</b> — lazy-upserts a {@code ShadowUser} record
 *       from the verified JWT claims ({@code sub}, {@code organizationId},
 *       {@code email}, {@code name}). Delegates to
 *       {@link ShadowUserSyncService#upsert} (same service the Identity
 *       module uses).</li>
 *   <li><b>Entity access check (reserved for future use)</b> — once
 *       endpoint-level route parameters are accessible, this filter will
 *       validate that the caller has an active {@code EntityUserAccess}
 *       record for the target legal entity.</li>
 * </ol>
 * </p>
 *
 * <p>If the principal is not a JWT (unauthenticated request hitting a public
 * Finance endpoint — if any are added), the filter is a no-op.</p>
 *
 * <p>Sync failures are logged as warnings but do not block the request — the
 * JWT is still valid and the request proceeds. This prevents a transient DB
 * hiccup from taking down the entire API.</p>
 *
 * <p>Registered in {@link FinanceSecurityConfig} at the appropriate position
 * within the Spring Security filter chain.</p>
 *
 * @see FinanceSecurityConfig
 * @see ShadowUserSyncService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceJwtAuthenticationFilter extends OncePerRequestFilter {

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
                log.warn("Finance shadow user sync failed for sub={}: {}", jwt.getSubject(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
