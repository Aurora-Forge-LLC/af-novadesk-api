package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.common.constants.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code PATCH /api/v1/expense/vendors/{id}/status}.
 *
 * <p>A dedicated DTO is required because {@link VendorDto} carries
 * {@code @NotBlank vendorName} and {@code @NotNull vendorType} constraints that
 * would reject a status-only payload with a 400 validation error.
 * Same shape as {@link UpdateEntityStatusRequest} for consistency.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update the operational status of a vendor")
public class UpdateVendorStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(
            description = "New operational status — ACTIVE to re-enable the vendor on the expense form, " +
                          "INACTIVE to hide it from autocomplete",
            example = "INACTIVE",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Status status;
}
