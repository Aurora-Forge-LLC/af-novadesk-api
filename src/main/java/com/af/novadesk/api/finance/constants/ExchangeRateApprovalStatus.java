package com.af.novadesk.api.finance.constants;

/**
 * Approval lifecycle for a manually entered exchange rate.
 * Distinct from the inherited {@code Status} (ACTIVE / INACTIVE / SUSPENDED / DELETED)
 * so that "pending finance-team approval" is not conflated with "soft-deleted".
 *
 * <ul>
 *   <li>{@code APPROVAL_PENDING} – submitted but awaiting Finance-team approval</li>
 *   <li>{@code APPROVED}         – Finance team approved; rate is ready for use</li>
 *   <li>{@code REJECTED}         – Finance team rejected the submitted rate</li>
 * </ul>
 */
public enum ExchangeRateApprovalStatus {
    APPROVAL_PENDING,
    APPROVED,
    REJECTED
}
