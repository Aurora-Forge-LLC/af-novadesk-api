package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.payroll.entity.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {

    List<LeavePolicy> findByLegalEntityId(UUID legalEntityId);

    List<LeavePolicy> findByLegalEntityIdAndStatus(UUID legalEntityId, Status status);

    Optional<LeavePolicy> findByLegalEntityIdAndName(UUID legalEntityId, String name);

    Optional<LeavePolicy> findByLegalEntityIdAndNameAndStatus(UUID legalEntityId, String name, Status status);

    List<LeavePolicy> findByLegalEntityOrganizationId(UUID organizationId);

    /** Find all active earned-leave policies for the monthly accrual scheduler. */
    List<LeavePolicy> findByIsEarnedTrueAndStatus(Status status);
}
