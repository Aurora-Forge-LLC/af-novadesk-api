package com.af.novadesk.api.asset.reservation.constants;

/**
 * Shared constants for the asset-reservation module.
 */
public final class ReservationConstants {

    private ReservationConstants() {
        throw new AssertionError("Constants class should not be instantiated");
    }

    /** Default cron for the expiry sweep — every 15 minutes. */
    public static final String DEFAULT_EXPIRY_CRON = "0 */15 * * * *";

    /** Maximum length of the free-text purpose note. */
    public static final int MAX_PURPOSE_LENGTH = 300;

    /** Maximum length of the ops decision note. */
    public static final int MAX_DECISION_NOTES_LENGTH = 500;
}
