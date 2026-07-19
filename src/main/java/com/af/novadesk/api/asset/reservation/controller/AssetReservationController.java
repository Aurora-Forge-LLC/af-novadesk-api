package com.af.novadesk.api.asset.reservation.controller;

import com.af.novadesk.api.asset.reservation.api.AssetReservationApi;
import com.af.novadesk.api.asset.reservation.dto.ReservationCreateRequest;
import com.af.novadesk.api.asset.reservation.dto.ReservationDecisionRequest;
import com.af.novadesk.api.asset.reservation.dto.ReservationDto;
import com.af.novadesk.api.asset.reservation.service.AssetReservationService;
import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssetReservationController implements AssetReservationApi {

    private final AssetReservationService reservationService;

    @Override
    public ResponseEntity<ApiResponse<ReservationDto>> create(ReservationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Reservation created successfully",
                        reservationService.create(request.getAssetId(), request)));
    }

    @Override
    public ResponseEntity<ApiResponse<List<ReservationDto>>> listMyReservations() {
        return ResponseBuilder.ok(reservationService.listMyReservations(), ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<ReservationDto>> getById(UUID id) {
        return ResponseBuilder.ok(reservationService.getById(id), ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<ReservationDto>> cancel(UUID id) {
        return ResponseBuilder.ok(reservationService.cancel(id), "Reservation cancelled successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<List<ReservationDto>>> listAll() {
        return ResponseBuilder.ok(reservationService.listAll(), ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<ReservationDto>> approve(UUID id, ReservationDecisionRequest request) {
        return ResponseBuilder.ok(reservationService.approve(id, request), "Reservation approved successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<ReservationDto>> reject(UUID id, ReservationDecisionRequest request) {
        return ResponseBuilder.ok(reservationService.reject(id, request), "Reservation rejected successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<Map<UUID, List<ReservationDto>>>> availability(LocalDate from, LocalDate to) {
        return ResponseBuilder.ok(reservationService.availability(from, to), ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
