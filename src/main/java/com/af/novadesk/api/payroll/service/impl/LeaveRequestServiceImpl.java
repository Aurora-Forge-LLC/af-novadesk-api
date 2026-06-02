package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.payroll.constants.LeaveRequestEventType;
import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.constants.LeaveType;
import com.af.novadesk.api.payroll.dto.LeaveBalanceDto;
import com.af.novadesk.api.payroll.dto.LeaveRequestDto;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import com.af.novadesk.api.payroll.entity.LeaveTransaction;
import com.af.novadesk.api.payroll.exception.*;
import com.af.novadesk.api.payroll.mapper.LeaveRequestMapper;
import com.af.novadesk.api.payroll.repository.*;
import com.af.novadesk.api.payroll.service.LeaveRequestOutboxService;
import com.af.novadesk.api.payroll.service.LeaveRequestService;
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
    private final LeaveRequestMapper mapper;
    private final LeaveRequestOutboxService outboxService;

    public LeaveRequestServiceImpl(LeaveRequestRepository leaveRequestRepository,
                                   LeaveBalanceRepository leaveBalanceRepository,
                                   LeaveTransactionRepository leaveTransactionRepository,
                                   EmployeeRepository employeeRepository,
                                   LeaveRequestMapper mapper,
                                   LeaveRequestOutboxService outboxService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveTransactionRepository = leaveTransactionRepository;
        this.employeeRepository = employeeRepository;
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

        // Load balances
        LeaveBalance paidBalance = leaveBalanceRepository.findByEmployeeIdAndLeaveType(
                employee.getId(), LeaveType.PAID).stream().findFirst().orElse(null);
        LeaveBalance sickBalance = leaveBalanceRepository.findByEmployeeIdAndLeaveType(
                employee.getId(), LeaveType.SICK).stream().findFirst().orElse(null);

        BigDecimal paidBal = paidBalance != null ? paidBalance.getAvailableDays() : BigDecimal.ZERO;
        BigDecimal sickBal = sickBalance != null ? sickBalance.getAvailableDays() : BigDecimal.ZERO;

        BigDecimal paidDays = BigDecimal.ZERO;
        BigDecimal sickDays = BigDecimal.ZERO;
        BigDecimal unpaidDays = BigDecimal.ZERO;
        BigDecimal remaining = request.getNumberOfDays();

        if (request.getLeaveType() == LeaveType.PAID) {
            if (paidBal.compareTo(remaining) >= 0) {
                paidDays = remaining;
            } else {
                paidDays = paidBal;
                remaining = remaining.subtract(paidBal);
                unpaidDays = remaining;
            }
        } else if (request.getLeaveType() == LeaveType.SICK) {
            if (sickBal.compareTo(remaining) >= 0) {
                sickDays = remaining;
            } else {
                sickDays = sickBal;
                remaining = remaining.subtract(sickBal);
                unpaidDays = remaining;
            }
        } else {
            unpaidDays = remaining;
        }

        // Reserve days in balance
        if (paidDays.compareTo(BigDecimal.ZERO) > 0 && paidBalance != null) {
            paidBalance.setPendingDays(paidBalance.getPendingDays().add(paidDays));
            paidBalance.setAvailableDays(paidBalance.getTotalAllocated()
                    .subtract(paidBalance.getUsedDays())
                    .subtract(paidBalance.getPendingDays()));
            leaveBalanceRepository.save(paidBalance);
        }
        if (sickDays.compareTo(BigDecimal.ZERO) > 0 && sickBalance != null) {
            sickBalance.setPendingDays(sickBalance.getPendingDays().add(sickDays));
            sickBalance.setAvailableDays(sickBalance.getTotalAllocated()
                    .subtract(sickBalance.getUsedDays())
                    .subtract(sickBalance.getPendingDays()));
            leaveBalanceRepository.save(sickBalance);
        }

        // Create leave request
        LeaveRequest lr = new LeaveRequest();
        lr.setLegalEntity(employee.getLegalEntity());
        lr.setEmployee(employee);
        lr.setLeaveType(request.getLeaveType());
        lr.setStartDate(request.getStartDate());
        lr.setEndDate(request.getEndDate());
        lr.setNumberOfDays(request.getNumberOfDays());
        lr.setReason(request.getReason());
        lr.setAttachmentPath(request.getAttachmentPath());
        lr.setPaidBalanceBefore(paidBal);
        lr.setSickBalanceBefore(sickBal);
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
    public LeaveRequestDto approveLeaveRequest(UUID requestId, LeaveRequestDto approval) {
        LeaveRequest lr = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));

        if (lr.getLeaveRequestStatus() != LeaveRequestStatus.PENDING
                && lr.getLeaveRequestStatus() != LeaveRequestStatus.MODIFICATION_REQUESTED) {
            throw new InvalidLeaveStateException(requestId, lr.getLeaveRequestStatus(), LeaveRequestStatus.APPROVED);
        }

        // Deduct from balances
        deductBalance(lr.getEmployee().getId(), lr.getLeaveType(), lr.getPaidDaysUsed(), lr.getSickDaysUsed());

        // Create leave transactions
        if (lr.getPaidDaysUsed().compareTo(BigDecimal.ZERO) > 0) {
            createTransaction(lr, LeaveType.PAID, lr.getPaidDaysUsed(), "DEDUCTION", "Approved paid leave");
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
    public LeaveRequestDto rejectLeaveRequest(UUID requestId, LeaveRequestDto rejection) {
        LeaveRequest lr = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));

        if (lr.getLeaveRequestStatus() != LeaveRequestStatus.PENDING
                && lr.getLeaveRequestStatus() != LeaveRequestStatus.MODIFICATION_REQUESTED) {
            throw new InvalidLeaveStateException(requestId, lr.getLeaveRequestStatus(), LeaveRequestStatus.REJECTED);
        }

        // Restore pending days
        restorePendingDays(lr.getEmployee().getId(), lr.getLeaveType(), lr.getPaidDaysUsed(), lr.getSickDaysUsed());

        lr.setLeaveRequestStatus(LeaveRequestStatus.REJECTED);
        lr.setApproverComment(rejection.getApproverComment());
        lr = leaveRequestRepository.save(lr);

        outboxService.createEvent(lr, LeaveRequestEventType.LEAVE_REJECTED,
                "{\"leaveRequestId\":\"" + lr.getId() + "\"}", lr.getEmployee().getAuthUserId());

        return mapper.toDto(lr);
    }

    @Override
    public LeaveRequestDto requestModification(UUID requestId, LeaveRequestDto modification) {
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
            // Restore used days
            restoreBalance(lr.getEmployee().getId(), LeaveType.PAID, lr.getPaidDaysUsed());
            restoreBalance(lr.getEmployee().getId(), LeaveType.SICK, lr.getSickDaysUsed());
            if (lr.getPaidDaysUsed().compareTo(BigDecimal.ZERO) > 0)
                createTransaction(lr, LeaveType.PAID, lr.getPaidDaysUsed().negate(), "RESTORATION", "Leave cancelled");
            if (lr.getSickDaysUsed().compareTo(BigDecimal.ZERO) > 0)
                createTransaction(lr, LeaveType.SICK, lr.getSickDaysUsed().negate(), "RESTORATION", "Leave cancelled");
        } else {
            // Restore pending days
            restorePendingDays(lr.getEmployee().getId(), lr.getLeaveType(), lr.getPaidDaysUsed(), lr.getSickDaysUsed());
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
                .map(this::toBalanceDto).collect(Collectors.toList());
    }

    @Override
    public LeaveBalanceDto initializeLeaveBalances(UUID employeeId, UUID legalEntityId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        // Re-initialization handled by scheduled job
        return null;
    }

    // --- Helpers ---

    private void deductBalance(UUID employeeId, LeaveType type, BigDecimal paidDays, BigDecimal sickDays) {
        if (paidDays.compareTo(BigDecimal.ZERO) > 0) {
            LeaveBalance bal = leaveBalanceRepository.findByEmployeeIdAndLeaveType(employeeId, LeaveType.PAID)
                    .stream().findFirst().orElse(null);
            if (bal != null) {
                bal.setPendingDays(bal.getPendingDays().subtract(paidDays));
                bal.setUsedDays(bal.getUsedDays().add(paidDays));
                bal.setAvailableDays(bal.getTotalAllocated().subtract(bal.getUsedDays()).subtract(bal.getPendingDays()));
                leaveBalanceRepository.save(bal);
            }
        }
        if (sickDays.compareTo(BigDecimal.ZERO) > 0) {
            LeaveBalance bal = leaveBalanceRepository.findByEmployeeIdAndLeaveType(employeeId, LeaveType.SICK)
                    .stream().findFirst().orElse(null);
            if (bal != null) {
                bal.setPendingDays(bal.getPendingDays().subtract(sickDays));
                bal.setUsedDays(bal.getUsedDays().add(sickDays));
                bal.setAvailableDays(bal.getTotalAllocated().subtract(bal.getUsedDays()).subtract(bal.getPendingDays()));
                leaveBalanceRepository.save(bal);
            }
        }
    }

    private void restoreBalance(UUID employeeId, LeaveType type, BigDecimal days) {
        if (days.compareTo(BigDecimal.ZERO) > 0) {
            LeaveBalance bal = leaveBalanceRepository.findByEmployeeIdAndLeaveType(employeeId, type)
                    .stream().findFirst().orElse(null);
            if (bal != null) {
                bal.setUsedDays(bal.getUsedDays().subtract(days));
                bal.setAvailableDays(bal.getTotalAllocated().subtract(bal.getUsedDays()).subtract(bal.getPendingDays()));
                leaveBalanceRepository.save(bal);
            }
        }
    }

    private void restorePendingDays(UUID employeeId, LeaveType type, BigDecimal paidDays, BigDecimal sickDays) {
        if (paidDays.compareTo(BigDecimal.ZERO) > 0) {
            LeaveBalance bal = leaveBalanceRepository.findByEmployeeIdAndLeaveType(employeeId, LeaveType.PAID)
                    .stream().findFirst().orElse(null);
            if (bal != null) {
                bal.setPendingDays(bal.getPendingDays().subtract(paidDays));
                bal.setAvailableDays(bal.getTotalAllocated().subtract(bal.getUsedDays()).subtract(bal.getPendingDays()));
                leaveBalanceRepository.save(bal);
            }
        }
        if (sickDays.compareTo(BigDecimal.ZERO) > 0) {
            LeaveBalance bal = leaveBalanceRepository.findByEmployeeIdAndLeaveType(employeeId, LeaveType.SICK)
                    .stream().findFirst().orElse(null);
            if (bal != null) {
                bal.setPendingDays(bal.getPendingDays().subtract(sickDays));
                bal.setAvailableDays(bal.getTotalAllocated().subtract(bal.getUsedDays()).subtract(bal.getPendingDays()));
                leaveBalanceRepository.save(bal);
            }
        }
    }

    private void createTransaction(LeaveRequest lr, LeaveType type, BigDecimal daysChange, String txnType, String desc) {
        LeaveBalance bal = leaveBalanceRepository.findByEmployeeIdAndLeaveType(lr.getEmployee().getId(), type)
                .stream().findFirst().orElse(null);
        LeaveTransaction tx = LeaveTransaction.builder()
                .leaveRequest(lr)
                .legalEntity(lr.getLegalEntity())
                .employee(lr.getEmployee())
                .leaveType(type)
                .daysChange(daysChange)
                .balanceBefore(bal != null ? bal.getAvailableDays() : BigDecimal.ZERO)
                .balanceAfter(bal != null ? bal.getAvailableDays().subtract(daysChange) : BigDecimal.ZERO)
                .transactionType(txnType)
                .description(desc)
                .status(Status.ACTIVE)
                .build();
        leaveTransactionRepository.save(tx);
    }

    private LeaveBalanceDto toBalanceDto(LeaveBalance entity) {
        return LeaveBalanceDto.builder()
                .id(entity.getId())
                .legalEntityId(entity.getLegalEntity() != null ? entity.getLegalEntity().getId() : null)
                .employeeId(entity.getEmployee() != null ? entity.getEmployee().getId() : null)
                .employeeName(entity.getEmployee() != null
                        ? entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName() : null)
                .leaveType(entity.getLeaveType())
                .totalAllocated(entity.getTotalAllocated())
                .usedDays(entity.getUsedDays())
                .pendingDays(entity.getPendingDays())
                .availableDays(entity.getAvailableDays())
                .accrualStartDate(entity.getAccrualStartDate())
                .fiscalYearSettingId(entity.getFiscalYearSetting() != null ? entity.getFiscalYearSetting().getId() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
