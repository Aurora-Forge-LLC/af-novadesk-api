package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.constants.AssetDeductionStatus;
import com.af.novadesk.api.asset.constants.WriteOffReason;
import com.af.novadesk.api.asset.dto.AssetPayrollDeductionDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AssetPayrollDeductionService {

    /**
     * Lightweight projection consumed by the payroll module during salary
     * calculation. Intentionally avoids exposing asset-module entities
     * directly to the payroll service.
     */
    record PendingDeduction(
            UUID id,
            UUID employeeId,
            BigDecimal amount,
            String currencyCode,
            WriteOffReason writeOffReason,
            String assetLabel
    ) {}

    /**
     * Returns all PENDING deductions for one employee whose {@code deductionDate}
     * falls within the payroll batch period. Called by payroll during
     * {@code calculateSalaries()}.
     */
    List<PendingDeduction> findPendingForPeriod(UUID orgId, UUID employeeId,
                                                LocalDate periodStart, LocalDate periodEnd);

    /**
     * Marks a deduction as APPLIED and records which payroll batch and payslip
     * processed it. Called by payroll immediately after creating the line item.
     */
    void markApplied(UUID deductionId, UUID payrollBatchId, UUID payslipId);

    /** List deductions for the current organisation with optional filters. */
    Page<AssetPayrollDeductionDto> listDeductions(UUID orgId, AssetDeductionStatus status,
                                                   UUID employeeId, LocalDate from, LocalDate to,
                                                   Pageable pageable);

    /** Fetch the deduction record for a specific write-off. */
    AssetPayrollDeductionDto getByWriteOffId(UUID writeOffId, UUID orgId);
}
