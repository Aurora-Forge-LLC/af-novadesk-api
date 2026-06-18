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
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.entity.PayrollDetails;
import com.af.novadesk.api.payroll.mapper.EmployeeMapper;
import com.af.novadesk.api.payroll.outbox.EmployeeOutboxService;
import com.af.novadesk.api.payroll.repository.PayrollDetailsRepository;
import com.af.novadesk.api.payroll.service.EmployeeService;
import com.af.novadesk.api.payroll.service.LeavePolicyService;
import lombok.extern.slf4j.Slf4j;
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
    private final EmployeeOutboxService                 employeeOutboxService;
    private final LeavePolicyService                    leavePolicyService;

    public EmployeeServiceImpl(CmEmployeeRepository cmEmployeeRepository,
                               ShadowUserRepository shadowUserRepository,
                               LegalEntityRepository legalEntityRepository,
                               CmEmployeeEntityAssignmentRepository cmAssignmentRepository,
                               PayrollDetailsRepository payrollDetailsRepository,
                               EmployeeMapper mapper,
                               IdentitySecurityContext identitySecurityContext,
                               EmployeeOutboxService employeeOutboxService,
                               LeavePolicyService leavePolicyService) {
        this.cmEmployeeRepository       = cmEmployeeRepository;
        this.shadowUserRepository       = shadowUserRepository;
        this.legalEntityRepository      = legalEntityRepository;
        this.cmAssignmentRepository     = cmAssignmentRepository;
        this.payrollDetailsRepository   = payrollDetailsRepository;
        this.mapper                     = mapper;
        this.identitySecurityContext    = identitySecurityContext;
        this.employeeOutboxService      = employeeOutboxService;
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
            // NEW REVERSED FLOW: Pre-generate UUID, create ShadowUser,
            // publish outbox event (AuthHub will consume asynchronously).
            orgId = identitySecurityContext.getOrganizationId();
            final UUID preGeneratedUserId = UUID.randomUUID();

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

            // Pre-generate the AuthHub user ID; the actual user creation
            // happens asynchronously via the outbox → RabbitMQ → AuthHub.
            final UUID authUserId = preGeneratedUserId;

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

        // ── Outbox: publish employee onboarded event for AuthHub ──────────
        // The outbox event is written in the SAME transaction as the employee
        // save (above). The EmployeeOutboxPublisher picks it up, publishes
        // to RabbitMQ, and AuthHub creates the user + sends invite email.
        if (request.getShadowUserId() == null) {
            employeeOutboxService.publishEmployeeOnboarded(
                    cmEmployee,
                    cmEmployee.getAuthUserId(),
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    empOrgId,
                    identitySecurityContext.getAuthUserId()
            );
        }

        // 2. CmEmployeeEntityAssignment — reactivate or create
        CmEmployeeEntityAssignment assignment = cmAssignmentRepository
                .findByEmployeeIdAndLegalEntityId(cmEmployee.getId(), finalEntity.getId())
                .map(existing -> {
                    // Reactivate an existing assignment (re-onboarding)
                    if (existing.getAssignmentStatus() == EmployeeAssignmentStatus.TERMINATED
                            || existing.getAssignmentStatus() == EmployeeAssignmentStatus.INACTIVE) {
                        existing.setAssignmentStatus(EmployeeAssignmentStatus.ACTIVE);
                        existing.setDepartment(request.getDepartment());
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

        // Re-onboarding: generate a new authUserId and publish an outbox event.
        // AuthHub will consume this asynchronously to create/recreate the user
        // and send a fresh invitation email.
        UUID newAuthUserId = UUID.randomUUID();

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

        // ── Outbox: publish re-onboarded event for AuthHub ────────────────
        employeeOutboxService.publishEmployeeReonboarded(
                cmEmployee,
                newAuthUserId,
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                orgId,
                identitySecurityContext.getAuthUserId()
        );

        // Create fresh entity assignment
        LegalEntity legalEntity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.getLegalEntityId()));

        CmEmployeeEntityAssignment assignment = CmEmployeeEntityAssignment.builder()
                .employee(cmEmployee)
                .legalEntity(legalEntity)
                .organizationId(orgId)
                .department(request.getDepartment())
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

        log.info("Employee re-onboarded: employeeId={}, email={}, newAuthUserId={}",
                cmEmployee.getId(), request.getEmail(), newAuthUserId);

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

        UUID orgId = identitySecurityContext.getOrganizationId();

        // 1. Publish outbox event — AuthHub will consume asynchronously
        //    to offboard the user (revoke tokens, deactivate sessions,
        //    soft-delete OrgUser membership, deactivate account).
        employeeOutboxService.publishEmployeeOffboarded(
                cm, orgId, identitySecurityContext.getAuthUserId());

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
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDto> listAllEmployees() {
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
    @Transactional(readOnly = true)
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
}
