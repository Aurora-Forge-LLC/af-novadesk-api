package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.CapitalInjectionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body to update the lifecycle status of a capital injection (LLR-FIN-02).
 */
@Data
@Schema(description = "Request to update capital injection lifecycle status")
public class CapitalInjectionStatusRequest {

    @NotNull(message = "injectionStatus is required")
    @Schema(description = "New lifecycle status", example = "PENDING_REVIEW")
    @JsonProperty("injection_status")
    private CapitalInjectionStatus injectionStatus;

    @Size(max = 500, message = "reason must not exceed 500 characters")
    @Schema(description = "Reason for the status change (required for VOID/FAILED)")
    private String reason;
}
