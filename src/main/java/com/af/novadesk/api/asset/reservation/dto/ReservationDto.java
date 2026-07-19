package com.af.novadesk.api.asset.reservation.dto;

import com.af.novadesk.api.asset.reservation.constants.ReservationStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ReservationDto {
    private UUID id;
    private UUID assetId;
    private String assetType;
    private String serialNumber;
    private UUID requestedBy;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String purpose;
    private ReservationStatus status;
    private UUID decidedBy;
    private LocalDateTime decidedAt;
    private String decisionNotes;
    private LocalDateTime createdAt;
}
