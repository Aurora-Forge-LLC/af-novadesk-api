package com.af.novadesk.api.common.repository;

import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmEmployeeEntityAssignmentRepository extends JpaRepository<CmEmployeeEntityAssignment, UUID> {

    List<CmEmployeeEntityAssignment> findAllByEmployeeId(UUID employeeId);

    Optional<CmEmployeeEntityAssignment> findByEmployeeIdAndLegalEntityId(
            UUID employeeId, UUID legalEntityId);

    /** Primary entity assignment for an employee (payroll entity). */
    Optional<CmEmployeeEntityAssignment> findByEmployeeIdAndPrimaryEntityTrue(UUID employeeId);

    boolean existsByEmployeeIdAndLegalEntityId(UUID employeeId, UUID legalEntityId);
}
