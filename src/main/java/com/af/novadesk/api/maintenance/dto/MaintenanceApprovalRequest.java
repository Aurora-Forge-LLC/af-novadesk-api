package com.af.novadesk.api.maintenance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaintenanceApprovalRequest {

    @NotNull(message = "Cost estimate is required")
    @DecimalMin(value = "0.01", message = "Cost estimate must be greater than zero")
    private BigDecimal costEstimate;

    @Size(max = 1000)
    private String notes;
}
