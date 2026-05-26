package com.af.novadesk.api.organization.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Typed accessor for the Organization module's per-request security context.
 *
 * <p>Extracts the verified JWT claims stored in Spring Security's
 * {@link SecurityContextHolder} after the JWT filter has run. Services
 * call this instead of coupling directly to Spring Security APIs,
 * making them easier to test (inject a mock context).</p>
 *
 * <p>Claims read: {@code sub} → authUserId, {@code organizationId},
 * {@code permissions} → authorities.</p>
 */
@Component
public class OrganizationSecurityContext {

    private static final String SUPER_ADMIN_AUTHORITY = "Super_admin";

    /**
     * The {@code sub} claim — stable AuthHub user UUID.
     *
     * @throws IllegalStateException if the claim is missing or not a valid UUID
     */
    public UUID getAuthUserId() {
        try {
            return UUID.fromString(jwt().getSubject());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "JWT 'sub' claim is not a valid UUID: " + jwt().getSubject(), e);
        }
    }

    /**
     * The {@code organizationId} claim — used to scope all Organization queries.
     *
     * @throws IllegalStateException if the claim is missing
     */
    public UUID getOrganizationId() {
        String orgId = jwt().getClaimAsString("organizationId");
        if (orgId == null) {
            throw new IllegalStateException("JWT is missing required 'organizationId' claim");
        }
        return UUID.fromString(orgId);
    }

    /**
     * Checks whether the current user has the {@code Super_admin} authority
     * in their JWT permissions claim.
     *
     * @return {@code true} if the user has Super_admin authority
     */
    public boolean isSuperAdmin() {
        // Check permissions claim (e.g. "Super_admin")
        List<String> permissions = jwt().getClaim("permissions");
        if (permissions != null && permissions.contains(SUPER_ADMIN_AUTHORITY)) {
            return true;
        }
        // Check roles claim (e.g. "SUPER_ADMIN")
        List<String> roles = jwt().getClaim("roles");
        return roles != null && roles.contains("SUPER_ADMIN");
    }

    /**
     * Verifies the current user is Super_admin and returns their user ID.
     *
     * @return the authenticated Super_admin user's UUID
     * @throws IllegalStateException if the user is not a Super_admin
     */
    public UUID requireSuperAdminUserId() {
        if (!isSuperAdmin()) {
            throw new IllegalStateException("Access denied: Super_admin authority required");
        }
        return getAuthUserId();
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
