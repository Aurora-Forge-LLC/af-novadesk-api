package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    /**
     * Finds pending/modification-requested leave requests whose start date has passed.
     * Used by the auto-expiry scheduled job to expire stale requests.
     */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.leaveRequestStatus IN :statuses AND lr.startDate < :cutoffDate")
    List<LeaveRequest> findByStatusesAndStartDateBefore(
            @Param("statuses") List<LeaveRequestStatus> statuses,
            @Param("cutoffDate") LocalDate cutoffDate);
}
