package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.EmployeeAssignmentStatus;
import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.exception.DuplicateEmployeeException;
import com.af.novadesk.api.common.exception.EmployeeNotFoundException;
import com.af.novadesk.api.common.repository.CmEmployeeEntityAssignmentRepository;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.common.service.AuthHubClientService;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import com.af.novadesk.api.payroll.entity.PayrollDetails;
import com.af.novadesk.api.payroll.exception.InvalidEmployeeStateException;
import com.af.novadesk.api.payroll.mapper.EmployeeMapper;
import com.af.novadesk.api.payroll.repository.LeaveRequestRepository;
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
    private final LeaveRequestRepository                leaveRequestRepository;

    public EmployeeServiceImpl(CmEmployeeRepository cmEmployeeRepository,
                               ShadowUserRepository shadowUserRepository,
                               LegalEntityRepository legalEntityRepository,
                               CmEmployeeEntityAssignmentRepository cmAssignmentRepository,
                               PayrollDetailsRepository payrollDetailsRepository,
                               EmployeeMapper mapper,
                               IdentitySecurityContext identitySecurityContext,
                               AuthHubClientService authHubClientService,
                               LeavePolicyService leavePolicyService,
                               LeaveRequestRepository leaveRequestRepository) {
        this.cmEmployeeRepository       = cmEmployeeRepository;
        this.shadowUserRepository       = shadowUserRepository;
        this.legalEntityRepository      = legalEntityRepository;
        this.cmAssignmentRepository     = cmAssignmentRepository;
        this.payrollDetailsRepository   = payrollDetailsRepository;
        this.mapper                     = mapper;
        this.identitySecurityContext    = identitySecurityContext;
        this.authHubClientService       = authHubClientService;
        this.leavePolicyService         = leavePolicyService;
        this.leaveRequestRepository     = leaveRequestRepository;
    }

    @Override
    public EmployeeDto onboardEmployee(EmployeeDto request) {
        LegalEntity legalEntity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.getLegalEntityId()));

        // ── Validate hire date against entity incorporation date ──────────
        validateHireDateNotBeforeIncorporation(request.getHireDate(), legalEntity);

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
                // ── Validate hire date against entity incorporation date ──
                if (request.getHireDate() != null) {
                    LegalEntity entity = legalEntityRepository.findById(request.getLegalEntityId())
                            .orElse(null);
                    if (entity != null) {
                        validateHireDateNotBeforeIncorporation(request.getHireDate(), entity);
                    }
                    assignment.setHireDate(request.getHireDate());
                }
                if (request.getDepartment() != null) assignment.setDepartment(request.getDepartment());
                if (request.getDesignation() != null) assignment.setDesignation(request.getDesignation());
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

        // Mark all active entity assignments with the termination date
        // so payroll can prorate salary for days worked in the final period.
        List<CmEmployeeEntityAssignment> activeAssignments = cmAssignmentRepository
                .findAllByEmployeeId(cm.getId());
        for (CmEmployeeEntityAssignment assignment : activeAssignments) {
            if (assignment.getAssignmentStatus() == EmployeeAssignmentStatus.ACTIVE) {
                assignment.setAssignmentStatus(EmployeeAssignmentStatus.INACTIVE);
                assignment.setTerminationDate(terminationDate);
                cmAssignmentRepository.save(assignment);
            }
        }
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

    @Override
    public EmployeeDto moveEmployee(UUID employeeId, UUID fromEntityId, UUID toEntityId, boolean makePrimary) {
        // 1. Validate employee exists and is ACTIVE
        CmEmployee cm = cmEmployeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        if (cm.getEmployeeStatus() != EmployeeStatus.ACTIVE) {
            throw new InvalidEmployeeStateException(employeeId,
                    "Employee is " + cm.getEmployeeStatus() + ". Only ACTIVE employees can be moved.");
        }

        // 2. Validate source and target are different
        if (fromEntityId.equals(toEntityId)) {
            throw new IllegalArgumentException("Source and target entities must be different");
        }

        // 3. Validate source entity assignment exists and is ACTIVE
        CmEmployeeEntityAssignment sourceAssignment = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(employeeId, fromEntityId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId, fromEntityId));

        if (sourceAssignment.getAssignmentStatus() != EmployeeAssignmentStatus.ACTIVE) {
            throw new InvalidEmployeeStateException(employeeId,
                    "Source entity assignment is " + sourceAssignment.getAssignmentStatus()
                            + ". Expected ACTIVE.");
        }

        // 4. Validate target legal entity exists
        legalEntityRepository.findById(toEntityId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId, toEntityId));

        // 5. Check target assignment not already ACTIVE
        cmAssignmentRepository.findByEmployeeIdAndLegalEntityId(employeeId, toEntityId)
                .ifPresent(assignment -> {
                    if (assignment.getAssignmentStatus() == EmployeeAssignmentStatus.ACTIVE) {
                        throw new DuplicateEmployeeException(
                                "Employee already has an ACTIVE assignment to the target entity " + toEntityId);
                    }
                });

        // 6. Block move if employee has pending leave requests in source entity
        List<LeaveRequestStatus> pendingStatuses = List.of(
                LeaveRequestStatus.PENDING,
                LeaveRequestStatus.MODIFICATION_REQUESTED);
        List<LeaveRequest> unresolvedLeaves = leaveRequestRepository
                .findByEmployeeIdAndLegalEntityIdAndLeaveRequestStatusIn(
                        employeeId, fromEntityId, pendingStatuses);

        if (!unresolvedLeaves.isEmpty()) {
            throw new InvalidEmployeeStateException(employeeId,
                    "Employee has " + unresolvedLeaves.size()
                            + " pending leave request(s) in the source entity. "
                            + "Resolve all pending/modification-requested leaves before moving.");
        }

        // 7. Deactivate source entity assignment
        sourceAssignment.setAssignmentStatus(EmployeeAssignmentStatus.INACTIVE);
        sourceAssignment.setTerminationDate(LocalDate.now());
        cmAssignmentRepository.save(sourceAssignment);

        // 8. Create or reactivate target entity assignment
        CmEmployeeEntityAssignment targetAssignment = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(employeeId, toEntityId)
                .orElseGet(() -> {
                    boolean hasNoPrimary = cmAssignmentRepository
                            .findByEmployeeIdAndPrimaryEntityTrue(employeeId).isEmpty();
                    boolean isPrimaryAssignment = makePrimary || hasNoPrimary;
                    return CmEmployeeEntityAssignment.builder()
                            .employee(cm)
                            .legalEntity(legalEntityRepository.getReferenceById(toEntityId))
                            .organizationId(cm.getOrganizationId())
                            .department(sourceAssignment.getDepartment())
                            .designation(sourceAssignment.getDesignation())
                            .hireDate(LocalDate.now())
                            .primaryEntity(isPrimaryAssignment)
                            .assignmentStatus(EmployeeAssignmentStatus.ACTIVE)
                            .build();
                });

        // If it already existed (INACTIVE/TERMINATED), reactivate it
        if (targetAssignment.getAssignmentStatus() != EmployeeAssignmentStatus.ACTIVE) {
            targetAssignment.setAssignmentStatus(EmployeeAssignmentStatus.ACTIVE);
            targetAssignment.setTerminationDate(null);
            targetAssignment.setHireDate(LocalDate.now());
        }

        // Handle primary entity flag
        if (makePrimary) {
            // Find current primary and unset it
            cmAssignmentRepository.findByEmployeeIdAndPrimaryEntityTrue(employeeId)
                    .ifPresent(currentPrimary -> {
                        currentPrimary.setPrimaryEntity(false);
                        cmAssignmentRepository.save(currentPrimary);
                    });
            targetAssignment.setPrimaryEntity(true);
        }

        cmAssignmentRepository.save(targetAssignment);

        // 9. Generate leave balances for the target entity
        leavePolicyService.generateBalanceSheetsForEmployee(employeeId, toEntityId);

        // 10. Return result
        return mapper.toDto(cm, targetAssignment);
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Validates that an employee's hire date is not before the entity's
     * incorporation date. Throws InvalidEmployeeStateException if violated.
     */
    private void validateHireDateNotBeforeIncorporation(LocalDate hireDate, LegalEntity legalEntity) {
        if (hireDate.isBefore(legalEntity.getIncorporationDate())) {
            throw new InvalidEmployeeStateException(
                    "Hire date " + hireDate
                    + " cannot be before entity incorporation date "
                    + legalEntity.getIncorporationDate()
                    + " for entity '" + legalEntity.getEntityName() + "'");
        }
    }
}
