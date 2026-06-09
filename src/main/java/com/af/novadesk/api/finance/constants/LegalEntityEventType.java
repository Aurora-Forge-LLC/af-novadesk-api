package com.af.novadesk.api.finance.constants;


import com.af.novadesk.api.common.entity.LegalEntity;

/**
 * Domain events produced by the {@link LegalEntity} aggregate.
 *
 * <p>Each value documents its payload contract in a Javadoc comment so the
 * event catalogue lives alongside the code.</p>
 */
public enum LegalEntityEventType {

    /**
     * Fired when an admin submits a new LegalEntity for approval.
     * Triggers: Finance-team approval notification (LLR-FIN-01.2).
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code entityId}              – UUID of the created entity</li>
     *   <li>{@code entityName}            – human-readable name</li>
     *   <li>{@code entityCode}            – short alphanumeric code</li>
     *   <li>{@code country}               – CountryCode enum value</li>
     *   <li>{@code baseCurrency}          – ISO 4217 code</li>
     *   <li>{@code incorporationDate}     – ISO-8601 date</li>
     *   <li>{@code submittedByAuthUserId} – JWT sub of submitting admin</li>
     *   <li>{@code organizationId}        – org scope of the request</li>
     * </ul>
     * </p>
     */
    LEGAL_ENTITY_CREATED,

    /**
     * Fired when the Finance team approves a pending LegalEntity.
     * Triggers: Chart of Accounts generation, bank account seeding,
     * fiscal year initialisation (LLR-FIN-01.2).
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code entityId}            – UUID of the approved entity</li>
     *   <li>{@code approvedByAuthUserId}– JWT sub of the approver</li>
     *   <li>{@code approvedAt}          – ISO-8601 datetime</li>
     *   <li>{@code organizationId}      – org scope</li>
     * </ul>
     * </p>
     */
    LEGAL_ENTITY_APPROVED,

    /**
     * Fired when the Finance team rejects a pending LegalEntity.
     * Triggers: rejection notification to the submitting admin.
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code entityId}            – UUID of the rejected entity</li>
     *   <li>{@code rejectedByAuthUserId}– JWT sub of the reviewer</li>
     *   <li>{@code reason}              – human-readable rejection reason</li>
     *   <li>{@code rejectedAt}          – ISO-8601 datetime</li>
     *   <li>{@code organizationId}      – org scope</li>
     * </ul>
     * </p>
     */
    LEGAL_ENTITY_REJECTED,

    /**
     * Fired when an entity's operational Status transitions (ACTIVE ↔ INACTIVE).
     * Triggers: downstream modules to stop/resume serving entity-scoped data.
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code entityId}            – UUID of the entity</li>
     *   <li>{@code previousStatus}      – prior Status enum value</li>
     *   <li>{@code newStatus}           – new Status enum value</li>
     *   <li>{@code changedByAuthUserId} – JWT sub of the actor</li>
     *   <li>{@code organizationId}      – org scope</li>
     * </ul>
     * </p>
     */
    LEGAL_ENTITY_STATUS_CHANGED
}