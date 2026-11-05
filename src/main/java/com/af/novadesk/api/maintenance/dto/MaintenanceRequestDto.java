package com.af.novadesk.api.maintenance.dto;

import com.af.novadesk.api.maintenance.constants.MaintenancePriority;
import com.af.novadesk.api.maintenance.constants.MaintenanceStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MaintenanceRequestDto {
    private UUID id;
    private UUID assetId;
    private String assetSerialNumber;
    private UUID requestedBy;
    private String requestedByName;
    private String description;
    private MaintenancePriority priority;
    private MaintenanceStatus maintenanceStatus;
    private BigDecimal costEstimate;
    private UUID assignedTechnicianId;
    private String assignedTechnicianLabel;
    private String reviewNotes;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
