package com.af.novadesk.api.department.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.Department;
import com.af.novadesk.api.common.repository.CmEmployeeEntityAssignmentRepository;
import com.af.novadesk.api.department.dto.DepartmentCreateRequest;
import com.af.novadesk.api.department.dto.DepartmentDto;
import com.af.novadesk.api.department.exception.DepartmentDuplicateException;
import com.af.novadesk.api.department.exception.DepartmentInUseException;
import com.af.novadesk.api.department.exception.DepartmentNotFoundException;
import com.af.novadesk.api.department.repository.DepartmentRepository;
import com.af.novadesk.api.department.service.DepartmentService;
import com.af.novadesk.api.finance.exception.EntityAccessDeniedException;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link DepartmentService}.
 *
 * <p>Departments are organisational units (IT, HR, Finance, etc.) that users
 * and employees are assigned to. They are distinct from entity roles — a
 * department is an organisational grouping, while a role determines permissions.</p>
 *
 * <h3>Authorization</h3>
 * <ul>
 *   <li><b>Org-level</b> departments: ORG_ADMIN, ORG_HR, ORG_IT, ORG_FINANCE
 *       (plus SUPER_ADMIN, SYSTEM_ADMIN) can create/update/delete.</li>
 *   <li><b>Entity-level</b> departments: ENTITY_ADMIN, HR_MANAGER, IT_ADMIN
 *       on the target entity can create/update/delete.</li>
 *   <li><b>Read</b> (list/get): any authenticated user.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentServiceImpl implements DepartmentService {

    private static final List<String> DEFAULT_DEPARTMENTS = List.of("IT", "HR", "Finance");

    private static final List<String> ORG_TIER_DEPT_ROLES = List.of(
            "ORG_ADMIN", "ORG_HR", "SUPER_ADMIN", "SYSTEM_ADMIN");

    /** Entity-tier roles that may manage entity-scoped departments. */
    private static final List<String> ENTITY_TIER_DEPT_ROLES = List.of(
            "ENTITY_ADMIN", "HR_MANAGER");

    private final DepartmentRepository                departmentRepository;
    private final FinanceSecurityContext               securityContext;
    private final EntityAccessGuard                    entityAccessGuard;
    private final EntityUserAccessRepository           entityUserAccessRepository;
    private final CmEmployeeEntityAssignmentRepository employeeAssignmentRepository;

    // =========================================================================
    // Authorization
    // =========================================================================

    /**
     * Asserts the caller may manage (create/update/delete) a department at the
     * given scope.
     *
     * @param legalEntityId {@code null} = org-level department; set = entity-level
     * @throws EntityAccessDeniedException if the caller is not authorised
     */
    /** Sentinel UUID used when denying org-level department access (no entity involved). */
    private static final UUID ORG_LEVEL_SENTINEL = UUID.fromString(
            "00000000-0000-0000-0000-000000000000");

    private void assertCanManageDepartment(UUID legalEntityId) {
        List<String> callerRoles = securityContext.getRoles();

        // Platform-tier roles bypass all checks
        if (callerRoles.stream().anyMatch(r ->
                "SUPER_ADMIN".equalsIgnoreCase(r) || "SYSTEM_ADMIN".equalsIgnoreCase(r))) {
            return;
        }

        if (legalEntityId == null) {
            // ── Org-level department ────────────────────────────────────────────
            boolean isOrgTier = callerRoles.stream()
                    .anyMatch(r -> ORG_TIER_DEPT_ROLES.contains(r.toUpperCase()));
            if (!isOrgTier) {
                log.warn("Department management denied: caller roles {} are not authorised "
                        + "for org-level departments", callerRoles);
                throw new EntityAccessDeniedException(
                        securityContext.getAuthUserId(), ORG_LEVEL_SENTINEL);
            }
        } else {
            // ── Entity-level department ─────────────────────────────────────────
            // Check if caller holds one of the allowed entity-tier roles on this entity
            UUID callerId = securityContext.getAuthUserId();
            boolean hasEntityRole = ENTITY_TIER_DEPT_ROLES.stream().anyMatch(role ->
                    entityUserAccessRepository
                            .existsByStatusAndShadowUserAuthUserIdAndLegalEntityIdAndEntityRole(
                                    Status.ACTIVE, callerId, legalEntityId, role));

            // Org-tier callers (ORG_ADMIN, ORG_HR, ORG_IT, ORG_FINANCE) can also
            // manage entity-level departments
            boolean isOrgTier = callerRoles.stream()
                    .anyMatch(r -> ORG_TIER_DEPT_ROLES.contains(r.toUpperCase()));

            if (!hasEntityRole && !isOrgTier) {
                log.warn("Department management denied for entity {}: caller {} has no "
                        + "ENTITY_ADMIN/HR_MANAGER/IT_ADMIN grant and no org-tier role",
                        legalEntityId, callerId);
                throw new EntityAccessDeniedException(callerId, legalEntityId);
            }
        }
    }

    /**
     * Asserts the caller may manage the department identified by {@code departmentId}.
     * Loads the department first to determine its scope.
     */
    private void assertCanManageDepartmentById(UUID departmentId) {
        UUID orgId = securityContext.getOrganizationId();
        Department department = departmentRepository.findByIdAndOrganizationId(departmentId, orgId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
        assertCanManageDepartment(department.getLegalEntityId());
    }

    // =========================================================================
    // Read
    // =========================================================================

    @Override
    public List<DepartmentDto> listOrgDepartments() {
        UUID orgId = securityContext.getOrganizationId();
        return departmentRepository
                .findAllByOrganizationIdAndLegalEntityIdIsNullOrderByNameAsc(orgId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<DepartmentDto> listEntityDepartments(UUID legalEntityId) {
        entityAccessGuard.assertCanAccessEntity(legalEntityId);
        return departmentRepository
                .findAllByLegalEntityIdOrderByNameAsc(legalEntityId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public DepartmentDto getDepartment(UUID departmentId) {
        UUID orgId = securityContext.getOrganizationId();
        Department department = departmentRepository.findByIdAndOrganizationId(departmentId, orgId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
        return toDto(department);
    }

    // =========================================================================
    // Write
    // =========================================================================

    @Override
    @Transactional
    public DepartmentDto createDepartment(DepartmentCreateRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID legalEntityId = request.getLegalEntityId();

        // Authorization: who can create a department at this scope
        assertCanManageDepartment(legalEntityId);

        // Assert entity access if scoped to a specific entity
        if (legalEntityId != null) {
            entityAccessGuard.assertCanAccessEntity(legalEntityId);
        }

        // Duplicate name check
        if (legalEntityId != null) {
            if (departmentRepository.existsByLegalEntityIdAndNameIgnoreCase(legalEntityId, request.getName())) {
                throw new DepartmentDuplicateException(request.getName(), "legal entity");
            }
        } else {
            if (departmentRepository.existsByOrganizationIdAndLegalEntityIdIsNullAndNameIgnoreCase(orgId, request.getName())) {
                throw new DepartmentDuplicateException(request.getName(), "organisation");
            }
        }

        Department department = Department.builder()
                .organizationId(orgId)
                .legalEntityId(legalEntityId)
                .name(request.getName().trim())
                .build();

        Department saved = departmentRepository.save(department);
        log.info("Department created: id={}, name='{}', orgId={}, legalEntityId={}",
                saved.getId(), saved.getName(), orgId, legalEntityId);

        return toDto(saved);
    }

    @Override
    @Transactional
    public DepartmentDto updateDepartment(UUID departmentId, DepartmentCreateRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID newLegalEntityId = request.getLegalEntityId();

        Department department = departmentRepository.findByIdAndOrganizationId(departmentId, orgId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        // Authorization: who can update a department at this scope
        assertCanManageDepartment(newLegalEntityId);

        // Assert entity access if scoping to a specific entity
        if (newLegalEntityId != null) {
            entityAccessGuard.assertCanAccessEntity(newLegalEntityId);
        }

        // Duplicate name check (excluding current department)
        String newName = request.getName().trim();
        if (!department.getName().equalsIgnoreCase(newName)) {
            if (newLegalEntityId != null) {
                if (departmentRepository.existsByLegalEntityIdAndNameIgnoreCaseAndIdNot(
                        newLegalEntityId, newName, departmentId)) {
                    throw new DepartmentDuplicateException(newName, "legal entity");
                }
            } else {
                if (departmentRepository.existsByOrganizationIdAndLegalEntityIdIsNullAndNameIgnoreCaseAndIdNot(
                        orgId, newName, departmentId)) {
                    throw new DepartmentDuplicateException(newName, "organisation");
                }
            }
        }

        department.setName(newName);
        department.setLegalEntityId(newLegalEntityId);

        Department saved = departmentRepository.save(department);
        log.info("Department updated: id={}, name='{}', legalEntityId={}",
                saved.getId(), saved.getName(), saved.getLegalEntityId());

        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteDepartment(UUID departmentId) {
        UUID orgId = securityContext.getOrganizationId();

        Department department = departmentRepository.findByIdAndOrganizationId(departmentId, orgId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        // Authorization: who can delete a department at this scope
        assertCanManageDepartment(department.getLegalEntityId());

        // Check for active references before deleting
        long employeeAssignmentCount = employeeAssignmentRepository.countByDepartmentId(departmentId);
        long userAccessCount = entityUserAccessRepository.countByDepartmentId(departmentId);
        long totalReferences = employeeAssignmentCount + userAccessCount;

        if (totalReferences > 0) {
            throw new DepartmentInUseException(departmentId, department.getName(), totalReferences);
        }

        departmentRepository.delete(department);
        log.info("Department deleted: id={}, name='{}'", departmentId, department.getName());
    }

    // =========================================================================
    // Seeding
    // =========================================================================

    @Override
    @Transactional
    public void seedDefaultsForOrganization(UUID organizationId) {
        if (departmentRepository.existsByOrganizationIdAndLegalEntityIdIsNull(organizationId)) {
            return;
        }
        for (String name : DEFAULT_DEPARTMENTS) {
            departmentRepository.save(Department.builder()
                    .organizationId(organizationId)
                    .legalEntityId(null)
                    .name(name)
                    .build());
        }
        log.info("Seeded default org-level departments for organizationId={}", organizationId);
    }

    @Override
    @Transactional
    public void seedDefaultsForEntity(UUID organizationId, UUID legalEntityId) {
        if (departmentRepository.existsByLegalEntityId(legalEntityId)) {
            return;
        }
        for (String name : DEFAULT_DEPARTMENTS) {
            departmentRepository.save(Department.builder()
                    .organizationId(organizationId)
                    .legalEntityId(legalEntityId)
                    .name(name)
                    .build());
        }
        log.info("Seeded default departments for legalEntityId={}", legalEntityId);
    }

    // =========================================================================
    // Mapper
    // =========================================================================

    private DepartmentDto toDto(Department department) {
        return DepartmentDto.builder()
                .id(department.getId())
                .name(department.getName())
                .legalEntityId(department.getLegalEntityId())
                .build();
    }
}
