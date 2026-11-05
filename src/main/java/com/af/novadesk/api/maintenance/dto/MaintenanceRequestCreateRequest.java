package com.af.novadesk.api.maintenance.dto;

import com.af.novadesk.api.maintenance.constants.MaintenancePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class MaintenanceRequestCreateRequest {

    @NotNull(message = "Asset is required")
    private UUID assetId;

    @NotBlank(message = "Description is required")
    @Size(max = 2000)
    private String description;

    private MaintenancePriority priority;
}
