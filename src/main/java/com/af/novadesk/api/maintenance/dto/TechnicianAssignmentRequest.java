package com.af.novadesk.api.maintenance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TechnicianAssignmentRequest {

    @NotNull(message = "Technician is required")
    private UUID technicianId;
}
