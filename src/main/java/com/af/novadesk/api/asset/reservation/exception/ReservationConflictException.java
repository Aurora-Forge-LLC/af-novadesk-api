package com.af.novadesk.api.asset.reservation.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

import java.time.LocalDateTime;
import java.util.UUID;

public class ReservationConflictException extends FinanceBaseException {
    public ReservationConflictException(UUID assetId, LocalDateTime from, LocalDateTime to) {
        super("RSV_002",
              "Asset " + assetId + " already has a reservation overlapping "
              + from + " – " + to);
    }
}
