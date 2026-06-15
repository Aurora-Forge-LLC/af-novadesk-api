package com.af.novadesk.api.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight request DTO for rejecting a payroll batch.
 * Only the rejection reason is required; all period/date fields
 * already exist on the persisted batch entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectPayrollRequest {

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;
}
