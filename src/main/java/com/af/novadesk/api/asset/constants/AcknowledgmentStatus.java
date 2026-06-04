package com.af.novadesk.api.asset.constants;

public enum AcknowledgmentStatus {
    /** Waiting for employee to click the acknowledgment link. */
    PENDING,
    /** Employee has acknowledged receipt via the email link. */
    ACKNOWLEDGED,
    /** IT admin waived the acknowledgment requirement. */
    WAIVED
}
