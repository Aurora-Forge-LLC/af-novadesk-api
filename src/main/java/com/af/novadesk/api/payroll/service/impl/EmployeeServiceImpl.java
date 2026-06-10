package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.EmployeeAssignmentStatus;
import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.exception.AuthHubIntegrationException;
import com.af.novadesk.api.common.exception.DuplicateEmployeeException;
import com.af.novadesk.api.common.exception.EmployeeNotFoundException;
import com.af.novadesk.api.common.repository.CmEmployeeEntityAssignmentRepository;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.common.service.AuthHubClientService;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.entity.PayrollDetails;
import com.af.novadesk.api.payroll.mapper.EmployeeMapper;
import com.af.novadesk.api.payroll.repository.PayrollDetailsRepository;
import com.af.novadesk.api.payroll.service.EmployeeService;
import com.af.novadesk.api.payroll.service.LeavePolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final CmEmployeeRepository                  cmEmployeeRepository;
    private final ShadowUserRepository                  shadowUserRepository;
    private final LegalEntityRepository                 legalEntityRepository;
    private final CmEmployeeEntityAssignmentRepository  cmAssignmentRepository;
    private final PayrollDetailsRepository              payrollDetailsRepository;
    private final EmployeeMapper                        mapper;
    private final IdentitySecurityContext               identitySecurityContext;
    private final AuthHubClientService                  authHubClientService;
    private final LeavePolicyService                    leavePolicyService;

    public EmployeeServiceImpl(CmEmployeeRepository cmEmployeeRepository,
                               ShadowUserRepository shadowUserRepository,
                               LegalEntityRepository legalEntityRepository,
                               CmEmployeeEntityAssignmentRepository cmAssignmentRepository,
                               PayrollDetailsRepository payrollDetailsRepository,
                               EmployeeMapper mapper,
                               IdentitySecurityContext identitySecurityContext,
                               AuthHubClientService authHubClientService,
                               LeavePolicyService leavePolicyService) {
        this.cmEmployeeRepository       = cmEmployeeRepository;
        this.shadowUserRepository       = shadowUserRepository;
        this.legalEntityRepository      = legalEntityRepository;
        this.cmAssignmentRepository     = cmAssignmentRepository;
        this.payrollDetailsRepository   = payrollDetailsRepository;
        this.mapper                     = mapper;
        this.identitySecurityContext    = identitySecurityContext;
        this.authHubClientService       = authHubClientService;
        this.leavePolicyService         = leavePolicyService;
    }

    @Override
    public EmployeeDto onboardEmployee(EmployeeDto request) {
        LegalEntity legalEntity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.getLegalEntityId()));

        final ShadowUser shadowUser;
        final UUID orgId;

        if (request.getShadowUserId() != null) {
            // OLD FLOW: ShadowUser already exists
            shadowUser = shadowUserRepository.findById(request.getShadowUserId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.getShadowUserId()));
            checkDuplicateEmployee(shadowUser.getAuthUserId(), legalEntity.getId());
            orgId = shadowUser.getOrganizationId();
        } else {
            // NEW REVERSED FLOW: Register in AuthHub first, then create ShadowUser
            orgId = identitySecurityContext.getOrganizationId();

            if (request.getEmail() != null) {
                shadowUserRepository.findByEmail(request.getEmail())
                        .ifPresent(u -> { throw new DuplicateEmployeeException(
                                "Email already exists: " + request.getEmail()); });
            }

            UUID authUserId = authHubClientService.createUser(
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    orgId
            );

            shadowUser = ShadowUser.builder()
                    .authUserId(authUserId)
                    .organizationId(orgId)
                    .email(request.getEmail())
                    .displayName(request.getFirstName() + " " + request.getLastName())
                    .lastSyncedAt(LocalDateTime.now())
                    .status(Status.ACTIVE)
                    .build();
            shadowUserRepository.save(shadowUser);
        }

        // Prepare employee data
        final String salaryCurrency = (request.getSalaryCurrency() != null && !request.getSalaryCurrency().isBlank())
                ? request.getSalaryCurrency() : legalEntity.getBaseCurrency();
        final UUID   empOrgId      = orgId;
        final LegalEntity finalEntity = legalEntity;

        // 1. CmEmployee — canonical identity record (create or get existing)
        CmEmployee cmEmployee = cmEmployeeRepository
                .findByAuthUserIdAndOrganizationId(shadowUser.getAuthUserId(), empOrgId)
                .orElseGet(() -> {
                    CmEmployee newCm = CmEmployee.builder()
                            .organizationId(empOrgId)
                            .authUserId(shadowUser.getAuthUserId())
                            .employeeCode(request.getEmployeeCode())
                            .displayName(request.getFirstName() + " " + request.getLastName())
                            .email(request.getEmail())
                            .employeeStatus(EmployeeStatus.ACTIVE)
                            .build();

                    // Set manager if provided
                    if (request.getManagerId() != null) {
                        CmEmployee manager = cmEmployeeRepository.findById(request.getManagerId())
                                .orElseThrow(() -> new EmployeeNotFoundException(request.getManagerId()));
                        newCm.setManager(manager);
                    }

                    return cmEmployeeRepository.save(newCm);
                });

        // 2. CmEmployeeEntityAssignment
        final boolean isFirstAssignment = !cmAssignmentRepository
                .existsByEmployeeIdAndLegalEntityId(cmEmployee.getId(), finalEntity.getId());

        CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(cmEmployee.getId(), finalEntity.getId())
                .orElseGet(() -> {
                    CmEmployeeEntityAssignment newAssignment = CmEmployeeEntityAssignment.builder()
                            .employee(cmEmployee)
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
        if (!payrollDetailsRepository.existsByEmployeeId(cmEmployee.getId())) {
            PayrollDetails payrollDetails = PayrollDetails.builder()
                    .employeeId(cmEmployee.getId())
                    .entityAssignmentId(assignment.getId())
                    .baseSalary(request.getBaseSalary())
                    .salaryCurrency(salaryCurrency)
                    .bankAccountNumber(request.getBankAccountNumber())
                    .bankName(request.getBankName())
                    .bankIfscCode(request.getBankIfscCode())
                    .build();
            payrollDetailsRepository.save(payrollDetails);
        }

        // Auto-create policy-based leave balances for all active policies in this entity
        leavePolicyService.generateBalanceSheetsForEmployee(cmEmployee.getId(), legalEntity.getId());

        return mapper.toDto(cmEmployee, assignment);
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
        CmEmployee cm = cmEmployeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        // Resolve primary entity assignment
        CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                .findByEmployeeIdAndPrimaryEntityTrue(cm.getId()).orElse(null);
        return mapper.toDto(cm, assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDto getEmployeeByAuthUserAndEntity(UUID authUserId, UUID legalEntityId) {
        UUID orgId = identitySecurityContext.getOrganizationId();
        CmEmployee cm = cmEmployeeRepository.findByAuthUserIdAndOrganizationId(authUserId, orgId)
                .orElseThrow(() -> new EmployeeNotFoundException(authUserId, legalEntityId));
        CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(cm.getId(), legalEntityId)
                .orElseThrow(() -> new EmployeeNotFoundException(authUserId, legalEntityId));
        return mapper.toDto(cm, assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDto getEmployeeByCode(String employeeCode, UUID legalEntityId) {
        CmEmployee cm = cmEmployeeRepository.findByEmployeeCodeAndLegalEntityId(employeeCode, legalEntityId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeCode, legalEntityId));
        CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(cm.getId(), legalEntityId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeCode, legalEntityId));
        return mapper.toDto(cm, assignment);
    }

    @Override
    public EmployeeDto updateEmployee(UUID employeeId, EmployeeDto request) {
        CmEmployee cm = cmEmployeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        // Update CmEmployee fields
        if (request.getEmployeeCode() != null) {
            cm.setEmployeeCode(request.getEmployeeCode());
        }
        if (request.getDisplayName() != null) {
            cm.setDisplayName(request.getDisplayName());
        }
        if (request.getEmail() != null) {
            cm.setEmail(request.getEmail());
        }
        if (request.getManagerId() != null) {
            CmEmployee manager = cmEmployeeRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.getManagerId()));
            cm.setManager(manager);
        }

        cm = cmEmployeeRepository.save(cm);

        // Update assignment if legal entity context provided
        CmEmployeeEntityAssignment assignment = null;
        if (request.getLegalEntityId() != null) {
            assignment = cmAssignmentRepository
                    .findByEmployeeIdAndLegalEntityId(cm.getId(), request.getLegalEntityId())
                    .orElse(null);
            if (assignment != null) {
                if (request.getDepartment() != null) assignment.setDepartment(request.getDepartment());
                if (request.getDesignation() != null) assignment.setDesignation(request.getDesignation());
                if (request.getHireDate() != null) assignment.setHireDate(request.getHireDate());
                if (request.getTerminationDate() != null) assignment.setTerminationDate(request.getTerminationDate());
                cmAssignmentRepository.save(assignment);
            }
        }

        return mapper.toDto(cm, assignment);
    }

    @Override
    public void terminateEmployee(UUID employeeId, LocalDate terminationDate) {
        CmEmployee cm = cmEmployeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        cm.setEmployeeStatus(EmployeeStatus.INACTIVE);
        cmEmployeeRepository.save(cm);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDto> listAllEmployees() {
        // List all CmEmployees and their primary assignments
        return cmEmployeeRepository.findAll().stream()
                .map(cm -> {
                    CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                            .findByEmployeeIdAndPrimaryEntityTrue(cm.getId()).orElse(null);
                    return mapper.toDto(cm, assignment);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDto> listEmployeesByEntity(UUID legalEntityId) {
        return cmEmployeeRepository.findAllByLegalEntityIdAndStatus(legalEntityId, EmployeeStatus.ACTIVE)
                .stream()
                .map(cm -> {
                    CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                            .findByEmployeeIdAndLegalEntityId(cm.getId(), legalEntityId).orElse(null);
                    return mapper.toDto(cm, assignment);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDto> listEmployeesByManager(UUID managerId) {
        return cmEmployeeRepository.findByManagerId(managerId).stream()
                .map(cm -> {
                    CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                            .findByEmployeeIdAndPrimaryEntityTrue(cm.getId()).orElse(null);
                    return mapper.toDto(cm, assignment);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDto getCurrentEmployee(UUID legalEntityId) {
        // Read the authUserId from the JWT via identitySecurityContext
        UUID authUserId = identitySecurityContext.getAuthUserId();
        UUID orgId = identitySecurityContext.getOrganizationId();

        // Look up CmEmployee by authUserId + orgId
        CmEmployee cm = cmEmployeeRepository.findByAuthUserIdAndOrganizationId(authUserId, orgId)
                .orElseThrow(() -> new EmployeeNotFoundException(authUserId));

        // Look up the entity assignment for the specified legal entity
        CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(cm.getId(), legalEntityId)
                .orElseThrow(() -> new EmployeeNotFoundException(
                        cm.getId(), legalEntityId));

        return mapper.toDto(cm, assignment);
    }
}
