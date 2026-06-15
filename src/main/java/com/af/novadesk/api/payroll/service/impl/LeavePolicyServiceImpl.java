package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.repository.CmEmployeeEntityAssignmentRepository;
import com.af.novadesk.api.common.entity.FiscalYearSetting;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.payroll.dto.LeavePolicyDto;
import com.af.novadesk.api.payroll.dto.LeavePolicyRequest;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.entity.LeavePolicy;
import com.af.novadesk.api.payroll.exception.LeavePolicyDuplicateException;
import com.af.novadesk.api.payroll.exception.LeavePolicyNotFoundException;
import com.af.novadesk.api.payroll.repository.LeaveBalanceRepository;
import com.af.novadesk.api.payroll.repository.LeavePolicyRepository;
import com.af.novadesk.api.payroll.service.LeavePolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class LeavePolicyServiceImpl implements LeavePolicyService {

    private static final Logger log = LoggerFactory.getLogger(LeavePolicyServiceImpl.class);

    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final CmEmployeeRepository cmEmployeeRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final FiscalYearSettingRepository fiscalYearSettingRepository;
    private final CmEmployeeEntityAssignmentRepository cmAssignmentRepository;

    public LeavePolicyServiceImpl(LeavePolicyRepository leavePolicyRepository,
                                  LeaveBalanceRepository leaveBalanceRepository,
                                  CmEmployeeRepository cmEmployeeRepository,
                                  LegalEntityRepository legalEntityRepository,
                                  FiscalYearSettingRepository fiscalYearSettingRepository,
                                  CmEmployeeEntityAssignmentRepository cmAssignmentRepository) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.cmEmployeeRepository = cmEmployeeRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
        this.cmAssignmentRepository = cmAssignmentRepository;
    }

    @Override
    public LeavePolicyDto createPolicy(LeavePolicyRequest request) {
        LegalEntity entity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new IllegalArgumentException("Legal entity not found: " + request.getLegalEntityId()));

        // Check for duplicate name within entity (only among ACTIVE policies)
        leavePolicyRepository.findByLegalEntityIdAndNameAndStatus(
                request.getLegalEntityId(), request.getName(), Status.ACTIVE)
                .ifPresent(p -> {
                    throw new LeavePolicyDuplicateException(request.getName(), request.getLegalEntityId());
                });

        LeavePolicy policy = LeavePolicy.builder()
                .legalEntity(entity)
                .name(request.getName())
                .paymentType(request.getPaymentType())
                .allowedDays(request.getAllowedDays())
                .isUnlimited(request.getIsUnlimited())
                .isEarned(request.getIsEarned())
                .borrowMultiple(request.getBorrowMultiple())
                .createdBy(request.getLegalEntityId()) // TODO: get from JWT context
                .status(Status.ACTIVE)
                .build();

        policy = leavePolicyRepository.save(policy);
        log.info("Created leave policy '{}' for entity {}", policy.getName(), entity.getId());

        // Generate balance sheets for all active employees
        generateBalanceSheetsForPolicy(policy.getId());

        return toDto(policy);
    }

    @Override
    public LeavePolicyDto updatePolicy(UUID policyId, LeavePolicyRequest request) {
        LeavePolicy policy = leavePolicyRepository.findById(policyId)
                .orElseThrow(() -> new LeavePolicyNotFoundException(policyId));

        // Name and paymentType are immutable after creation
        policy.setAllowedDays(request.getAllowedDays());
        policy.setIsUnlimited(request.getIsUnlimited());
        policy.setIsEarned(request.getIsEarned());
        policy.setBorrowMultiple(request.getBorrowMultiple());

        policy = leavePolicyRepository.save(policy);
        log.info("Updated leave policy '{}' (id={})", policy.getName(), policyId);

        // Update existing balance sheets' totalAllocated
        List<LeaveBalance> balances = leaveBalanceRepository.findByLeavePolicyId(policyId);
        for (LeaveBalance balance : balances) {
            balance.setTotalAllocated(policy.getAllowedDaysAsDecimal());
            if (!policy.getIsEarned()) {
                balance.setEarnedDays(policy.getAllowedDaysAsDecimal());
            }
            leaveBalanceRepository.save(balance);
        }

        return toDto(policy);
    }

    @Override
    public void deletePolicy(UUID policyId) {
        LeavePolicy policy = leavePolicyRepository.findById(policyId)
                .orElseThrow(() -> new LeavePolicyNotFoundException(policyId));

        // Soft-delete unused leave balances for this policy.
        // A balance is "unused" only when no days have been taken and none are pending.
        List<LeaveBalance> balances = leaveBalanceRepository.findByLeavePolicyId(policyId);
        int deletedBalances = 0;
        int retainedBalances = 0;
        for (LeaveBalance balance : balances) {
            if (balance.getUsedDays().compareTo(BigDecimal.ZERO) == 0
                    && balance.getPendingDays().compareTo(BigDecimal.ZERO) == 0) {
                balance.setStatus(Status.INACTIVE);
                leaveBalanceRepository.save(balance);
                deletedBalances++;
            } else {
                retainedBalances++;
            }
        }

        policy.setStatus(Status.INACTIVE);
        leavePolicyRepository.save(policy);
        log.info("Soft-deleted leave policy '{}' (id={}); deleted {} unused balances, retained {} balances with usage",
                policy.getName(), policyId, deletedBalances, retainedBalances);
    }

    @Override
    @Transactional(readOnly = true)
    public LeavePolicyDto getPolicy(UUID policyId) {
        LeavePolicy policy = leavePolicyRepository.findById(policyId)
                .orElseThrow(() -> new LeavePolicyNotFoundException(policyId));
        return toDto(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeavePolicyDto> listPoliciesByEntity(UUID legalEntityId) {
        return leavePolicyRepository.findByLegalEntityIdAndStatus(legalEntityId, Status.ACTIVE)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeavePolicyDto> listPoliciesByOrganization(UUID organizationId) {
        return leavePolicyRepository.findByLegalEntityOrganizationId(organizationId)
                .stream()
                .filter(p -> p.getStatus() == Status.ACTIVE)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public void generateBalanceSheetsForPolicy(UUID policyId) {
        LeavePolicy policy = leavePolicyRepository.findById(policyId)
                .orElseThrow(() -> new LeavePolicyNotFoundException(policyId));

        if (policy.getStatus() == Status.INACTIVE) {
            log.warn("Skipping balance sheet generation for inactive policy {}", policyId);
            return;
        }

        UUID legalEntityId = policy.getLegalEntity().getId();
        List<CmEmployee> employees = cmEmployeeRepository.findAllByLegalEntityIdAndStatus(
                legalEntityId, com.af.novadesk.api.common.constants.EmployeeStatus.ACTIVE);
        FiscalYearSetting fiscalYear = getCurrentFiscalYear(legalEntityId);

        if (fiscalYear == null) {
            log.warn("No fiscal year setting found for entity {}, skipping balance generation", legalEntityId);
            return;
        }

        // Resolve termination/hire dates from cm_employee_entity_assignments
        Map<UUID, CmEmployeeEntityAssignment> assignmentByCmEmployeeId = buildAssignmentMap(employees, legalEntityId);

        int created = 0;
        LocalDate today = LocalDate.now();
        for (CmEmployee emp : employees) {
            CmEmployeeEntityAssignment assignment = assignmentByCmEmployeeId.get(emp.getId());

            // Skip terminated employees
            if (assignment != null
                    && assignment.getTerminationDate() != null
                    && assignment.getTerminationDate().isBefore(today)) {
                continue;
            }

            Optional<LeaveBalance> existing = leaveBalanceRepository
                    .findByEmployeeIdAndLeavePolicyIdAndFiscalYearSettingId(
                            emp.getId(), policyId, fiscalYear.getId());

            if (existing.isEmpty()) {
                LocalDate hireDate = assignment != null ? assignment.getHireDate() : today;

                LeaveBalance balance = LeaveBalance.builder()
                        .organizationId(emp.getOrganizationId())
                        .employee(emp)
                        .leaveType(com.af.novadesk.api.payroll.constants.LeaveType.PAID)
                        .leavePolicy(policy)
                        .totalAllocated(policy.getAllowedDaysAsDecimal())
                        .usedDays(BigDecimal.ZERO)
                        .pendingDays(BigDecimal.ZERO)
                        .availableDays(policy.getIsEarned() ? BigDecimal.ZERO : policy.getAllowedDaysAsDecimal())
                        .earnedDays(policy.getIsEarned() ? BigDecimal.ZERO : policy.getAllowedDaysAsDecimal())
                        .accrualStartDate(hireDate)
                        .fiscalYearSetting(fiscalYear)
                        .build();
                leaveBalanceRepository.save(balance);
                created++;
            }
        }

        log.info("Generated {} new leave balances for policy '{}' (entity={})",
                created, policy.getName(), legalEntityId);
    }

    @Override
    public void generateBalanceSheetsForEmployee(UUID employeeId, UUID legalEntityId) {
        CmEmployee employee = cmEmployeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        // Resolve hire date from cm_employee_entity_assignments
        LocalDate hireDate = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(employee.getId(), legalEntityId)
                .map(CmEmployeeEntityAssignment::getHireDate)
                .orElse(LocalDate.now());

        List<LeavePolicy> policies = leavePolicyRepository
                .findByLegalEntityIdAndStatus(legalEntityId, Status.ACTIVE);

        FiscalYearSetting fiscalYear = getCurrentFiscalYear(legalEntityId);
        if (fiscalYear == null) {
            log.warn("No fiscal year setting found for entity {}", legalEntityId);
            return;
        }

        for (LeavePolicy policy : policies) {
            Optional<LeaveBalance> existing = leaveBalanceRepository
                    .findByEmployeeIdAndLeavePolicyIdAndFiscalYearSettingId(
                            employeeId, policy.getId(), fiscalYear.getId());

            if (existing.isEmpty()) {
                LeaveBalance balance = LeaveBalance.builder()
                        .organizationId(employee.getOrganizationId())
                        .employee(employee)
                        .leaveType(com.af.novadesk.api.payroll.constants.LeaveType.PAID)
                        .leavePolicy(policy)
                        .totalAllocated(policy.getAllowedDaysAsDecimal())
                        .usedDays(BigDecimal.ZERO)
                        .pendingDays(BigDecimal.ZERO)
                        .availableDays(policy.getIsEarned() ? BigDecimal.ZERO : policy.getAllowedDaysAsDecimal())
                        .earnedDays(policy.getIsEarned() ? BigDecimal.ZERO : policy.getAllowedDaysAsDecimal())
                        .accrualStartDate(hireDate)
                        .fiscalYearSetting(fiscalYear)
                        .build();
                leaveBalanceRepository.save(balance);
            }
        }
    }

    // --- Private Helpers ---

    private FiscalYearSetting getCurrentFiscalYear(UUID legalEntityId) {
        return fiscalYearSettingRepository.findByLegalEntityId(legalEntityId)
                .stream().findFirst().orElse(null);
    }

    /**
     * Builds a map of cmEmployeeId → CmEmployeeEntityAssignment for the given
     * employees and legal entity, so termination/hire dates can be resolved
     * without N+1 queries.
     */
    private Map<UUID, CmEmployeeEntityAssignment> buildAssignmentMap(
            List<CmEmployee> employees, UUID legalEntityId) {

        List<UUID> cmEmployeeIds = employees.stream()
                .map(CmEmployee::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        if (cmEmployeeIds.isEmpty()) {
            return Map.of();
        }

        return cmAssignmentRepository
                .findByEmployeeIdInAndLegalEntityId(cmEmployeeIds, legalEntityId)
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getEmployee().getId(),
                        Function.identity(),
                        (existing, replacement) -> existing));
    }

    private LeavePolicyDto toDto(LeavePolicy entity) {
        return LeavePolicyDto.builder()
                .id(entity.getId())
                .legalEntityId(entity.getLegalEntity() != null ? entity.getLegalEntity().getId() : null)
                .name(entity.getName())
                .paymentType(entity.getPaymentType())
                .allowedDays(entity.getAllowedDays())
                .isUnlimited(entity.getIsUnlimited())
                .isEarned(entity.getIsEarned())
                .borrowMultiple(entity.getBorrowMultiple())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
