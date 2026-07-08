package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.EmployeeAssignmentStatus;
import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.event.EmployeeOnboardedEvent;
import com.af.novadesk.api.common.exception.AuthHubIntegrationException;
import com.af.novadesk.api.common.exception.DuplicateEmployeeException;
import com.af.novadesk.api.common.exception.EmployeeNotFoundException;
import com.af.novadesk.api.common.entity.Department;
import com.af.novadesk.api.common.repository.CmEmployeeEntityAssignmentRepository;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.department.repository.DepartmentRepository;
import com.af.novadesk.api.asset.dto.OffboardingAssetCheckDto;
import com.af.novadesk.api.asset.service.AssetAssignmentService;
import com.af.novadesk.api.common.service.AuthHubClientService;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import com.af.novadesk.api.payroll.entity.PayrollDetails;
import com.af.novadesk.api.payroll.exception.AssetOffboardingNotClearException;
import com.af.novadesk.api.payroll.exception.InvalidEmployeeStateException;
import com.af.novadesk.api.payroll.mapper.EmployeeMapper;
import com.af.novadesk.api.payroll.repository.LeaveBalanceRepository;
import com.af.novadesk.api.payroll.repository.LeaveRequestRepository;
import com.af.novadesk.api.payroll.repository.PayrollDetailsRepository;
import com.af.novadesk.api.payroll.service.EmployeeService;
import com.af.novadesk.api.payroll.service.LeavePolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
    private final LeaveBalanceRepository                leaveBalanceRepository;
    private final AssetAssignmentService                assetAssignmentService;
    private final ApplicationEventPublisher             eventPublisher;
    private final EntityAccessGuard                     entityAccessGuard;
    private final DepartmentRepository                  departmentRepository;

    public EmployeeServiceImpl(CmEmployeeRepository cmEmployeeRepository,
                               ShadowUserRepository shadowUserRepository,
                               LegalEntityRepository legalEntityRepository,
                               CmEmployeeEntityAssignmentRepository cmAssignmentRepository,
                               PayrollDetailsRepository payrollDetailsRepository,
                               EmployeeMapper mapper,
                               IdentitySecurityContext identitySecurityContext,
                               AuthHubClientService authHubClientService,
                               LeavePolicyService leavePolicyService,
                               LeaveRequestRepository leaveRequestRepository,
                               LeaveBalanceRepository leaveBalanceRepository,
                               AssetAssignmentService assetAssignmentService,
                               ApplicationEventPublisher eventPublisher,
                               EntityAccessGuard entityAccessGuard,
                               DepartmentRepository departmentRepository) {
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
        this.leaveBalanceRepository     = leaveBalanceRepository;
        this.assetAssignmentService     = assetAssignmentService;
        this.eventPublisher             = eventPublisher;
        this.entityAccessGuard          = entityAccessGuard;
        this.departmentRepository       = departmentRepository;
    }

    /**
     * Validates that {@code departmentId}, when provided, refers to a
     * department actually scoped to {@code legalEntityId} — prevents a caller
     * from passing a department UUID that belongs to a different entity or org.
     * Null is allowed (e.g. for initial admin setup).
     */
    private void validateDepartmentForEntity(UUID departmentId, UUID legalEntityId) {
        if (departmentId == null) {
            return;
        }
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));
        if (!legalEntityId.equals(department.getLegalEntityId())) {
            throw new IllegalArgumentException("Department does not belong to the target legal entity");
        }
    }

    /**
     * When moving an employee to a different entity, the source assignment's
     * departmentId belongs to the source entity and cannot be reused as-is
     * (departments are entity-scoped). Resolve the department with the same
     * name in the target entity instead — departments are seeded identically
     * (IT/HR/Finance) per entity, so this recovers the equivalent department.
     * Returns null if no matching name is found (e.g. source had none).
     */
    private UUID resolveEquivalentDepartmentId(UUID sourceDepartmentId, UUID targetLegalEntityId) {
        if (sourceDepartmentId == null) {
            return null;
        }
        return departmentRepository.findById(sourceDepartmentId)
                .map(Department::getName)
                .flatMap(name -> departmentRepository
                        .findAllByLegalEntityIdOrderByNameAsc(targetLegalEntityId).stream()
                        .filter(d -> d.getName().equals(name))
                        .findFirst())
                .map(Department::getId)
                .orElse(null);
    }

    /**
     * Resolves every legal entity an employee is assigned to and asserts the
     * caller may act on at least one of them (org-wide roles bypass). Used to
     * scope operations keyed only by employeeId.
     */
    private void assertCanAccessEmployee(UUID employeeId) {
        java.util.List<UUID> entityIds = cmAssignmentRepository.findAllByEmployeeId(employeeId).stream()
                .map(a -> a.getLegalEntity().getId())
                .collect(java.util.stream.Collectors.toList());
        if (entityIds.isEmpty()) {
            // No assignment yet — fall back to org-wide-only access (entity-tier
            // callers cannot act on an unassigned employee).
            if (!entityAccessGuard.hasOrgWideVisibility()) {
                throw new com.af.novadesk.api.finance.exception.EntityAccessDeniedException(
                        identitySecurityContext.getAuthUserId(), null);
            }
            return;
        }
        entityAccessGuard.assertCanAccessAnyOf(entityIds);
    }

    @Override
    public EmployeeDto onboardEmployee(EmployeeDto request) {
        LegalEntity legalEntity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.getLegalEntityId()));

        // Entity-scope guard: caller must be able to onboard into this entity.
        entityAccessGuard.assertCanAccessEntity(legalEntity.getId());

        // ── Validate hire date against entity incorporation date ──────────
        validateHireDateNotBeforeIncorporation(request.getHireDate(), legalEntity);

        // ── Department is required and must belong to this entity ─────────
        validateDepartmentForEntity(request.getDepartmentId(), legalEntity.getId());

        final ShadowUser shadowUser;
        final UUID orgId;

        if (request.getShadowUserId() != null) {
            // OLD FLOW: ShadowUser already exists
            shadowUser = shadowUserRepository.findById(request.getShadowUserId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.getShadowUserId()));
            checkDuplicateEmployee(shadowUser.getAuthUserId(), legalEntity.getId());
            orgId = shadowUser.getOrganizationId();
        } else {
            // NEW REVERSED FLOW: Pre-generate UUID, register in AuthHub, then create ShadowUser
            orgId = identitySecurityContext.getOrganizationId();
            UUID preGeneratedUserId = UUID.randomUUID();

            if (request.getEmail() != null) {
                // Check if employee was previously offboarded — handle re-onboarding
                shadowUserRepository.findByEmail(request.getEmail())
                        .ifPresent(existingShadow -> {
                            CmEmployee existingCm = cmEmployeeRepository
                                    .findByAuthUserIdAndOrganizationId(
                                            existingShadow.getAuthUserId(), orgId)
                                    .orElse(null);
                            if (existingCm != null
                                    && existingCm.getEmployeeStatus() == EmployeeStatus.OFFBOARDED) {
                                // Re-onboarding: throw a specific exception so the
                                // controller or caller can handle the reactivation flow.
                                throw new DuplicateEmployeeException(
                                        "Employee was previously offboarded. "
                                        + "Use re-onboard flow for email: " + request.getEmail());
                            }
                            throw new DuplicateEmployeeException(
                                    "Email already exists: " + request.getEmail());
                        });
            }

            // Call AuthHub's admin endpoint (POST /api/v1/admin/users) —
            // creates User with PENDING_SETUP, Profile, OrgUser(EMPLOYEE),
            // and publishes invite email event via RabbitMQ.
            UUID authUserId = authHubClientService.createUser(
                    preGeneratedUserId,
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
                            .employeeStatus(EmployeeStatus.PENDING_SETUP) // password not yet set
                            .build();

                    // Set manager if provided
                    if (request.getManagerId() != null) {
                        CmEmployee manager = cmEmployeeRepository.findById(request.getManagerId())
                                .orElseThrow(() -> new EmployeeNotFoundException(request.getManagerId()));
                        newCm.setManager(manager);
                    }

                    return cmEmployeeRepository.save(newCm);
                });

        // If CmEmployee already existed (e.g. from old flow), ensure status is at least PENDING_SETUP
        if (cmEmployee.getEmployeeStatus() == EmployeeStatus.OFFBOARDED) {
            cmEmployee.setEmployeeStatus(EmployeeStatus.PENDING_SETUP);
            cmEmployee.setEmail(request.getEmail());
            cmEmployee.setDisplayName(request.getFirstName() + " " + request.getLastName());
            cmEmployee.setEmployeeCode(request.getEmployeeCode());
            cmEmployee = cmEmployeeRepository.save(cmEmployee);
        }

        final CmEmployee finalCmEmployee = cmEmployee;

        // 2. CmEmployeeEntityAssignment — reactivate or create
        CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(cmEmployee.getId(), finalEntity.getId())
                .map(existing -> {
                    // Reactivate an existing assignment (re-onboarding)
                    if (existing.getAssignmentStatus() == EmployeeAssignmentStatus.TERMINATED
                            || existing.getAssignmentStatus() == EmployeeAssignmentStatus.INACTIVE) {
                        existing.setAssignmentStatus(EmployeeAssignmentStatus.ACTIVE);
                        existing.setDepartment(request.getDepartment());
                        existing.setDepartmentId(request.getDepartmentId());
                        existing.setDesignation(request.getDesignation());
                        existing.setHireDate(request.getHireDate());
                        existing.setTerminationDate(null);
                        cmAssignmentRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    final boolean isFirstAssignment = !cmAssignmentRepository
                            .existsByEmployeeIdAndLegalEntityId(finalCmEmployee.getId(), finalEntity.getId());
                    CmEmployeeEntityAssignment newAssignment = CmEmployeeEntityAssignment.builder()
                            .employee(finalCmEmployee)
                            .legalEntity(finalEntity)
                            .organizationId(empOrgId)
                            .department(request.getDepartment())
                            .departmentId(request.getDepartmentId())
                            .designation(request.getDesignation())
                            .primaryEntity(isFirstAssignment)
                            .hireDate(request.getHireDate())
                            .terminationDate(request.getTerminationDate())
                            .assignmentStatus(EmployeeAssignmentStatus.ACTIVE)
                            .build();
                    return cmAssignmentRepository.save(newAssignment);
                });

        // 3. PayrollDetails — upsert (re-onboarding may have existing record)
        PayrollDetails payrollDetails = payrollDetailsRepository.findByEmployeeId(cmEmployee.getId())
                .orElse(null);
        if (payrollDetails == null) {
            payrollDetails = PayrollDetails.builder()
                    .employeeId(cmEmployee.getId())
                    .entityAssignmentId(assignment.getId())
                    .baseSalary(request.getBaseSalary())
                    .salaryCurrency(salaryCurrency)
                    .bankAccountNumber(request.getBankAccountNumber())
                    .bankName(request.getBankName())
                    .bankIfscCode(request.getBankIfscCode())
                    .build();
            payrollDetailsRepository.save(payrollDetails);
        } else {
            payrollDetails.setEntityAssignmentId(assignment.getId());
            payrollDetails.setBaseSalary(request.getBaseSalary());
            payrollDetails.setSalaryCurrency(salaryCurrency);
            payrollDetails.setBankAccountNumber(request.getBankAccountNumber());
            payrollDetails.setBankName(request.getBankName());
            payrollDetails.setBankIfscCode(request.getBankIfscCode());
            payrollDetailsRepository.save(payrollDetails);
        }

        // Auto-create policy-based leave balances for all active policies in this entity
        leavePolicyService.generateBalanceSheetsForEmployee(cmEmployee.getId(), legalEntity.getId());

        // Publish event so EntityAccessSyncService creates EntityUserAccess for this employee
        String entityRole = request.getIsManager() != null && request.getIsManager()
                ? "MANAGER" : "VIEWER";
        eventPublisher.publishEvent(new EmployeeOnboardedEvent(
                cmEmployee.getId(),
                shadowUser.getAuthUserId(),
                orgId,
                legalEntity.getId(),
                request.getEmployeeCode(),
                request.getFirstName() + " " + request.getLastName(),
                request.getEmail(),
                request.getIsManager() != null && request.getIsManager(),
                entityRole
        ));
        log.info("EmployeeOnboardedEvent published: employeeId={}, authUserId={}, entityId={}, role={}",
                cmEmployee.getId(), shadowUser.getAuthUserId(), legalEntity.getId(), entityRole);

        return mapper.toDto(cmEmployee, assignment);
    }

    /**
     * Re-onboards a previously offboarded employee — reactivates the existing
     * CmEmployee, creates a new AuthHub user with PENDING_SETUP, and sends a
     * fresh password-setup invitation email.
     *
     * @param request the re-onboard request (existing email required)
     * @return the reactivated EmployeeDto
     */
    @Override
    public EmployeeDto reonboardEmployee(EmployeeDto request) {
        UUID orgId = identitySecurityContext.getOrganizationId();

        // Find existing ShadowUser by email
        ShadowUser shadowUser = shadowUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ShadowUser found for email: " + request.getEmail()));

        // Find existing CmEmployee in OFFBOARDED status
        CmEmployee cmEmployee = cmEmployeeRepository
                .findByAuthUserIdAndOrganizationId(shadowUser.getAuthUserId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No offboarded employee found for email: " + request.getEmail()));

        if (cmEmployee.getEmployeeStatus() != EmployeeStatus.OFFBOARDED) {
            throw new DuplicateEmployeeException(
                    "Employee is not offboarded: " + request.getEmail());
        }

        // Create a fresh AuthHub user — AuthHub will handle reactivation internally
        UUID newAuthUserId = authHubClientService.createUser(
                UUID.randomUUID(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                orgId
        );

        // Update ShadowUser with new authUserId
        shadowUser.setAuthUserId(newAuthUserId);
        shadowUser.setDisplayName(request.getFirstName() + " " + request.getLastName());
        shadowUser.setLastSyncedAt(LocalDateTime.now());
        shadowUserRepository.save(shadowUser);

        // Reactivate CmEmployee
        cmEmployee.setAuthUserId(newAuthUserId);
        cmEmployee.setEmployeeStatus(EmployeeStatus.PENDING_SETUP);
        cmEmployee.setDisplayName(request.getFirstName() + " " + request.getLastName());
        cmEmployee.setEmail(request.getEmail());
        cmEmployee.setEmployeeCode(request.getEmployeeCode());
        cmEmployee = cmEmployeeRepository.save(cmEmployee);

        // Create fresh entity assignment
        LegalEntity legalEntity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.getLegalEntityId()));

        // Entity-scope guard: caller must be able to re-onboard into this entity.
        entityAccessGuard.assertCanAccessEntity(legalEntity.getId());

        // ── Department is required and must belong to this entity ─────────
        validateDepartmentForEntity(request.getDepartmentId(), legalEntity.getId());

        CmEmployeeEntityAssignment assignment = CmEmployeeEntityAssignment.builder()
                .employee(cmEmployee)
                .legalEntity(legalEntity)
                .organizationId(orgId)
                .department(request.getDepartment())
                .departmentId(request.getDepartmentId())
                .designation(request.getDesignation())
                .primaryEntity(true)
                .hireDate(request.getHireDate())
                .assignmentStatus(EmployeeAssignmentStatus.ACTIVE)
                .build();
        assignment = cmAssignmentRepository.save(assignment);

        // Update PayrollDetails
        PayrollDetails payrollDetails = payrollDetailsRepository.findByEmployeeId(cmEmployee.getId())
                .orElse(null);
        if (payrollDetails != null) {
            payrollDetails.setEntityAssignmentId(assignment.getId());
            payrollDetails.setBaseSalary(request.getBaseSalary());
            payrollDetails.setSalaryCurrency(
                    request.getSalaryCurrency() != null ? request.getSalaryCurrency()
                            : legalEntity.getBaseCurrency());
            payrollDetailsRepository.save(payrollDetails);
        }

        // Auto-create leave balances
        leavePolicyService.generateBalanceSheetsForEmployee(cmEmployee.getId(), legalEntity.getId());

        // Publish event so EntityAccessSyncService creates EntityUserAccess for this employee
        String entityRole = request.getIsManager() != null && request.getIsManager()
                ? "MANAGER" : "VIEWER";
        eventPublisher.publishEvent(new EmployeeOnboardedEvent(
                cmEmployee.getId(),
                newAuthUserId,
                orgId,
                legalEntity.getId(),
                request.getEmployeeCode(),
                request.getFirstName() + " " + request.getLastName(),
                request.getEmail(),
                request.getIsManager() != null && request.getIsManager(),
                entityRole
        ));
        log.info("EmployeeOnboardedEvent published (re-onboard): employeeId={}, authUserId={}, entityId={}, role={}",
                cmEmployee.getId(), newAuthUserId, legalEntity.getId(), entityRole);

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
        assertCanAccessEmployee(employeeId);
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
        assertCanAccessEmployee(employeeId);

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
                if (request.getDepartmentId() != null) {
                    validateDepartmentForEntity(request.getDepartmentId(), request.getLegalEntityId());
                    assignment.setDepartmentId(request.getDepartmentId());
                }
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
        assertCanAccessEmployee(employeeId);

        UUID orgId = identitySecurityContext.getOrganizationId();

        // 1. Call AuthHub to offboard — revokes tokens, deactivates sessions,
        //    soft-deletes OrgUser membership, deactivates user account.
        try {
            authHubClientService.offboardUser(cm.getAuthUserId(), orgId);
        } catch (AuthHubIntegrationException e) {
            log.error("AuthHub offboard failed for employeeId={}, authUserId={}: {}",
                    employeeId, cm.getAuthUserId(), e.getMessage());
        }

        // 2. Set employee status to OFFBOARDED (soft-delete — data preserved)
        cm.setEmployeeStatus(EmployeeStatus.OFFBOARDED);
        cmEmployeeRepository.save(cm);

        // 3. Terminate all active entity assignments
        List<CmEmployeeEntityAssignment> assignments =
                cmAssignmentRepository.findAllByEmployeeId(employeeId);
        for (CmEmployeeEntityAssignment a : assignments) {
            if (a.getAssignmentStatus() != EmployeeAssignmentStatus.TERMINATED) {
                a.setAssignmentStatus(EmployeeAssignmentStatus.TERMINATED);
                a.setTerminationDate(terminationDate);
                cmAssignmentRepository.save(a);
            }
        }

        // 4. Deactivate PayrollDetails
        payrollDetailsRepository.findByEmployeeId(employeeId).ifPresent(pd -> {
            pd.setRecordStatus("INACTIVE");
            payrollDetailsRepository.save(pd);
        });

        log.info("Employee offboarded: employeeId={}, authUserId={}, terminationDate={}",
                employeeId, cm.getAuthUserId(), terminationDate);

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
    @Transactional
    public void hardDeleteEmployee(UUID employeeId) {
        CmEmployee cm = cmEmployeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        assertCanAccessEmployee(employeeId);

        UUID orgId = identitySecurityContext.getOrganizationId();
        log.info("Hard-deleting employee: employeeId={}, authUserId={}", employeeId, cm.getAuthUserId());

        // 1. Asset offboarding gate check
        OffboardingAssetCheckDto assetCheck = assetAssignmentService.checkOffboarding(employeeId);
        if (!assetCheck.isCleared()) {
            log.warn("Hard-delete blocked for employee {}: {} unreturned asset(s)",
                    employeeId, assetCheck.getUnreturnedCount());
            throw new AssetOffboardingNotClearException(assetCheck);
        }

        // 2. Hard-delete user in AuthHub (User + Profile + all associated auth data)
        try {
            authHubClientService.deleteUser(cm.getAuthUserId());
        } catch (AuthHubIntegrationException e) {
            log.error("AuthHub hard-delete failed for authUserId={}: {}", cm.getAuthUserId(), e.getMessage());
            throw e;
        }

        // 3. Delete ShadowUser
        shadowUserRepository.deleteByAuthUserId(cm.getAuthUserId());

        // 4. Delete entity assignments
        cmAssignmentRepository.findAllByEmployeeId(employeeId)
                .forEach(cmAssignmentRepository::delete);

        // 5. Delete payroll details
        payrollDetailsRepository.findByEmployeeId(employeeId)
                .ifPresent(payrollDetailsRepository::delete);

        // 6. Delete leave balances
        leaveBalanceRepository.findByEmployeeId(employeeId)
                .forEach(leaveBalanceRepository::delete);

        // 7. Delete leave requests (and any cascade-linked transactions)
        leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .forEach(leaveRequestRepository::delete);

        // 8. Delete the employee record itself
        cmEmployeeRepository.deleteById(employeeId);

        log.info("Employee hard-deleted successfully: employeeId={}, authUserId={}",
                employeeId, cm.getAuthUserId());
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
        return cmEmployeeRepository.findAllByLegalEntityId(legalEntityId)
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
    public List<EmployeeDto> listEmployeesByStatus(EmployeeStatus status, UUID legalEntityId) {
        if (legalEntityId != null) {
            return cmEmployeeRepository
                    .findAllByLegalEntityIdAndStatus(legalEntityId, status)
                    .stream()
                    .map(cm -> {
                        CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                                .findByEmployeeIdAndLegalEntityId(cm.getId(), legalEntityId)
                                .orElse(null);
                        return mapper.toDto(cm, assignment);
                    })
                    .collect(Collectors.toList());
        }
        return cmEmployeeRepository
                .findAllByOrganizationIdAndEmployeeStatus(
                        identitySecurityContext.getOrganizationId(), status)
                .stream()
                .map(cm -> {
                    CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                            .findByEmployeeIdAndPrimaryEntityTrue(cm.getId()).orElse(null);
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
    @Transactional
    public EmployeeDto getCurrentEmployee(UUID legalEntityId) {
        UUID authUserId = identitySecurityContext.getAuthUserId();
        UUID orgId = identitySecurityContext.getOrganizationId();

        CmEmployee cm = cmEmployeeRepository.findByAuthUserIdAndOrganizationId(authUserId, orgId)
                .orElseThrow(() -> new EmployeeNotFoundException(authUserId));

        // Auto-transition: if employee was PENDING_SETUP and is now calling this
        // endpoint (meaning they logged in = password was set), mark them ACTIVE.
        if (cm.getEmployeeStatus() == EmployeeStatus.PENDING_SETUP) {
            cm.setEmployeeStatus(EmployeeStatus.ACTIVE);
            cmEmployeeRepository.save(cm);
            log.info("Employee auto-activated on first login: employeeId={}, authUserId={}",
                    cm.getId(), authUserId);
        }

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

        // 2a. Entity-scope guard: a move touches both entities, so the caller must
        // be able to act on both the source and the target (org-wide roles bypass).
        entityAccessGuard.assertCanAccessEntity(fromEntityId);
        entityAccessGuard.assertCanAccessEntity(toEntityId);

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
                            // sourceAssignment's departmentId belongs to the OLD entity —
                            // resolve the equivalent department by name in the new entity
                            // instead of copying a foreign-entity FK across.
                            .departmentId(resolveEquivalentDepartmentId(sourceAssignment.getDepartmentId(), toEntityId))
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

    @Override
    @Transactional
    public void activateEmployee(UUID authUserId, UUID orgId) {
        CmEmployee cm = cmEmployeeRepository.findByAuthUserIdAndOrganizationId(authUserId, orgId)
                .orElseThrow(() -> new EmployeeNotFoundException(authUserId, orgId));

        if (cm.getEmployeeStatus() == EmployeeStatus.PENDING_SETUP) {
            cm.setEmployeeStatus(EmployeeStatus.ACTIVE);
            cmEmployeeRepository.save(cm);
            log.info("Employee activated via RabbitMQ event: employeeId={}, authUserId={}",
                    cm.getId(), authUserId);
        } else {
            log.info("Employee already active or not in PENDING_SETUP: employeeId={}, status={}",
                    cm.getId(), cm.getEmployeeStatus());
        }
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
