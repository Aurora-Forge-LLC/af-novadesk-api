package com.af.novadesk.api.maintenance.constants;

/**
 * Tunable thresholds for the maintenance request module.
 */
public final class MaintenanceConstants {

    private MaintenanceConstants() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /** SUBMITTED requests open at least this many days are escalated to HIGH priority. */
    public static final int ESCALATION_THRESHOLD_DAYS = 3;

    public static final int MAX_DESCRIPTION_LENGTH = 2000;
}
