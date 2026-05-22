package com.af.novadesk.api.finance.security;

import com.af.novadesk.api.finance.exception.JwtClaimMissingException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Typed accessor for the Finance module's per-request security context.
 *
 * <p>Extracts the verified JWT claims stored in Spring Security's
 * {@link SecurityContextHolder} after the JWT filter has run. Services
 * call this instead of coupling directly to Spring Security APIs,
 * making them easier to test (inject a mock context).</p>
 *
 * <p>Claims read: {@code sub} → authUserId, {@code organizationId}, {@code email}.</p>
 */
@Component
public class FinanceSecurityContext {

    /**
     * The {@code sub} claim — stable AuthHub user UUID.
     *
     * @throws JwtClaimMissingException if the claim is missing or not a valid UUID
     */
    public UUID getAuthUserId() {
        try {
            return UUID.fromString(jwt().getSubject());
        } catch (IllegalArgumentException e) {
            throw new JwtClaimMissingException("sub");
        }
    }

    /**
     * The {@code organizationId} claim — used to scope all Finance queries.
     *
     * @throws JwtClaimMissingException if the claim is missing
     */
    public UUID getOrganizationId() {
        String orgId = jwt().getClaimAsString("organizationId");
        if (orgId == null) {
            throw new JwtClaimMissingException("organizationId");
        }
        return UUID.fromString(orgId);
    }

    /**
     * The {@code email} claim — used for shadow user upsert.
     */
    public String getEmail() {
        return jwt().getClaimAsString("email");
    }

    /**
     * Optional display name from the JWT (first name + last, or username).
     * Returns null if the claim is absent.
     */
    public String getDisplayName() {
        return jwt().getClaimAsString("name");
    }

    // -------------------------------------------------------------------------

    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException(
                    "No authenticated JWT principal found in SecurityContext");
        }
        return jwt;
    }
}
