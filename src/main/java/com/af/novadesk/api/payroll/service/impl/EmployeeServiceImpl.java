package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.event.EmployeeOnboardedEvent;
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
import com.af.novadesk.api.payroll.service.EmployeeOutboxService;
import com.af.novadesk.api.payroll.service.EmployeeService;
import org.springframework.context.ApplicationEventPublisher;
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
    private final EmployeeOutboxService employeeOutboxService;
    private final ApplicationEventPublisher eventPublisher;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               ShadowUserRepository shadowUserRepository,
                               LegalEntityRepository legalEntityRepository,
                               LeaveBalanceRepository leaveBalanceRepository,
                               FiscalYearSettingRepository fiscalYearSettingRepository,
                               EmployeeMapper mapper,
                               IdentitySecurityContext identitySecurityContext,
                               AuthHubClientService authHubClientService,
                               EmployeeOutboxService employeeOutboxService,
                               ApplicationEventPublisher eventPublisher) {
        this.employeeRepository = employeeRepository;
        this.shadowUserRepository = shadowUserRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
        this.mapper = mapper;
        this.identitySecurityContext = identitySecurityContext;
        this.authHubClientService = authHubClientService;
        this.employeeOutboxService = employeeOutboxService;
        this.eventPublisher = eventPublisher;
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
            // ── NEW REVERSED FLOW: Call AuthHub first, then persist ShadowUser ──
            UUID orgId = identitySecurityContext.getOrganizationId();
            UUID preGenAuthUserId = AuthHubClientService.deriveUserId(
                    request.getEmail(), orgId);

            // Check email uniqueness against local ShadowUser table
            if (request.getEmail() != null) {
                shadowUserRepository.findByEmail(request.getEmail())
                        .ifPresent(u -> { throw new DuplicateEmployeeException(
                                "Email already exists: " + request.getEmail()); });
            }

            // Call af-authhub FIRST to provision the user.
            // Uses a deterministic userId (email+orgId hash) so the call is
            // idempotent: a 409 from a previous rolled-back attempt is handled
            // gracefully inside createUser().
            authHubClientService.createUser(
                    preGenAuthUserId,
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    orgId
            );

            // Now persist the ShadowUser. If this transaction rolls back later,
            // the AuthHub user remains (idempotent userId ensures retries work).
            shadowUser = ShadowUser.builder()
                    .authUserId(preGenAuthUserId)
                    .organizationId(orgId)
                    .email(request.getEmail())
                    .displayName(request.getFirstName() + " " + request.getLastName())
                    .lastSyncedAt(LocalDateTime.now())
                    .status(Status.ACTIVE)
                    .build();
            shadowUser = shadowUserRepository.save(shadowUser);
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
        // Auto-derive salaryCurrency from the entity's base currency if not provided;
        // allows override for multi-currency edge cases (e.g. expat employees).
        String salaryCurrency = request.getSalaryCurrency();
        if (salaryCurrency == null || salaryCurrency.isBlank()) {
            salaryCurrency = legalEntity.getBaseCurrency();
        }
        employee.setSalaryCurrency(salaryCurrency);
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

        // Assign manager role if requested
        boolean isManager = Boolean.TRUE.equals(request.getIsManager());
        String entityRole = isManager ? "MANAGER" : "VIEWER";

        if (isManager) {
            UUID managerUuid = UUID.randomUUID();
            employee.setManagerUuid(managerUuid);
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

        // Persist outbox event (same @Transactional boundary)
        employeeOutboxService.createOnboardedEvent(employee, entityRole, isManager);

        // Publish ApplicationEvent for cross-module consumers (Finance)
        EmployeeOnboardedEvent event = new EmployeeOnboardedEvent(
                employee.getId(),
                employee.getAuthUserId(),
                employee.getOrganizationId(),
                legalEntity.getId(),
                employee.getEmployeeCode(),
                employee.getFirstName() + " " + employee.getLastName(),
                employee.getEmail(),
                isManager,
                entityRole
        );
        eventPublisher.publishEvent(event);

        EmployeeDto result = mapper.toDto(employee);
        result.setIsManager(isManager);
        result.setManagerUuid(employee.getManagerUuid());
        return result;
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

        // Handle manager role toggle (isManager field)
        String entityRole = null;
        boolean roleChanged = false;
        if (request.getIsManager() != null) {
            boolean isManager = Boolean.TRUE.equals(request.getIsManager());
            boolean currentlyIsManager = employee.getManagerUuid() != null;
            if (isManager && !currentlyIsManager) {
                // Promote to MANAGER role
                employee.setManagerUuid(UUID.randomUUID());
                entityRole = "MANAGER";
                roleChanged = true;
            } else if (!isManager && currentlyIsManager) {
                // Demote to VIEWER role
                employee.setManagerUuid(null);
                entityRole = "VIEWER";
                roleChanged = true;
            }
        }

        employee = employeeRepository.save(employee);

        // Persist outbox event if manager role changed
        if (roleChanged && entityRole != null) {
            employeeOutboxService.createRoleChangedEvent(employee, entityRole, "MANAGER".equals(entityRole));
        }

        EmployeeDto result = mapper.toDto(employee);
        result.setIsManager(employee.getManagerUuid() != null);
        result.setManagerUuid(employee.getManagerUuid());
        return result;
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

    @Override
    @Transactional(readOnly = true)
    public EmployeeDto getCurrentEmployee(UUID legalEntityId) {
        UUID authUserId = identitySecurityContext.getAuthUserId();
        Employee employee = employeeRepository.findByAuthUserIdAndLegalEntityId(authUserId, legalEntityId)
                .orElseThrow(() -> new EmployeeNotFoundException(authUserId, legalEntityId));
        EmployeeDto dto = mapper.toDto(employee);
        dto.setIsManager(employee.getManagerUuid() != null);
        dto.setManagerUuid(employee.getManagerUuid());
        return dto;
    }
}
