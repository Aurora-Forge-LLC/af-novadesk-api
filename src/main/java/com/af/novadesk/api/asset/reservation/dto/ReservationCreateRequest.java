package com.af.novadesk.api.asset.reservation.dto;

import com.af.novadesk.api.asset.reservation.constants.ReservationConstants;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ReservationCreateRequest {

    @NotNull(message = "Asset is required")
    private UUID assetId;

    @NotNull(message = "Reservation start is required")
    @Future(message = "Reservation start must be in the future")
    private LocalDateTime startsAt;

    @NotNull(message = "Reservation end is required")
    @Future(message = "Reservation end must be in the future")
    private LocalDateTime endsAt;

    @Size(max = ReservationConstants.MAX_PURPOSE_LENGTH,
          message = "Purpose must not exceed 300 characters")
    private String purpose;
}
