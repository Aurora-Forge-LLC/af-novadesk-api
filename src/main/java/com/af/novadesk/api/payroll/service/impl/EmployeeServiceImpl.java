package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import com.af.novadesk.api.payroll.constants.LeaveType;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.exception.AuthHubIntegrationException;
import com.af.novadesk.api.payroll.exception.DuplicateEmployeeException;
import com.af.novadesk.api.payroll.exception.EmployeeNotFoundException;
import com.af.novadesk.api.payroll.mapper.EmployeeMapper;
import com.af.novadesk.api.payroll.repository.EmployeeRepository;
import com.af.novadesk.api.payroll.repository.LeaveBalanceRepository;
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

    private final EmployeeRepository employeeRepository;
    private final ShadowUserRepository shadowUserRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final FiscalYearSettingRepository fiscalYearSettingRepository;
    private final EmployeeMapper mapper;
    private final IdentitySecurityContext identitySecurityContext;
    private final AuthHubClientService authHubClientService;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               ShadowUserRepository shadowUserRepository,
                               LegalEntityRepository legalEntityRepository,
                               LeaveBalanceRepository leaveBalanceRepository,
                               FiscalYearSettingRepository fiscalYearSettingRepository,
                               EmployeeMapper mapper,
                               IdentitySecurityContext identitySecurityContext,
                               AuthHubClientService authHubClientService) {
        this.employeeRepository = employeeRepository;
        this.shadowUserRepository = shadowUserRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
        this.mapper = mapper;
        this.identitySecurityContext = identitySecurityContext;
        this.authHubClientService = authHubClientService;
    }

    @Override
    public EmployeeDto onboardEmployee(EmployeeDto request) {
        LegalEntity legalEntity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new RuntimeException("Legal entity not found: " + request.getLegalEntityId()));

        ShadowUser shadowUser;

        if (request.getShadowUserId() != null) {
            // ── OLD FLOW: ShadowUser already exists (backward compatible) ──
            shadowUser = shadowUserRepository.findById(request.getShadowUserId())
                    .orElseThrow(() -> new RuntimeException("Shadow user not found: " + request.getShadowUserId()));
            checkDuplicateEmployee(shadowUser.getAuthUserId(), legalEntity.getId());
        } else {
            // ── NEW REVERSED FLOW: Create ShadowUser + call AuthHub ──
            UUID orgId = identitySecurityContext.getOrganizationId();
            UUID preGenAuthUserId = UUID.randomUUID();

            // Check email uniqueness
            if (request.getEmail() != null) {
                shadowUserRepository.findByEmail(request.getEmail())
                        .ifPresent(u -> { throw new DuplicateEmployeeException(
                                "Email already exists: " + request.getEmail()); });
            }

            // Create ShadowUser with pre-generated authUserId
            shadowUser = ShadowUser.builder()
                    .authUserId(preGenAuthUserId)
                    .organizationId(orgId)
                    .email(request.getEmail())
                    .displayName(request.getFirstName() + " " + request.getLastName())
                    .lastSyncedAt(LocalDateTime.now())
                    .status(Status.ACTIVE)
                    .build();
            shadowUser = shadowUserRepository.save(shadowUser);

            // Call af-authhub to create the user (throws AuthHubIntegrationException on failure)
            // On failure, the @Transactional annotation ensures the ShadowUser is rolled back
            authHubClientService.createUser(
                    preGenAuthUserId,
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    orgId
            );
        }

        // ── Common: Create Employee ──
        Employee employee = new Employee();
        employee.setShadowUser(shadowUser);
        employee.setOrganizationId(shadowUser.getOrganizationId());
        employee.setAuthUserId(shadowUser.getAuthUserId());
        employee.setLegalEntity(legalEntity);
        employee.setEmployeeCode(request.getEmployeeCode());
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setEmail(request.getEmail() != null ? request.getEmail() : shadowUser.getEmail());
        employee.setDepartment(request.getDepartment());
        employee.setDesignation(request.getDesignation());
        employee.setHireDate(request.getHireDate());
        employee.setBaseSalary(request.getBaseSalary());
        employee.setSalaryCurrency(request.getSalaryCurrency());
        employee.setBankAccountNumber(request.getBankAccountNumber());
        employee.setBankName(request.getBankName());
        employee.setBankIfscCode(request.getBankIfscCode());
        employee.setStatus(Status.ACTIVE);

        // Manager assignment
        if (request.getManagerId() != null) {
            Employee manager = employeeRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.getManagerId()));
            employee.setManager(manager);
        }

        employee = employeeRepository.save(employee);

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
                .legalEntity(legalEntity)
                .employee(employee)
                .leaveType(type)
                .totalAllocated(allocated)
                .usedDays(BigDecimal.ZERO)
                .pendingDays(BigDecimal.ZERO)
                .availableDays(allocated)
                .accrualStartDate(employee.getHireDate())
                .fiscalYearSetting(fiscalYear)
                .status(Status.ACTIVE)
                .build();
        leaveBalanceRepository.save(balance);
    }

    private void checkDuplicateEmployee(UUID authUserId, UUID legalEntityId) {
        employeeRepository.findByAuthUserIdAndLegalEntityId(authUserId, legalEntityId)
                .ifPresent(e -> { throw new DuplicateEmployeeException(authUserId, legalEntityId); });
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
        Employee employee = employeeRepository.findByAuthUserIdAndLegalEntityId(authUserId, legalEntityId)
                .orElseThrow(() -> new EmployeeNotFoundException(authUserId, legalEntityId));
        return mapper.toDto(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDto getEmployeeByCode(String employeeCode, UUID legalEntityId) {
        Employee employee = employeeRepository.findByAuthUserIdAndLegalEntityId(null, legalEntityId)
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
        employee.setTerminationDate(terminationDate);
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
