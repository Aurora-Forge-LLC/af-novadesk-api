package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.payroll.dto.LeavePolicyDto;
import com.af.novadesk.api.payroll.dto.LeavePolicyRequest;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.entity.LeavePolicy;
import com.af.novadesk.api.payroll.exception.LeavePolicyDuplicateException;
import com.af.novadesk.api.payroll.exception.LeavePolicyNotFoundException;
import com.af.novadesk.api.payroll.repository.EmployeeRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LeavePolicyServiceImpl implements LeavePolicyService {

    private static final Logger log = LoggerFactory.getLogger(LeavePolicyServiceImpl.class);

    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final FiscalYearSettingRepository fiscalYearSettingRepository;

    public LeavePolicyServiceImpl(LeavePolicyRepository leavePolicyRepository,
                                  LeaveBalanceRepository leaveBalanceRepository,
                                  EmployeeRepository employeeRepository,
                                  LegalEntityRepository legalEntityRepository,
                                  FiscalYearSettingRepository fiscalYearSettingRepository) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeRepository = employeeRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
    }

    @Override
    public LeavePolicyDto createPolicy(LeavePolicyRequest request) {
        LegalEntity entity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new IllegalArgumentException("Legal entity not found: " + request.getLegalEntityId()));

        // Check for duplicate name within entity
        leavePolicyRepository.findByLegalEntityIdAndName(request.getLegalEntityId(), request.getName())
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
        policy.setStatus(Status.INACTIVE);
        leavePolicyRepository.save(policy);
        log.info("Soft-deleted leave policy '{}' (id={})", policy.getName(), policyId);
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
        List<Employee> employees = employeeRepository.findByLegalEntityId(legalEntityId);
        FiscalYearSetting fiscalYear = getCurrentFiscalYear(legalEntityId);

        if (fiscalYear == null) {
            log.warn("No fiscal year setting found for entity {}, skipping balance generation", legalEntityId);
            return;
        }

        int created = 0;
        LocalDate today = LocalDate.now();
        for (Employee emp : employees) {
            if (emp.getTerminationDate() != null && emp.getTerminationDate().isBefore(today)) {
                continue; // skip terminated employees
            }

            Optional<LeaveBalance> existing = leaveBalanceRepository
                    .findByEmployeeIdAndLeavePolicyIdAndFiscalYearSettingId(
                            emp.getId(), policyId, fiscalYear.getId());

            if (existing.isEmpty()) {
                LeaveBalance balance = LeaveBalance.builder()
                        .legalEntity(policy.getLegalEntity())
                        .employee(emp)
                        .leavePolicy(policy)
                        .totalAllocated(policy.getAllowedDaysAsDecimal())
                        .usedDays(BigDecimal.ZERO)
                        .pendingDays(BigDecimal.ZERO)
                        .availableDays(policy.getIsEarned() ? BigDecimal.ZERO : policy.getAllowedDaysAsDecimal())
                        .earnedDays(policy.getIsEarned() ? BigDecimal.ZERO : policy.getAllowedDaysAsDecimal())
                        .accrualStartDate(emp.getHireDate())
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
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

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
                        .legalEntity(policy.getLegalEntity())
                        .employee(employee)
                        .leavePolicy(policy)
                        .totalAllocated(policy.getAllowedDaysAsDecimal())
                        .usedDays(BigDecimal.ZERO)
                        .pendingDays(BigDecimal.ZERO)
                        .availableDays(policy.getIsEarned() ? BigDecimal.ZERO : policy.getAllowedDaysAsDecimal())
                        .earnedDays(policy.getIsEarned() ? BigDecimal.ZERO : policy.getAllowedDaysAsDecimal())
                        .accrualStartDate(employee.getHireDate())
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
