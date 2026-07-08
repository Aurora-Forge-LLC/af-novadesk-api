package com.af.novadesk.api.department.repository;

import com.af.novadesk.api.common.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Department}.
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    /** Org-level departments (legalEntityId IS NULL) for the given organization. */
    List<Department> findAllByOrganizationIdAndLegalEntityIdIsNullOrderByNameAsc(UUID organizationId);

    /** Entity-level departments scoped to a single legal entity. */
    List<Department> findAllByLegalEntityIdOrderByNameAsc(UUID legalEntityId);

    /** Finds a single org-level department by ID within an organization. */
    Optional<Department> findByIdAndOrganizationIdAndLegalEntityIdIsNull(UUID id, UUID organizationId);

    /** Finds a single entity-level department by ID, verifying it belongs to the given organization. */
    Optional<Department> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndLegalEntityIdIsNull(UUID organizationId);

    boolean existsByLegalEntityId(UUID legalEntityId);

    /** Check for duplicate name at org level. */
    boolean existsByOrganizationIdAndLegalEntityIdIsNullAndNameIgnoreCase(UUID organizationId, String name);

    /** Check for duplicate name at entity level. */
    boolean existsByLegalEntityIdAndNameIgnoreCase(UUID legalEntityId, String name);

    /** Check for duplicate name at entity level, excluding a specific department (for rename). */
    boolean existsByLegalEntityIdAndNameIgnoreCaseAndIdNot(UUID legalEntityId, String name, UUID excludeId);

    /** Check for duplicate name at org level, excluding a specific department (for rename). */
    boolean existsByOrganizationIdAndLegalEntityIdIsNullAndNameIgnoreCaseAndIdNot(
            UUID organizationId, String name, UUID excludeId);
}
