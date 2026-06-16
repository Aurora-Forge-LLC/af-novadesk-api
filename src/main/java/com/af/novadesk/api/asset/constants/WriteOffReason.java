package com.af.novadesk.api.asset.constants;

public enum WriteOffReason {
    /** Asset is physically damaged beyond repair. */
    DAMAGED,
    /** Asset could not be located / was lost by the custodian. */
    LOST,
    /** Asset reached end-of-life and is being retired from service. */
    RETIRED,
    /** Any other asset-related reason not covered above. */
    OTHER
}
