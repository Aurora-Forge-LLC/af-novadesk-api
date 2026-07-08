package com.af.novadesk.api.asset.constants;

public enum AssetStatus {
    /** Ready to be assigned. */
    AVAILABLE,
    /** Currently assigned to an employee. */
    ASSIGNED,
    /** Returned to IT department — available for re-assignment. */
    RETURNED,
    /** Reported lost — pending executive write-off decision. */
    LOST,
    /** Write-off requested for DAMAGED/RETIRED/OTHER — pending executive decision (asset physically present). */
    WRITE_OFF_PENDING,
    /** Disposed / decommissioned. */
    DISPOSED,
    /** Net book value has reached zero — no further depreciation posted. */
    FULLY_DEPRECATED
}
