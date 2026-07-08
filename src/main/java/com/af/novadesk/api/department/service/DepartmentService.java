package com.af.novadesk.api.department.service;

import com.af.novadesk.api.department.dto.DepartmentCreateRequest;
import com.af.novadesk.api.department.dto.DepartmentDto;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for Department CRUD and seeding.
 *
 * <p>Departments are organisational units (IT, HR, Finance, etc.) that users
 * and employees are assigned to. They are distinct from entity roles — a
 * department is an organisational grouping, while a role determines permissions.</p>
 */
public interface DepartmentService {

    // =========================================================================
    // Read
    // =========================================================================

    /** Org-level departments for the caller's own organisation. */
    List<DepartmentDto> listOrgDepartments();

    /**
     * Entity-level departments for {@code legalEntityId}.
     * Asserts the caller may access that entity before returning results.
     */
    List<DepartmentDto> listEntityDepartments(UUID legalEntityId);

    /**
     * Returns a single department by ID, verifying the caller belongs to the
     * same organisation.
     */
    DepartmentDto getDepartment(UUID departmentId);

    // =========================================================================
    // Write
    // =========================================================================

    /**
     * Creates a new department. When {@code legalEntityId} is null, the
     * department is org-level; otherwise it's scoped to that legal entity.
     * Duplicate names within the same scope are rejected.
     */
    DepartmentDto createDepartment(DepartmentCreateRequest request);

    /**
     * Updates the name and/or scope of an existing department.
     */
    DepartmentDto updateDepartment(UUID departmentId, DepartmentCreateRequest request);

    /**
     * Deletes a department. Validates that no employee assignments or entity
     * user access grants still reference this department before deleting.
     *
     * @throws com.af.novadesk.api.department.exception.DepartmentInUseException
     *         if the department still has active references
     */
    void deleteDepartment(UUID departmentId);

    // =========================================================================
    // Seeding (existing)
    // =========================================================================

    /**
     * Seeds the default IT/HR/Finance departments for an organisation, if it
     * doesn't already have any org-level departments. Idempotent.
     */
    void seedDefaultsForOrganization(UUID organizationId);

    /**
     * Seeds the default IT/HR/Finance departments for a legal entity.
     * Idempotent.
     */
    void seedDefaultsForEntity(UUID organizationId, UUID legalEntityId);
}
