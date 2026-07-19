package com.af.novadesk.api.asset.reservation.api;

import com.af.novadesk.api.asset.reservation.dto.ReservationCreateRequest;
import com.af.novadesk.api.asset.reservation.dto.ReservationDecisionRequest;
import com.af.novadesk.api.asset.reservation.dto.ReservationDto;
import com.af.novadesk.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST contract for shared-asset reservations (LLR-AST-05).
 * Base path: {@code /api/v1/asset-reservations}
 */
@Tag(name = "Asset Reservations", description = "Reserve shared assets for a time window, with ops approval and auto-expiry (LLR-AST-05)")
@RequestMapping("/api/v1/asset-reservations")
@SecurityRequirement(name = "bearerAuth")
public interface AssetReservationApi {

    @Operation(summary = "Request a reservation", description = "LLR-AST-05.1 — books a shared asset for a time window, pending ops approval.")
    @PostMapping
    @PreAuthorize("hasAuthority('assets:reserve') or hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<ReservationDto>> create(@Valid @RequestBody ReservationCreateRequest request);

    @Operation(summary = "List my reservations", description = "LLR-AST-05.2 — the caller's own reservations.")
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('assets:reserve') or hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<List<ReservationDto>>> listMyReservations();

    @Operation(summary = "Get one of my reservations")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('assets:reserve') or hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<ReservationDto>> getById(@PathVariable UUID id);

    @Operation(summary = "Cancel my reservation", description = "LLR-AST-05.3 — withdraw a pending or approved reservation.")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('assets:reserve') or hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<ReservationDto>> cancel(@PathVariable UUID id);

    @Operation(summary = "List all reservations", description = "LLR-AST-05.4 — org-wide reservation feed for the ops console.")
    @GetMapping
    @PreAuthorize("hasAuthority('assets:read')")
    ResponseEntity<ApiResponse<List<ReservationDto>>> listAll();

    @Operation(summary = "Approve a reservation", description = "LLR-AST-05.5")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<ReservationDto>> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ReservationDecisionRequest request);

    @Operation(summary = "Reject a reservation", description = "LLR-AST-05.6")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<ReservationDto>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody ReservationDecisionRequest request);

    @Operation(summary = "Reservation availability calendar",
               description = "LLR-AST-05.7 — live reservations per asset across a date range.")
    @GetMapping("/availability")
    @PreAuthorize("hasAuthority('assets:read')")
    ResponseEntity<ApiResponse<Map<UUID, List<ReservationDto>>>> availability(
            @Parameter(description = "Window start (yyyy-MM-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Window end (yyyy-MM-dd)")   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
