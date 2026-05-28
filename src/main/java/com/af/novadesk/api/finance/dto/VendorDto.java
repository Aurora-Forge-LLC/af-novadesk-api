package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.VendorType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unified DTO for a {@link com.af.novadesk.api.finance.entity.Vendor}.
 *
 * <p>Used for create, update, and response payloads. Server-assigned fields
 * are ignored on inbound requests and populated on responses.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Vendor (payee) data transfer object (LLR-FIN-03.3)")
public class VendorDto {

    /* ── server-assigned ─────────────────────────────────────────── */

    @Schema(description = "Vendor UUID — assigned by the server on creation", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @Schema(description = "Operational status of the vendor", accessMode = Schema.AccessMode.READ_ONLY)
    private Status status;

    @Schema(description = "Timestamp when the vendor was created", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp of the last update", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /* ── client-supplied ─────────────────────────────────────────── */

    @NotBlank(message = "Vendor name is required")
    @Size(max = 100, message = "Vendor name must not exceed 100 characters")
    @Schema(description = "Full legal or trading name of the vendor", example = "Amazon Web Services")
    @JsonProperty("vendor_name")
    private String vendorName;

    @NotNull(message = "Vendor type is required")
    @Schema(description = "Business category of the vendor",
            example = "SERVICE_PROVIDER",
            allowableValues = {"SERVICE_PROVIDER", "LANDLORD", "UTILITY", "SUPPLIER", "CONTRACTOR", "GOVERNMENT", "OTHER"})
    @JsonProperty("vendor_type")
    private VendorType vendorType;

    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    @Schema(description = "Government-issued tax or company registration number (optional)", example = "92-0070768")
    @JsonProperty("tax_id")
    private String taxId;

    @Schema(description = "UUID of the default expense account for form auto-fill (optional). " +
                          "When set, the destination account field is pre-filled on the expense form.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @JsonProperty("default_account_id")
    private UUID defaultAccountId;
}
