package com.af.novadesk.api.config;

import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enables the Hibernate {@code organizationFilter} on every
 * {@code @Transactional} service method so that ALL JPA queries are
 * automatically scoped to the caller's organization.
 *
 * <h3>Behavior</h3>
 * <table>
 *   <tr><th>Context</th><th>Action</th></tr>
 *   <tr><td>Authenticated user with {@code organizationId} in JWT</td>
 *       <td>Enable filter → queries scoped to org</td></tr>
 *   <tr><td>Authenticated user, JWT missing {@code organizationId}</td>
 *       <td>{@code JwtClaimMissingException} → fail-closed</td></tr>
 *   <tr><td>No authentication (scheduler / background job)</td>
 *       <td>Skip filter → cross-org visibility (legitimate)</td></tr>
 * </table>
 *
 * @see com.af.novadesk.api.finance.entity.AbstractEntity AbstractEntity (@FilterDef)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OrganizationFilterAspect {

    private final FinanceSecurityContext securityContext;
    private final EntityManager entityManager;

    /**
     * Enables the organization filter before any {@code @Transactional}
     * method executes — whether annotated on the class or method.
     */
    @Before("@within(org.springframework.transaction.annotation.Transactional) || " +
            "@annotation(org.springframework.transaction.annotation.Transactional)")
    public void enableOrganizationFilter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            // Scheduler / background job — no user context.
            // Skip filter; cross-org visibility is legitimate for system processes.
            log.trace("No authenticated user — skipping organizationFilter enablement");
            return;
        }

        // Authenticated user — orgId is MANDATORY.
        // FinanceSecurityContext.getOrganizationId() throws JwtClaimMissingException
        // if the claim is absent, which is the correct fail-closed behavior.
        UUID orgId = securityContext.getOrganizationId();
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("organizationFilter")
               .setParameter("orgId", orgId);

        log.debug("Enabled organizationFilter for orgId={}", orgId);
    }
}
