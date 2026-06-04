package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.PayrollAuditAction;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Combined DTO for PayrollAuditLog — cross-cutting immutable audit trail.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayrollAuditLogDto {

    private UUID id;
    private UUID organizationId;

    private PayrollAuditAction action;

    /** Entity type, e.g. "LeaveRequest", "PayrollBatch". */
    private String entityType;

    private UUID entityId;

    private UUID performedById;
    private String performedByName;

    /** Human-readable audit message. */
    private String details;

    /** Full JSON snapshot of entity state at the time of the action. */
    private String changeSnapshot;

    private LocalDateTime createdAt;
}
