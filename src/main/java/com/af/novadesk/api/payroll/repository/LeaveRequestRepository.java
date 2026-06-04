package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    List<LeaveRequest> findByLegalEntityOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    List<LeaveRequest> findByApproverIdAndLeaveRequestStatus(
            UUID approverId, LeaveRequestStatus status);
    List<LeaveRequest> findByLegalEntityIdAndLeaveRequestStatus(
            UUID legalEntityId, LeaveRequestStatus status);
}
