package com.af.novadesk.api.asset.reservation.constants;

/**
 * Lifecycle states for an asset reservation (LLR-AST-05).
 */
public enum ReservationStatus {
    /** Awaiting an ops approval decision. */
    PENDING,
    /** Approved by ops — the asset is held for the requester's window. */
    APPROVED,
    /** Declined by ops. */
    REJECTED,
    /** Withdrawn by the requester before a decision was made. */
    CANCELLED,
    /** The window passed without an approval decision. */
    EXPIRED
}
