package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.EmployeeAssignmentStatus;
import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.entity.FiscalYearSetting;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.CmEmployeeEntityAssignmentRepository;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import com.af.novadesk.api.payroll.constants.LeaveType;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.entity.PayrollDetails;
import com.af.novadesk.api.payroll.exception.AuthHubIntegrationException;
import com.af.novadesk.api.payroll.exception.DuplicateEmployeeException;
import com.af.novadesk.api.payroll.exception.EmployeeNotFoundException;
import com.af.novadesk.api.payroll.mapper.EmployeeMapper;
import com.af.novadesk.api.payroll.repository.EmployeeRepository;
import com.af.novadesk.api.payroll.repository.LeaveBalanceRepository;
import com.af.novadesk.api.payroll.repository.PayrollDetailsRepository;
import com.af.novadesk.api.payroll.service.AuthHubClientService;
import com.af.novadesk.api.payroll.service.EmployeeService;
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
public class EmployeeServiceImpl implements EmployeeService {

    private static final BigDecimal DEFAULT_PAID_LEAVE = BigDecimal.valueOf(10);
    private static final BigDecimal DEFAULT_SICK_LEAVE = BigDecimal.valueOf(5);
    private static final BigDecimal UNLIMITED_UNPAID = BigDecimal.valueOf(999);

    private final EmployeeRepository                    employeeRepository;
    private final ShadowUserRepository                  shadowUserRepository;
    private final LegalEntityRepository                 legalEntityRepository;
    private final LeaveBalanceRepository                leaveBalanceRepository;
    private final FiscalYearSettingRepository           fiscalYearSettingRepository;
    private final CmEmployeeRepository                  cmEmployeeRepository;
    private final CmEmployeeEntityAssignmentRepository  cmAssignmentRepository;
    private final PayrollDetailsRepository              payrollDetailsRepository;
    private final EmployeeMapper                        mapper;
    private final IdentitySecurityContext               identitySecurityContext;
    private final AuthHubClientService                  authHubClientService;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               ShadowUserRepository shadowUserRepository,
                               LegalEntityRepository legalEntityRepository,
                               LeaveBalanceRepository leaveBalanceRepository,
                               FiscalYearSettingRepository fiscalYearSettingRepository,
                               CmEmployeeRepository cmEmployeeRepository,
                               CmEmployeeEntityAssignmentRepository cmAssignmentRepository,
                               PayrollDetailsRepository payrollDetailsRepository,
                               EmployeeMapper mapper,
                               IdentitySecurityContext identitySecurityContext,
                               AuthHubClientService authHubClientService) {
        this.employeeRepository       = employeeRepository;
        this.shadowUserRepository     = shadowUserRepository;
        this.legalEntityRepository    = legalEntityRepository;
        this.leaveBalanceRepository   = leaveBalanceRepository;
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
        this.cmEmployeeRepository     = cmEmployeeRepository;
        this.cmAssignmentRepository   = cmAssignmentRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.mapper                   = mapper;
        this.identitySecurityContext  = identitySecurityContext;
        this.authHubClientService     = authHubClientService;
    }

    @Override
    public EmployeeDto onboardEmployee(EmployeeDto request) {
        LegalEntity legalEntity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.getLegalEntityId()));

        ShadowUser shadowUser;

        if (request.getShadowUserId() != null) {
            // ── OLD FLOW: ShadowUser already exists (backward compatible) ──
            shadowUser = shadowUserRepository.findById(request.getShadowUserId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.getShadowUserId()));
            checkDuplicateEmployee(shadowUser.getAuthUserId(), legalEntity.getId());
        } else {
            // ── NEW REVERSED FLOW: Register in AuthHub first, then create ShadowUser ──
            UUID orgId = identitySecurityContext.getOrganizationId();

            // Check email uniqueness locally before calling AuthHub
            if (request.getEmail() != null) {
                shadowUserRepository.findByEmail(request.getEmail())
                        .ifPresent(u -> { throw new DuplicateEmployeeException(
                                "Email already exists: " + request.getEmail()); });
            }

            // Call af-authhub signup — AuthHub assigns the UUID (throws AuthHubIntegrationException on failure)
            UUID authUserId = authHubClientService.createUser(
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    orgId
            );

            // Create ShadowUser using AuthHub's assigned UUID
            shadowUser = ShadowUser.builder()
                    .authUserId(authUserId)
                    .organizationId(orgId)
                    .email(request.getEmail())
                    .displayName(request.getFirstName() + " " + request.getLastName())
                    .lastSyncedAt(LocalDateTime.now())
                    .status(Status.ACTIVE)
                    .build();
            shadowUser = shadowUserRepository.save(shadowUser);
        }

        // ── Common: Create thin Employee (payroll record) ──
        final String salaryCurrency = (request.getSalaryCurrency() != null && !request.getSalaryCurrency().isBlank())
                ? request.getSalaryCurrency() : legalEntity.getBaseCurrency();
        final UUID   empOrgId      = shadowUser.getOrganizationId();
        final LegalEntity finalEntity = legalEntity;

        Employee employee = new Employee();
        employee.setOrganizationId(empOrgId);
        employee.setStatus(Status.ACTIVE);

        if (request.getManagerId() != null) {
            Employee manager = employeeRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.getManagerId()));
            employee.setManager(manager);
        }

        // ── Dual-write to common module tables (identity, assignment, payroll) ──
        final ShadowUser finalShadowUser = shadowUser; // capture effectively-final for lambdas

        // 1. CmEmployee — canonical identity record
        CmEmployee cmEmployee = cmEmployeeRepository
                .findByAuthUserIdAndOrganizationId(finalShadowUser.getAuthUserId(), empOrgId)
                .orElseGet(() -> {
                    CmEmployee newCm = CmEmployee.builder()
                            .organizationId(empOrgId)
                            .authUserId(finalShadowUser.getAuthUserId())
                            .employeeCode(request.getEmployeeCode())
                            .displayName(request.getFirstName() + " " + request.getLastName())
                            .email(request.getEmail())
                            .employeeStatus(EmployeeStatus.ACTIVE)
                            .build();
                    return cmEmployeeRepository.save(newCm);
                });

        // Link thin Employee to its canonical CmEmployee record
        employee.setCmEmployeeId(cmEmployee.getId());
        employee = employeeRepository.save(employee);

        // 2. CmEmployeeEntityAssignment
        final boolean isFirstAssignment = !cmAssignmentRepository
                .existsByEmployeeIdAndLegalEntityId(cmEmployee.getId(), finalEntity.getId());
        final CmEmployee finalCmEmployee = cmEmployee;

        CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(finalCmEmployee.getId(), finalEntity.getId())
                .orElseGet(() -> {
                    CmEmployeeEntityAssignment newAssignment = CmEmployeeEntityAssignment.builder()
                            .employee(finalCmEmployee)
                            .legalEntity(finalEntity)
                            .organizationId(empOrgId)
                            .department(request.getDepartment())
                            .designation(request.getDesignation())
                            .primaryEntity(isFirstAssignment)
                            .hireDate(request.getHireDate())
                            .terminationDate(request.getTerminationDate())
                            .assignmentStatus(EmployeeAssignmentStatus.ACTIVE)
                            .build();
                    return cmAssignmentRepository.save(newAssignment);
                });

        // 3. PayrollDetails — one per employee
        if (!payrollDetailsRepository.existsByEmployeeId(finalCmEmployee.getId())) {
            String finalCurrency = salaryCurrency;
            PayrollDetails payrollDetails = PayrollDetails.builder()
                    .employeeId(finalCmEmployee.getId())
                    .entityAssignmentId(assignment.getId())
                    .baseSalary(request.getBaseSalary())
                    .salaryCurrency(finalCurrency)
                    .bankAccountNumber(request.getBankAccountNumber())
                    .bankName(request.getBankName())
                    .bankIfscCode(request.getBankIfscCode())
                    .build();
            payrollDetailsRepository.save(payrollDetails);
        }

        // Auto-create leave balances
        FiscalYearSetting fiscalYear = fiscalYearSettingRepository.findByLegalEntityId(legalEntity.getId())
                .stream().findFirst().orElse(null);
        if (fiscalYear != null) {
            createLeaveBalance(employee, LeaveType.PAID, DEFAULT_PAID_LEAVE, fiscalYear, legalEntity);
            createLeaveBalance(employee, LeaveType.SICK, DEFAULT_SICK_LEAVE, fiscalYear, legalEntity);
            createLeaveBalance(employee, LeaveType.UNPAID, UNLIMITED_UNPAID, fiscalYear, legalEntity);
        }

        return mapper.toDto(employee);
    }

    private void createLeaveBalance(Employee employee, LeaveType type, BigDecimal allocated,
                                    FiscalYearSetting fiscalYear, LegalEntity legalEntity) {
        LeaveBalance balance = LeaveBalance.builder()
                .organizationId(employee.getOrganizationId())
                .employee(employee)
                .leaveType(type)
                .totalAllocated(allocated)
                .usedDays(BigDecimal.ZERO)
                .pendingDays(BigDecimal.ZERO)
                .availableDays(allocated)
                .accrualStartDate(java.time.LocalDate.now()) // TODO: get from cm_employee_entity_assignment
                .fiscalYearSetting(fiscalYear)
                .status(Status.ACTIVE)
                .build();
        leaveBalanceRepository.save(balance);
    }

    private void checkDuplicateEmployee(UUID authUserId, UUID legalEntityId) {
        UUID orgId = identitySecurityContext.getOrganizationId();
        cmEmployeeRepository.findByAuthUserIdAndOrganizationId(authUserId, orgId)
                .ifPresent(cm -> {
                    if (cmAssignmentRepository.existsByEmployeeIdAndLegalEntityId(cm.getId(), legalEntityId)) {
                        throw new DuplicateEmployeeException(authUserId, legalEntityId);
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDto getEmployee(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        return mapper.toDto(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDto getEmployeeByAuthUserAndEntity(UUID authUserId, UUID legalEntityId) {
        UUID orgId = identitySecurityContext.getOrganizationId();
        CmEmployee cm = cmEmployeeRepository.findByAuthUserIdAndOrganizationId(authUserId, orgId)
                .orElseThrow(() -> new EmployeeNotFoundException(authUserId, legalEntityId));
        Employee employee = employeeRepository.findByCmEmployeeId(cm.getId())
                .orElseThrow(() -> new EmployeeNotFoundException(authUserId, legalEntityId));
        return mapper.toDto(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDto getEmployeeByCode(String employeeCode, UUID legalEntityId) {
        CmEmployee cm = cmEmployeeRepository.findByEmployeeCodeAndLegalEntityId(employeeCode, legalEntityId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeCode, legalEntityId));
        Employee employee = employeeRepository.findByCmEmployeeId(cm.getId())
                .orElseThrow(() -> new EmployeeNotFoundException(employeeCode, legalEntityId));
        return mapper.toDto(employee);
    }

    @Override
    public EmployeeDto updateEmployee(UUID employeeId, EmployeeDto request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        mapper.updateEntity(employee, request);
        if (request.getManagerId() != null) {
            Employee manager = employeeRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.getManagerId()));
            employee.setManager(manager);
        }
        employee = employeeRepository.save(employee);
        return mapper.toDto(employee);
    }

    @Override
    public void terminateEmployee(UUID employeeId, LocalDate terminationDate) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        employee.setStatus(Status.INACTIVE);
        employeeRepository.save(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDto> listAllEmployees() {
        return employeeRepository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDto> listEmployeesByEntity(UUID legalEntityId) {
        return employeeRepository.findByLegalEntityId(legalEntityId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDto> listEmployeesByManager(UUID managerId) {
        return employeeRepository.findByManagerId(managerId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }
}
