package com.af.novadesk.api.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight request DTO for voiding an approved payroll batch.
 * All relevant data already exists on the persisted batch entity.
 * No required fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoidPayrollRequest {

    /** Optional reason for voiding the payroll. */
    private String voidReason;
}
