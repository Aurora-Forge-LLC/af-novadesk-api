package com.af.novadesk.api.common.security;

import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

/**
 * Request-scoped bean that resolves the authenticated caller's employee identity
 * and exposes role/permission helpers for data-scoping decisions.
 *
 * <p>The JWT carries {@code sub} (authUserId) but not a novadesk {@code employeeId}.
 * This bean bridges the gap by looking up {@code CmEmployee} by authUserId once per
 * request and caching the result. All service-layer scoping decisions should inject
 * this bean rather than duplicating JWT parsing or employee lookup logic.</p>
 *
 * <p>Permission checks delegate to Spring Security's {@link SecurityContextHolder}
 * so they reflect the same authorities {@code @PreAuthorize} uses.</p>
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class CallerContext {

    private final CmEmployeeRepository  employeeRepository;
    private final IdentitySecurityContext identityContext;

    // Cached within the request
    private CmEmployee   resolvedEmployee;
    private List<UUID>   resolvedDirectReportIds;

    // ── Identity resolution ──────────────────────────────────────────────────

    public UUID getEmployeeId() {
        return resolved().getId();
    }

    public UUID getAuthUserId() {
        return identityContext.getAuthUserId();
    }

    public UUID getOrganizationId() {
        return identityContext.getOrganizationId();
    }

    /**
     * IDs of all ACTIVE employees whose manager is the caller.
     * Empty list if the caller is not a manager or has no active direct reports.
     */
    public List<UUID> getDirectReportIds() {
        if (resolvedDirectReportIds == null) {
            resolvedDirectReportIds = employeeRepository
                    .findActiveIdsByManagerId(getEmployeeId());
        }
        return resolvedDirectReportIds;
    }

    // ── Permission helpers ───────────────────────────────────────────────────

    public boolean hasPermission(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    public boolean hasAnyPermission(String... authorities) {
        for (String a : authorities) {
            if (hasPermission(a)) return true;
        }
        return false;
    }

    // ── Convenience scoping gates ────────────────────────────────────────────

    /** Only HR_MANAGER holds leave:approve — all other roles are self-scoped for leave. */
    public boolean isLeaveApprover() {
        return hasAnyPermission("leave:approve", "leave:manage");
    }

    /** payroll:write or payroll:manage — can see payroll batches and all payslips. */
    public boolean canReadAllPayroll() {
        return hasAnyPermission("payroll:write", "payroll:manage", "payroll:approve");
    }

    /** assets:manage or assets:assign — IT_ADMIN / ENTITY_ADMIN tier. */
    public boolean canReadAllAssets() {
        return hasAnyPermission("assets:manage", "assets:assign");
    }

    /** employees:manage or employees:write — HR_MANAGER / ENTITY_ADMIN tier. */
    public boolean canReadAllEmployees() {
        return hasAnyPermission("employees:manage", "employees:write");
    }

    /** True when the caller has at least one active direct report. */
    public boolean isManager() {
        return !getDirectReportIds().isEmpty();
    }

    /** True when the caller's employee ID matches the given id. */
    public boolean isSelf(UUID employeeId) {
        return getEmployeeId().equals(employeeId);
    }

    /** True when employeeId is in the caller's direct report list. */
    public boolean isDirectReport(UUID employeeId) {
        return getDirectReportIds().contains(employeeId);
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private CmEmployee resolved() {
        if (resolvedEmployee == null) {
            UUID authUserId = identityContext.getAuthUserId();
            UUID orgId      = identityContext.getOrganizationId();
            resolvedEmployee = employeeRepository
                    .findByAuthUserIdAndOrganizationId(authUserId, orgId)
                    .orElseThrow(() -> new IllegalStateException(
                            "No employee record found for authUserId=" + authUserId
                            + " orgId=" + orgId));
        }
        return resolvedEmployee;
    }

    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated JWT principal in SecurityContext");
        }
        return jwt;
    }
}
