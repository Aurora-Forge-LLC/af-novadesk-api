package com.af.novadesk.api.asset.constants;

/**
 * Domain event types produced by the Asset module.
 *
 * <p>Each value maps to a specific outbox event row persisted in
 * {@code af_novadesk_outbox.ast_outbox_events}. Consumers use the event type
 * to dispatch to the correct handler and deserialise the JSON payload.</p>
 */
public enum AssetEventType {

    /**
     * Fired when a new asset is registered.
     * Triggers: Debit Fixed Assets / Credit Cash or Accounts Payable in the finance GL.
     */
    ASSET_PURCHASED,

    /**
     * Fired at fiscal year-end when a depreciation schedule row is posted.
     * Triggers: Debit Depreciation Expense / Credit Accumulated Depreciation.
     */
    ASSET_DEPRECIATION_POSTED,

    /**
     * Fired when an asset is assigned to an employee (LLR-AST-02).
     * Used for audit trail and notification.
     */
    ASSET_ASSIGNED,

    /**
     * Fired when an employee returns an asset (LLR-AST-03.3).
     * Used for audit trail and notification.
     */
    ASSET_RETURNED,

    /**
     * Fired when IT admin requests a write-off (LLR-AST-03.5).
     * Triggers executive approval workflow notification.
     */
    ASSET_WRITTEN_OFF,

    /**
     * Fired when an executive approves a write-off request.
     * Triggers: accounting disposal entry in the finance GL.
     */
    ASSET_WRITE_OFF_APPROVED,

    /**
     * Fired when an executive rejects a write-off request.
     * Asset is reverted to ASSIGNED status — notification only.
     */
    ASSET_WRITE_OFF_REJECTED
}
