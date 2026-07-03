package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.common.constants.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unified DTO for EntityUserAccess.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityUserAccessDto {

    /* ── server-assigned ─────────────────────────────────────────── */
    private UUID id;
    private String email;
    private String displayName;
    private UUID legalEntityId;
    private String entityName;
    private Status status;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime createdAt;

    /* ── client-supplied ─────────────────────────────────────────── */
    @NotNull(message = "Auth user ID is required")
    private UUID authUserId;

    @NotBlank(message = "Entity role is required")
    @Pattern(
            regexp = "^(ENTITY_ADMIN|FINANCE_MANAGER|HR_MANAGER|IT_ADMIN|MANAGER|ACCOUNTANT|EMPLOYEE)$",
            message = "Entity role must be one of: ENTITY_ADMIN, FINANCE_MANAGER, HR_MANAGER, IT_ADMIN, MANAGER, ACCOUNTANT, EMPLOYEE"
    )
    private String entityRole;
}
