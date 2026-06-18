package com.af.novadesk.api.payroll.mapper;

import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.LedgerEntry;
import com.af.novadesk.api.payroll.repository.PayrollLedgerRepository;
import lombok.RequiredArgsConstructor;
import com.af.novadesk.api.payroll.dto.PayrollBatchDto;
import com.af.novadesk.api.payroll.dto.PayrollFlaggedEmployeeDto;
import com.af.novadesk.api.payroll.dto.PayrollLedgerEntryDto;
import com.af.novadesk.api.payroll.dto.PayslipDto;
import com.af.novadesk.api.payroll.dto.PayslipLineItemDto;
import com.af.novadesk.api.payroll.entity.PayrollBatch;
import com.af.novadesk.api.payroll.entity.PayrollFlaggedEmployee;
import com.af.novadesk.api.payroll.entity.Payslip;
import com.af.novadesk.api.payroll.entity.PayslipLineItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps between {@link PayrollBatch} entity and {@link PayrollBatchDto},
 * including all child entities.
 */
@Component
@RequiredArgsConstructor
public class PayrollBatchMapper {

    private final PayrollLedgerRepository payrollLedgerRepository;

    private String resolveDisplayName(CmEmployee cmEmployee) {
        return cmEmployee != null ? cmEmployee.getDisplayName() : null;
    }

    private String resolveEmployeeCode(CmEmployee cmEmployee) {
        return cmEmployee != null ? cmEmployee.getEmployeeCode() : null;
    }

    /**
     * Converts entity to DTO without children (summary view).
     */
    public PayrollBatchDto toDto(PayrollBatch entity) {
        return toDto(entity, false);
    }

    /**
     * Converts entity to DTO with optional children (detail view).
     */
    public PayrollBatchDto toDto(PayrollBatch entity, boolean includeChildren) {
        if (entity == null) return null;

        PayrollBatchDto dto = PayrollBatchDto.builder()
                .id(entity.getId())
                .payPeriodStart(entity.getPayPeriodStart())
                .payPeriodEnd(entity.getPayPeriodEnd())
                .paymentDate(entity.getPaymentDate())
                .totalHeadcount(entity.getTotalHeadcount())
                .processedCount(entity.getProcessedCount())
                .flaggedCount(entity.getFlaggedCount())
                .totalGrossSalary(entity.getTotalGrossSalary())
                .totalDeductions(entity.getTotalDeductions())
                .totalNetPayout(entity.getTotalNetPayout())
                .currencyCode(entity.getCurrencyCode())
                .exchangeRateUsd(entity.getExchangeRateUsd())
                .totalNetPayoutUsd(entity.getTotalNetPayoutUsd())
                .batchStatus(entity.getBatchStatus())
                .approvedAt(entity.getApprovedAt())
                .rejectionReason(entity.getRejectionReason())
                .journalId(entity.getJournalId())
                .ledgerPostedAt(entity.getLedgerPostedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .build();

        if (entity.getLegalEntity() != null) {
            dto.setLegalEntityId(entity.getLegalEntity().getId());
            dto.setLegalEntityName(entity.getLegalEntity().getEntityName());
        }
        if (entity.getApprovedBy() != null) {
            dto.setApprovedById(entity.getApprovedBy().getId());
            dto.setApprovedByName(
                    resolveDisplayName(entity.getApprovedBy()));
        }

        if (includeChildren) {
            dto.setPayslips(entity.getPayslips() != null
                    ? entity.getPayslips().stream().map(this::toPayslipDto).collect(Collectors.toList())
                    : Collections.emptyList());
            dto.setFlaggedEmployees(entity.getFlaggedEmployees() != null
                    ? entity.getFlaggedEmployees().stream().map(this::toFlaggedDto).collect(Collectors.toList())
                    : Collections.emptyList());
            List<LedgerEntry> ledgerEntries = payrollLedgerRepository.findByReferenceId(entity.getId());
            dto.setLedgerEntries(ledgerEntries.stream().map(this::toLedgerDto).collect(Collectors.toList()));
        }

        return dto;
    }

    public PayrollFlaggedEmployeeDto toFlaggedDto(PayrollFlaggedEmployee entity) {
        if (entity == null) return null;
        PayrollFlaggedEmployeeDto dto = PayrollFlaggedEmployeeDto.builder()
                .id(entity.getId())
                .payrollBatchId(entity.getPayrollBatch() != null ? entity.getPayrollBatch().getId() : null)
                .unpaidLeaveDays(entity.getUnpaidLeaveDays())
                .unauthorizedAbsenceDays(entity.getUnauthorizedAbsenceDays())
                .flagReason(entity.getFlagReason())
                .baseSalary(entity.getBaseSalary())
                .calculatedSalary(entity.getCalculatedSalary())
                .totalWorkingDays(entity.getTotalWorkingDays())
                .daysWorked(entity.getDaysWorked())
                .flagAction(entity.getFlagAction())
                .actionAt(entity.getActionAt())
                .actionReason(entity.getActionReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
        if (entity.getEmployee() != null) {
            dto.setEmployeeId(entity.getEmployee().getId());
            dto.setEmployeeName(resolveDisplayName(entity.getEmployee()));
            dto.setDepartment(null); // department now in cm_employee_entity_assignments
        }
        if (entity.getActionBy() != null) {
            dto.setActionById(entity.getActionBy().getId());
            dto.setActionByName(resolveDisplayName(entity.getActionBy()));
        }
        return dto;
    }

    public PayslipDto toPayslipDto(Payslip entity) {
        if (entity == null) return null;
        PayslipDto dto = PayslipDto.builder()
                .id(entity.getId())
                .payrollBatchId(entity.getPayrollBatch() != null ? entity.getPayrollBatch().getId() : null)
                .payPeriodStart(entity.getPayPeriodStart())
                .payPeriodEnd(entity.getPayPeriodEnd())
                .paymentDate(entity.getPaymentDate())
                .totalWorkingDays(entity.getTotalWorkingDays())
                .daysWorked(entity.getDaysWorked())
                .paidLeaveDays(entity.getPaidLeaveDays())
                .sickLeaveDays(entity.getSickLeaveDays())
                .unpaidLeaveDays(entity.getUnpaidLeaveDays())
                .grossSalary(entity.getGrossSalary())
                .totalDeductions(entity.getTotalDeductions())
                .netSalary(entity.getNetSalary())
                .ytdGrossEarnings(entity.getYtdGrossEarnings())
                .ytdTaxes(entity.getYtdTaxes())
                .currencyCode(entity.getCurrencyCode())
                .payslipPdfPath(entity.getPayslipPdfPath())
                .isDownloaded(entity.getIsDownloaded())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
        if (entity.getEmployee() != null) {
            dto.setEmployeeId(entity.getEmployee().getId());
            dto.setEmployeeName(resolveDisplayName(entity.getEmployee()));
            dto.setEmployeeCode(resolveEmployeeCode(entity.getEmployee()));
            dto.setDepartment(null); // department now in cm_employee_entity_assignments
        }
        // legalEntityId now derived from payrollBatch since Payslip carries only organizationId
        if (entity.getPayrollBatch() != null && entity.getPayrollBatch().getLegalEntity() != null) {
            dto.setLegalEntityId(entity.getPayrollBatch().getLegalEntity().getId());
            dto.setLegalEntityName(entity.getPayrollBatch().getLegalEntity().getEntityName());
        }
        if (entity.getLineItems() != null) {
            dto.setLineItems(entity.getLineItems().stream()
                    .map(this::toLineItemDto).collect(Collectors.toList()));
        }
        return dto;
    }

    public PayslipLineItemDto toLineItemDto(PayslipLineItem entity) {
        if (entity == null) return null;
        return PayslipLineItemDto.builder()
                .id(entity.getId())
                .payslipId(entity.getPayslip() != null ? entity.getPayslip().getId() : null)
                .lineItemType(entity.getLineItemType())
                .lineItemCode(entity.getLineItemCode())
                .lineItemDescription(entity.getLineItemDescription())
                .amount(entity.getAmount())
                .currencyCode(entity.getCurrencyCode())
                .displayOrder(entity.getDisplayOrder())
                .build();
    }

    public PayrollLedgerEntryDto toLedgerDto(LedgerEntry entity) {
        if (entity == null) return null;
        return PayrollLedgerEntryDto.builder()
                .id(entity.getId())
                .journalId(entity.getJournalId())
                .payrollBatchId(entity.getReferenceId())
                .legalEntityId(entity.getLegalEntity() != null ? entity.getLegalEntity().getId() : null)
                .accountCode(entity.getAccountCode())
                .accountDescription(entity.getAccountName())
                .entrySide(entity.getEntrySide() != null ? entity.getEntrySide().name() : null)
                .amount(entity.getAmountLocal())
                .currencyCode(entity.getCurrencyLocal())
                .amountUsd(entity.getAmountUsd())
                .exchangeRateUsed(entity.getExchangeRateUsed())
                .isReversal(entity.getIsReversal())
                .originalEntryId(entity.getOriginalEntryId())
                .description(entity.getDescription())
                .referenceType(entity.getReferenceType())
                .referenceId(entity.getReferenceId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
