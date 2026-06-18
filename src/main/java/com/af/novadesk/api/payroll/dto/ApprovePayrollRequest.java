package com.af.novadesk.api.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight request DTO for approving a payroll batch.
 * The batch already contains pay period and payment date from initiation;
 * approval only needs to confirm the action. No required fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovePayrollRequest {

    /** Optional approval note or comment. */
    private String approvalNote;
}
