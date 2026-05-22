package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.common.constants.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for PATCH /api/v1/legal-entities/{id}/status.
 *
 * <p>Only the {@code status} field is required — all other entity fields
 * are ignored for a status toggle operation.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update the operational status of a legal entity")
public class UpdateEntityStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "New operational status", example = "INACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private Status status;
}
