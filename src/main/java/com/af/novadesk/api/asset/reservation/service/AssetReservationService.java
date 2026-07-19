package com.af.novadesk.api.asset.reservation.service;

import com.af.novadesk.api.asset.reservation.dto.ReservationCreateRequest;
import com.af.novadesk.api.asset.reservation.dto.ReservationDecisionRequest;
import com.af.novadesk.api.asset.reservation.dto.ReservationDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AssetReservationService {

    /** Create a reservation request for a shared asset. */
    ReservationDto create(UUID assetId, ReservationCreateRequest request);

    /** Fetch a single reservation owned by the caller. */
    ReservationDto getById(UUID id);

    /** The caller's own reservations — drives the "My reservations" view. */
    List<ReservationDto> listMyReservations();

    /** Withdraw a pending reservation. */
    ReservationDto cancel(UUID id);

    /** Org-wide reservation feed for the ops console. */
    List<ReservationDto> listAll();

    /** Approve a pending reservation. */
    ReservationDto approve(UUID id, ReservationDecisionRequest request);

    /** Reject a pending reservation. */
    ReservationDto reject(UUID id, ReservationDecisionRequest request);

    /**
     * Reservation calendar for every asset in the organization over a date range.
     * Keyed by asset id, each value is the list of live reservations intersecting
     * the window.
     */
    Map<UUID, List<ReservationDto>> availability(LocalDate from, LocalDate to);
}
