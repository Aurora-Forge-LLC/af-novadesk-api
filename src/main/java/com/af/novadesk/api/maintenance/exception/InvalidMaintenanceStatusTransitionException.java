package com.af.novadesk.api.maintenance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import com.af.novadesk.api.maintenance.constants.MaintenanceStatus;

import java.util.UUID;

public class InvalidMaintenanceStatusTransitionException extends FinanceBaseException {
    public InvalidMaintenanceStatusTransitionException(UUID requestId, MaintenanceStatus currentStatus, MaintenanceStatus targetStatus) {
        super("MNT_002",
              "Cannot transition maintenance request " + requestId +
              " from '" + currentStatus + "' to '" + targetStatus + "'");
    }
}
