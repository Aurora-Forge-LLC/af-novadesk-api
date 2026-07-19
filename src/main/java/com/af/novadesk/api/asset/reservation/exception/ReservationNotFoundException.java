package com.af.novadesk.api.asset.reservation.exception;

import com.af.novadesk.api.finance.exception.NotFoundException;

import java.util.UUID;

public class ReservationNotFoundException extends NotFoundException {
    public ReservationNotFoundException(UUID id) {
        super("RSV_001", "Reservation not found: " + id);
    }
}
