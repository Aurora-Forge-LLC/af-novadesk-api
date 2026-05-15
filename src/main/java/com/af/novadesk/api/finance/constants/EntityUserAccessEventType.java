package com.af.novadesk.api.finance.constants;


import com.af.novadesk.api.finance.entity.EntityUserAccess;

/**
 * Domain events produced by the {@link EntityUserAccess} aggregate.
 */
public enum EntityUserAccessEventType {

    /**
     * Fired when a user is granted access to a LegalEntity.
     * Triggers: downstream permission caches, entity-selector refresh (LLR-FIN-01.3).
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code accessId}            – UUID of the EntityUserAccess record</li>
     *   <li>{@code entityId}            – UUID of the LegalEntity</li>
     *   <li>{@code authUserId}          – JWT sub of the user receiving access</li>
     *   <li>{@code organizationId}      – org scope</li>
     *   <li>{@code entityRole}          – role granted (VIEWER / EDITOR / APPROVER / ADMIN)</li>
     *   <li>{@code grantedByAuthUserId} – JWT sub of the admin granting access</li>
     *   <li>{@code grantedAt}           – ISO-8601 datetime</li>
     * </ul>
     * </p>
     */
    USER_ACCESS_GRANTED,

    /**
     * Fired when a user's access to a LegalEntity is revoked.
     * Critical: downstream modules must immediately stop serving
     * entity-scoped data to this user (LLR-FIN-01.3).
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code accessId}             – UUID of the EntityUserAccess record</li>
     *   <li>{@code entityId}             – UUID of the LegalEntity</li>
     *   <li>{@code authUserId}           – JWT sub of the user losing access</li>
     *   <li>{@code organizationId}       – org scope</li>
     *   <li>{@code revokedByAuthUserId}  – JWT sub of the admin revoking access</li>
     *   <li>{@code revokedAt}            – ISO-8601 datetime</li>
     * </ul>
     * </p>
     */
    USER_ACCESS_REVOKED,

    /**
     * Fired when a user's role on an entity is changed (e.g. VIEWER → APPROVER).
     * Triggers: downstream permission cache invalidation.
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code accessId}            – UUID of the EntityUserAccess record</li>
     *   <li>{@code entityId}            – UUID of the LegalEntity</li>
     *   <li>{@code authUserId}          – JWT sub of the affected user</li>
     *   <li>{@code organizationId}      – org scope</li>
     *   <li>{@code previousRole}        – role before change</li>
     *   <li>{@code newRole}             – role after change</li>
     *   <li>{@code changedByAuthUserId} – JWT sub of the admin making the change</li>
     * </ul>
     * </p>
     */
    USER_ACCESS_ROLE_CHANGED
}
