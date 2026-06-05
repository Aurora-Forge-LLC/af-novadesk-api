package com.af.novadesk.api.payroll.mapper;

import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.dto.LeaveRequestDto;
import com.af.novadesk.api.payroll.dto.LeaveTransactionDto;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import com.af.novadesk.api.payroll.entity.LeaveTransaction;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps between {@link LeaveRequest} entity and {@link LeaveRequestDto}.
 */
@Component
public class LeaveRequestMapper {

    /**
     * Converts entity to DTO without child transactions (summary view).
     */
    public LeaveRequestDto toDto(LeaveRequest entity) {
        return toDto(entity, false);
    }

    /**
     * Converts entity to DTO with optional child transactions (detail view).
     */
    public LeaveRequestDto toDto(LeaveRequest entity, boolean includeTransactions) {
        if (entity == null) return null;

        LeaveRequestDto dto = LeaveRequestDto.builder()
                .id(entity.getId())
                .leaveType(entity.getLeaveType())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .numberOfDays(entity.getNumberOfDays())
                .reason(entity.getReason())
                .attachmentPath(entity.getAttachmentPath())
                .paidBalanceBefore(entity.getPaidBalanceBefore())
                .sickBalanceBefore(entity.getSickBalanceBefore())
                .paidDaysUsed(entity.getPaidDaysUsed())
                .sickDaysUsed(entity.getSickDaysUsed())
                .unpaidDaysUsed(entity.getUnpaidDaysUsed())
                .leaveRequestStatus(entity.getLeaveRequestStatus())
                .approverComment(entity.getApproverComment())
                .submittedAt(entity.getSubmittedAt())
                .approvedAt(entity.getApprovedAt())
                .cancelledAt(entity.getCancelledAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .build();

        // Warning for auto-conversion
        if (entity.getUnpaidDaysUsed() != null && entity.getUnpaidDaysUsed().compareTo(java.math.BigDecimal.ZERO) > 0) {
            dto.setWarningMessage(String.format(
                    "You have %.1f days of %s leave. %.1f days will be marked as Unpaid.",
                    entity.getLeaveType() == com.af.novadesk.api.payroll.constants.LeaveType.PAID
                            ? entity.getPaidDaysUsed() : entity.getSickDaysUsed(),
                    entity.getLeaveType(),
                    entity.getUnpaidDaysUsed()));
        }

        // Resolve relationships
        if (entity.getLegalEntity() != null) {
            dto.setLegalEntityId(entity.getLegalEntity().getId());
        }
        if (entity.getEmployee() != null) {
            dto.setEmployeeId(entity.getEmployee().getId());
            dto.setEmployeeName(entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName());
            dto.setOrganizationId(entity.getEmployee().getOrganizationId());
        }
        if (entity.getApprover() != null) {
            dto.setApproverId(entity.getApprover().getId());
            dto.setApproverName(entity.getApprover().getFirstName() + " " + entity.getApprover().getLastName());
        }
        if (entity.getSecondApprover() != null) {
            dto.setSecondApproverId(entity.getSecondApprover().getId());
            dto.setSecondApproverName(entity.getSecondApprover().getFirstName() + " " + entity.getSecondApprover().getLastName());
        }
        if (entity.getExecutiveApprover() != null) {
            dto.setExecutiveApproverId(entity.getExecutiveApprover().getId());
            dto.setExecutiveApproverName(entity.getExecutiveApprover().getFirstName() + " " + entity.getExecutiveApprover().getLastName());
        }

        return dto;
    }

    /**
     * Converts a LeaveTransaction to its DTO.
     */
    public LeaveTransactionDto toTransactionDto(LeaveTransaction tx) {
        if (tx == null) return null;
        LeaveTransactionDto dto = LeaveTransactionDto.builder()
                .id(tx.getId())
                .leaveRequestId(tx.getLeaveRequest() != null ? tx.getLeaveRequest().getId() : null)
                .leaveType(tx.getLeaveType())
                .daysChange(tx.getDaysChange())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .transactionType(tx.getTransactionType())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .build();
        if (tx.getEmployee() != null) {
            dto.setEmployeeId(tx.getEmployee().getId());
            dto.setEmployeeName(tx.getEmployee().getFirstName() + " " + tx.getEmployee().getLastName());
        }
        if (tx.getLegalEntity() != null) {
            dto.setLegalEntityId(tx.getLegalEntity().getId());
        }
        return dto;
    }
}
