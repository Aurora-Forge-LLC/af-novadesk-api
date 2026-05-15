package com.af.novadesk.api.finance.constants;

/*
        * Approval lifecycle for a newly created {@link LegalEntity}.
        * Distinct from the inherited {@code Status} (ACTIVE / INACTIVE) so that
 * "pending finance-team approval" is not conflated with "soft-deleted".
        *
        * <ul>
 *   <li>{@code PENDING}  – created but awaiting Finance-team approval (LLR-FIN-01.2)</li>
        *   <li>{@code APPROVED} – Finance team approved; entity is fully operational</li>
        *   <li>{@code REJECTED} – Finance team rejected the entity registration</li>
        * </ul>
        */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}