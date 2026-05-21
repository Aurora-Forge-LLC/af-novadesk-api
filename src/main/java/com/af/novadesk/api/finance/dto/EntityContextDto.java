package com.af.novadesk.api.finance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unified DTO for entity context selection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityContextDto {

    /* ── client-supplied ─────────────────────────────────────────── */
    @NotNull(message = "Legal entity ID is required")
    private UUID legalEntityId;

    /* ── server-assigned ─────────────────────────────────────────── */
    private String entityName;
    private String entityCode;
    private String baseCurrency;
    private LocalDateTime selectedAt;
}
