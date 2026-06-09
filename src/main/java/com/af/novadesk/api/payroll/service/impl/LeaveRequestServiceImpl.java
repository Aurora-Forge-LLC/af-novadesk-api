package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.payroll.constants.LeavePaymentType;
import com.af.novadesk.api.payroll.constants.LeaveRequestEventType;
import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.constants.LeaveType;
import com.af.novadesk.api.payroll.dto.LeaveActionDto;
import com.af.novadesk.api.payroll.dto.LeaveBalanceDto;
import com.af.novadesk.api.payroll.dto.LeaveRequestDto;
import com.af.novadesk.api.payroll.dto.LeaveValidationResult;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.entity.LeavePolicy;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import com.af.novadesk.api.payroll.entity.LeaveTransaction;
import com.af.novadesk.api.payroll.exception.*;
import com.af.novadesk.api.payroll.mapper.LeaveRequestMapper;
import com.af.novadesk.api.payroll.repository.*;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.payroll.service.LeaveRequestOutboxService;
import com.af.novadesk.api.payroll.service.LeaveRequestService;
import com.af.novadesk.api.payroll.service.LeaveRuleEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LeaveRequestServiceImpl implements LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTransactionRepository leaveTransactionRepository;
    private final EmployeeRepository employeeRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveRuleEngine leaveRuleEngine;
    private final LeaveRequestMapper mapper;
    private final LeaveRequestOutboxService outboxService;
    private final FiscalYearSettingRepository fiscalYearSettingRepository;

    public LeaveRequestServiceImpl(LeaveRequestRepository leaveRequestRepository,
                                   LeaveBalanceRepository leaveBalanceRepository,
                                   LeaveTransactionRepository leaveTransactionRepository,
                                   EmployeeRepository employeeRepository,
                                   LeavePolicyRepository leavePolicyRepository,
                                   FiscalYearSettingRepository fiscalYearSettingRepository,
                                   LeaveRuleEngine leaveRuleEngine,
                                   LeaveRequestMapper mapper,
                                   LeaveRequestOutboxService outboxService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveTransactionRepository = leaveTransactionRepository;
        this.employeeRepository = employeeRepository;
        this.leavePolicyRepository = leavePolicyRepository;
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
        this.leaveRuleEngine = leaveRuleEngine;
        this.mapper = mapper;
        this.outboxService = outboxService;
    }

    @Override
    public LeaveRequestDto submitLeaveRequest(LeaveRequestDto request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.getEmployeeId()));

        // Validate dates
        LocalDate today = LocalDate.now();
        if (request.getStartDate().isBefore(today))
            throw new InvalidLeaveDateException("Start date cannot be in the past");
        if (request.getEndDate().isBefore(request.getStartDate()))
            throw new InvalidLeaveDateException("End date must be after start date");

        // Load the leave policy
        LeavePolicy policy = leavePolicyRepository.findById(request.getLeavePolicyId())
                .orElseThrow(() -> new LeavePolicyNotFoundException(request.getLeavePolicyId()));

        // Load balance for this specific employee + policy + fiscal year
        FiscalYearSetting currentFy = fiscalYearSettingRepository
                .findByLegalEntityId(employee.getLegalEntity().getId())
                .stream().findFirst().orElse(null);
        LeaveBalance balance = null;
        if (currentFy != null) {
            balance = leaveBalanceRepository
                    .findByEmployeeIdAndLeavePolicyIdAndFiscalYearSettingId(
                            employee.getId(), policy.getId(), currentFy.getId())
                    .orElse(null);
        }

        // Calculate effective available balance (uses rule engine for earned policies)
        BigDecimal effectiveAvailable;
        if (policy.getIsEarned() && balance != null) {
            LeaveValidationResult validation = leaveRuleEngine.validate(balance, policy, request.getNumberOfDays());
            // paidDaysAllowed is the number of days the rule engine allows as paid from this policy
            effectiveAvailable = validation.getPaidDaysAllowed() != null
                    ? validation.getPaidDaysAllowed() : BigDecimal.ZERO;
            // Clamp to zero
            if (effectiveAvailable.compareTo(BigDecimal.ZERO) < 0) {
                effectiveAvailable = BigDecimal.ZERO;
            }
        } else {
            effectiveAvailable = balance != null ? balance.getAvailableDays() : BigDecimal.ZERO;
        }

        BigDecimal remaining = request.getNumberOfDays();

        BigDecimal paidDays = BigDecimal.ZERO;
        BigDecimal sickDays = BigDecimal.ZERO;
        BigDecimal unpaidDays = BigDecimal.ZERO;

        // Calculate used days based on policy payment type and available balance
        if (policy.getPaymentType() == LeavePaymentType.PAID) {
            if (effectiveAvailable.compareTo(remaining) >= 0) {
                paidDays = remaining;
            } else {
                paidDays = effectiveAvailable;
                remaining = remaining.subtract(effectiveAvailable);
                unpaidDays = remaining;
            }
        } else {
            // UNPAID policy — all days are unpaid
            unpaidDays = remaining;
        }

        // Reserve days in the specific policy balance
        if (paidDays.compareTo(BigDecimal.ZERO) > 0 && balance != null) {
            balance.setPendingDays(balance.getPendingDays().add(paidDays));
            recalculateAvailableDays(balance, policy);
            leaveBalanceRepository.save(balance);
        }

        // Map policy payment type to legacy LeaveType for backward compatibility
        LeaveType mappedLeaveType = (policy.getPaymentType() == LeavePaymentType.PAID)
                ? LeaveType.PAID : LeaveType.UNPAID;

        // Create leave request
        LeaveRequest lr = new LeaveRequest();
        lr.setLegalEntity(employee.getLegalEntity());
        lr.setEmployee(employee);
        lr.setLeaveType(mappedLeaveType);
        lr.setLeavePolicy(policy);
        lr.setStartDate(request.getStartDate());
        lr.setEndDate(request.getEndDate());
        lr.setNumberOfDays(request.getNumberOfDays());
        lr.setReason(request.getReason());
        lr.setAttachmentPath(request.getAttachmentPath());
        lr.setPaidBalanceBefore(balance != null ? balance.getAvailableDays().add(balance.getPendingDays()) : BigDecimal.ZERO);
        lr.setSickBalanceBefore(BigDecimal.ZERO);
        lr.setPaidDaysUsed(paidDays);
        lr.setSickDaysUsed(sickDays);
        lr.setUnpaidDaysUsed(unpaidDays);
        lr.setLeaveRequestStatus(LeaveRequestStatus.PENDING);
        lr.setSubmittedAt(LocalDateTime.now());
        lr.setStatus(Status.ACTIVE);

        // Set manager as approver
        if (employee.getManager() != null) {
            lr.setApprover(employee.getManager());
        }

        lr = leaveRequestRepository.save(lr);

        outboxService.createEvent(lr, LeaveRequestEventType.LEAVE_REQUESTED,
                "{\"leaveRequestId\":\"" + lr.getId() + "\"}", employee.getAuthUserId());

        return mapper.toDto(lr);
    }

    @Override
    public LeaveRequestDto approveLeaveRequest(UUID requestId, LeaveActionDto approval) {
        LeaveRequest lr = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));

        if (lr.getLeaveRequestStatus() != LeaveRequestStatus.PENDING
                && lr.getLeaveRequestStatus() != LeaveRequestStatus.MODIFICATION_REQUESTED) {
            throw new InvalidLeaveStateException(requestId, lr.getLeaveRequestStatus(), LeaveRequestStatus.APPROVED);
        }

        // Deduct from the policy-specific balance
        deductBalance(lr);

        // Create leave transactions
        if (lr.getPaidDaysUsed().compareTo(BigDecimal.ZERO) > 0) {
            createTransaction(lr, lr.getLeaveType(), lr.getPaidDaysUsed(), "DEDUCTION", "Approved paid leave");
        }
        if (lr.getSickDaysUsed().compareTo(BigDecimal.ZERO) > 0) {
            createTransaction(lr, LeaveType.SICK, lr.getSickDaysUsed(), "DEDUCTION", "Approved sick leave");
        }
        if (lr.getUnpaidDaysUsed().compareTo(BigDecimal.ZERO) > 0) {
            createTransaction(lr, LeaveType.UNPAID, lr.getUnpaidDaysUsed(), "DEDUCTION", "Unpaid leave (excess balance)");
        }

        lr.setLeaveRequestStatus(LeaveRequestStatus.APPROVED);
        lr.setApprovedAt(LocalDateTime.now());
        if (approval != null && approval.getApproverComment() != null) {
            lr.setApproverComment(approval.getApproverComment());
        }
        lr = leaveRequestRepository.save(lr);

        outboxService.createEvent(lr, LeaveRequestEventType.LEAVE_APPROVED,
                "{\"leaveRequestId\":\"" + lr.getId() + "\"}", lr.getEmployee().getAuthUserId());

        return mapper.toDto(lr);
    }

    @Override
    public LeaveRequestDto rejectLeaveRequest(UUID requestId, LeaveActionDto rejection) {
        LeaveRequest lr = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));

        if (lr.getLeaveRequestStatus() != LeaveRequestStatus.PENDING
                && lr.getLeaveRequestStatus() != LeaveRequestStatus.MODIFICATION_REQUESTED) {
            throw new InvalidLeaveStateException(requestId, lr.getLeaveRequestStatus(), LeaveRequestStatus.REJECTED);
        }

        // Restore pending days to the policy-specific balance
        restorePendingDays(lr);

        lr.setLeaveRequestStatus(LeaveRequestStatus.REJECTED);
        lr.setApproverComment(rejection.getApproverComment());
        lr = leaveRequestRepository.save(lr);

        outboxService.createEvent(lr, LeaveRequestEventType.LEAVE_REJECTED,
                "{\"leaveRequestId\":\"" + lr.getId() + "\"}", lr.getEmployee().getAuthUserId());

        return mapper.toDto(lr);
    }

    @Override
    public LeaveRequestDto requestModification(UUID requestId, LeaveActionDto modification) {
        LeaveRequest lr = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));
        lr.setLeaveRequestStatus(LeaveRequestStatus.MODIFICATION_REQUESTED);
        lr.setApproverComment(modification.getApproverComment());
        lr = leaveRequestRepository.save(lr);

        outboxService.createEvent(lr, LeaveRequestEventType.LEAVE_MODIFICATION_REQUESTED,
                "{\"leaveRequestId\":\"" + lr.getId() + "\"}", lr.getEmployee().getAuthUserId());

        return mapper.toDto(lr);
    }

    @Override
    public LeaveRequestDto cancelLeaveRequest(UUID requestId) {
        LeaveRequest lr = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));

        // Must be at least 2 days before start date
        if (LocalDate.now().plusDays(2).isAfter(lr.getStartDate())) {
            throw new InvalidLeaveDateException("Leave can only be cancelled at least 2 days before the start date");
        }

        if (lr.getLeaveRequestStatus() == LeaveRequestStatus.CANCELLED) {
            throw new InvalidLeaveStateException("Leave request is already cancelled");
        }

        if (lr.getLeaveRequestStatus() == LeaveRequestStatus.APPROVED) {
            // Restore used days from the policy-specific balance
            restoreBalance(lr);
            if (lr.getPaidDaysUsed().compareTo(BigDecimal.ZERO) > 0)
                createTransaction(lr, lr.getLeaveType(), lr.getPaidDaysUsed().negate(), "RESTORATION", "Leave cancelled");
            if (lr.getSickDaysUsed().compareTo(BigDecimal.ZERO) > 0)
                createTransaction(lr, LeaveType.SICK, lr.getSickDaysUsed().negate(), "RESTORATION", "Leave cancelled");
            if (lr.getUnpaidDaysUsed().compareTo(BigDecimal.ZERO) > 0)
                createTransaction(lr, LeaveType.UNPAID, lr.getUnpaidDaysUsed().negate(), "RESTORATION", "Unpaid leave cancelled");
        } else {
            // Restore pending days
            restorePendingDays(lr);
        }

        lr.setLeaveRequestStatus(LeaveRequestStatus.CANCELLED);
        lr.setCancelledAt(LocalDateTime.now());
        lr = leaveRequestRepository.save(lr);

        outboxService.createEvent(lr, LeaveRequestEventType.LEAVE_CANCELLED,
                "{\"leaveRequestId\":\"" + lr.getId() + "\"}", lr.getEmployee().getAuthUserId());

        return mapper.toDto(lr);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveRequestDto getLeaveRequest(UUID requestId) {
        LeaveRequest lr = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));
        return mapper.toDto(lr);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> listLeaveRequestsByEmployee(UUID employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> listLeaveRequestsByOrganization(UUID organizationId) {
        return leaveRequestRepository.findByLegalEntityOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> listPendingByApprover(UUID approverId) {
        return leaveRequestRepository.findByApproverIdAndLeaveRequestStatus(approverId, LeaveRequestStatus.PENDING)
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> listPendingByEntity(UUID legalEntityId) {
        return leaveRequestRepository.findByLegalEntityIdAndLeaveRequestStatus(legalEntityId, LeaveRequestStatus.PENDING)
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    public LeaveRequestDto expireLeaveRequest(UUID requestId) {
        LeaveRequest lr = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));

        // Only PENDING and MODIFICATION_REQUESTED can be expired
        if (lr.getLeaveRequestStatus() != LeaveRequestStatus.PENDING
                && lr.getLeaveRequestStatus() != LeaveRequestStatus.MODIFICATION_REQUESTED) {
            throw new InvalidLeaveStateException(requestId, lr.getLeaveRequestStatus(), LeaveRequestStatus.EXPIRED);
        }

        // Restore pending days back to available balance
        restorePendingDays(lr);

        // Transition to EXPIRED
        lr.setLeaveRequestStatus(LeaveRequestStatus.EXPIRED);
        lr.setApproverComment("Auto-expired: start date (" + lr.getStartDate()
                + ") passed without approval");
        lr = leaveRequestRepository.save(lr);

        // Publish outbox event
        outboxService.createEvent(lr, LeaveRequestEventType.LEAVE_EXPIRED,
                "{\"leaveRequestId\":\"" + lr.getId() + "\"}",
                lr.getEmployee().getAuthUserId());

        return mapper.toDto(lr);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveBalanceDto getLeaveBalance(UUID employeeId, LeaveType type) {
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndLeaveType(employeeId, type)
                .stream().findFirst()
                .orElseThrow(() -> new LeaveBalanceNotFoundException(employeeId, type));
        return toBalanceDto(balance);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> getLeaveBalancesByEmployee(UUID employeeId) {
        return leaveBalanceRepository.findByEmployeeId(employeeId).stream()
                .filter(balance -> balance.getLeavePolicy() != null)
                .map(this::toBalanceDto).collect(Collectors.toList());
    }

    @Override
    public LeaveBalanceDto initializeLeaveBalances(UUID employeeId, UUID legalEntityId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        // Re-initialization handled by scheduled job
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> listUnpaidLeaveRequests(UUID employeeId) {
        return leaveRequestRepository
                .findByEmployeeIdAndUnpaidDaysUsedGreaterThanOrderByCreatedAtDesc(
                        employeeId, BigDecimal.ZERO)
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    // --- Helpers ---

    /**
     * Moves days from pending to used in the policy-specific balance.
     * Uses the leave policy associated with the request to find the correct balance.
     */
    private void deductBalance(LeaveRequest lr) {
        BigDecimal totalDaysToDeduct = lr.getPaidDaysUsed().add(lr.getSickDaysUsed());
        if (totalDaysToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        LeavePolicy policy = lr.getLeavePolicy();
        if (policy == null) {
            return;
        }

        LeaveBalance bal = leaveBalanceRepository
                .findByEmployeeIdAndLeavePolicyId(lr.getEmployee().getId(), policy.getId())
                .stream().findFirst().orElse(null);
        if (bal != null) {
            bal.setPendingDays(bal.getPendingDays().subtract(totalDaysToDeduct));
            bal.setUsedDays(bal.getUsedDays().add(totalDaysToDeduct));
            recalculateAvailableDays(bal, policy);
            leaveBalanceRepository.save(bal);
        }
    }

    /**
     * Moves days from used back to available in the policy-specific balance.
     * Used when an approved leave is cancelled.
     */
    private void restoreBalance(LeaveRequest lr) {
        BigDecimal totalDaysToRestore = lr.getPaidDaysUsed().add(lr.getSickDaysUsed());
        if (totalDaysToRestore.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        LeavePolicy policy = lr.getLeavePolicy();
        if (policy == null) {
            return;
        }

        LeaveBalance bal = leaveBalanceRepository
                .findByEmployeeIdAndLeavePolicyId(lr.getEmployee().getId(), policy.getId())
                .stream().findFirst().orElse(null);
        if (bal != null) {
            bal.setUsedDays(bal.getUsedDays().subtract(totalDaysToRestore));
            recalculateAvailableDays(bal, policy);
            leaveBalanceRepository.save(bal);
        }
    }

    /**
     * Moves days from pending back to available in the policy-specific balance.
     * Used when a pending leave request is rejected, cancelled, or expired.
     */
    private void restorePendingDays(LeaveRequest lr) {
        BigDecimal totalDaysToRestore = lr.getPaidDaysUsed().add(lr.getSickDaysUsed());
        if (totalDaysToRestore.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        LeavePolicy policy = lr.getLeavePolicy();
        if (policy == null) {
            return;
        }

        LeaveBalance bal = leaveBalanceRepository
                .findByEmployeeIdAndLeavePolicyId(lr.getEmployee().getId(), policy.getId())
                .stream().findFirst().orElse(null);
        if (bal != null) {
            bal.setPendingDays(bal.getPendingDays().subtract(totalDaysToRestore));
            recalculateAvailableDays(bal, policy);
            leaveBalanceRepository.save(bal);
        }
    }

    /**
     * Recalculates available days using the correct base:
     * {@code earnedDays} for earned policies, {@code totalAllocated} for upfront policies.
     */
    private void recalculateAvailableDays(LeaveBalance bal, LeavePolicy policy) {
        BigDecimal base = (policy != null && policy.getIsEarned())
                ? bal.getEarnedDays()
                : bal.getTotalAllocated();
        bal.setAvailableDays(base
                .subtract(bal.getUsedDays())
                .subtract(bal.getPendingDays()));
    }

    private void createTransaction(LeaveRequest lr, LeaveType type, BigDecimal daysChange, String txnType, String desc) {
        // Unpaid leave does not consume from the policy-specific balance
        BigDecimal balanceBefore;
        BigDecimal balanceAfter;

        if (type == LeaveType.UNPAID) {
            balanceBefore = BigDecimal.ZERO;
            balanceAfter = BigDecimal.ZERO;
        } else {
            LeavePolicy policy = lr.getLeavePolicy();
            LeaveBalance bal = null;
            if (policy != null) {
                bal = leaveBalanceRepository
                        .findByEmployeeIdAndLeavePolicyId(lr.getEmployee().getId(), policy.getId())
                        .stream().findFirst().orElse(null);
            }
            balanceBefore = bal != null ? bal.getAvailableDays() : BigDecimal.ZERO;
            // No clamping: balances may be negative when earned leave borrowing is in effect
            balanceAfter = balanceBefore.subtract(daysChange);
        }

        LeaveTransaction tx = LeaveTransaction.builder()
                .leaveRequest(lr)
                .legalEntity(lr.getLegalEntity())
                .employee(lr.getEmployee())
                .leaveType(type)
                .daysChange(daysChange)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .transactionType(txnType)
                .description(desc)
                .status(Status.ACTIVE)
                .build();
        leaveTransactionRepository.save(tx);
    }

    private LeaveBalanceDto toBalanceDto(LeaveBalance entity) {
        LeavePolicy policy = entity.getLeavePolicy();

        // Compute borrow limit for earned leave policies
        BigDecimal borrowLimit = BigDecimal.ZERO;
        if (policy != null && policy.getIsEarned()) {
            BigDecimal currentBalance = entity.getEarnedDays()
                    .subtract(entity.getUsedDays())
                    .subtract(entity.getPendingDays());
            int borrowMultiple = policy.getEffectiveBorrowMultiple();

            if (currentBalance.compareTo(BigDecimal.ZERO) < 0) {
                borrowLimit = BigDecimal.ZERO;
            } else if (currentBalance.compareTo(BigDecimal.ZERO) == 0) {
                borrowLimit = policy.getMonthlyAccrualRate()
                        .multiply(BigDecimal.valueOf(borrowMultiple));
            } else {
                borrowLimit = currentBalance.multiply(BigDecimal.valueOf(borrowMultiple));
            }
        }

        BigDecimal effectiveAvailable = entity.getAvailableDays().add(borrowLimit);

        return LeaveBalanceDto.builder()
                .id(entity.getId())
                .legalEntityId(entity.getLegalEntity() != null ? entity.getLegalEntity().getId() : null)
                .employeeId(entity.getEmployee() != null ? entity.getEmployee().getId() : null)
                .employeeName(entity.getEmployee() != null
                        ? entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName() : null)
                .leaveType(entity.getLeaveType())
                .leavePolicyId(policy != null ? policy.getId() : null)
                .leavePolicyName(policy != null ? policy.getName() : null)
                .paymentType(policy != null ? policy.getPaymentType() : null)
                .totalAllocated(entity.getTotalAllocated())
                .usedDays(entity.getUsedDays())
                .pendingDays(entity.getPendingDays())
                .availableDays(entity.getAvailableDays())
                .earnedDays(entity.getEarnedDays())
                .borrowLimit(borrowLimit)
                .effectiveAvailable(effectiveAvailable)
                .accrualStartDate(entity.getAccrualStartDate())
                .fiscalYearSettingId(entity.getFiscalYearSetting() != null ? entity.getFiscalYearSetting().getId() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
