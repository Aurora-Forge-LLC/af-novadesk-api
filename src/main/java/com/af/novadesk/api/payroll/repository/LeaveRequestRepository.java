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

    /** Returns all leave requests for a legal entity, newest first. */
    List<LeaveRequest> findByLegalEntityIdOrderByCreatedAtDesc(UUID legalEntityId);

    /** Finds pending/modification-requested leaves for a specific employee in an entity. */
    List<LeaveRequest> findByEmployeeIdAndLegalEntityIdAndLeaveRequestStatusIn(
            UUID employeeId, UUID legalEntityId, List<LeaveRequestStatus> statuses);

    /**
     * Finds pending/modification-requested leave requests whose start date has passed.
     * Used by the auto-expiry scheduled job to expire stale requests.
     */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.leaveRequestStatus IN :statuses AND lr.startDate < :cutoffDate")
    List<LeaveRequest> findByStatusesAndStartDateBefore(
            @Param("statuses") List<LeaveRequestStatus> statuses,
            @Param("cutoffDate") LocalDate cutoffDate);

    /** Returns leave requests with unpaid days for the given employee, ordered by newest first. */
    List<LeaveRequest> findByEmployeeIdAndUnpaidDaysUsedGreaterThanOrderByCreatedAtDesc(
            UUID employeeId, java.math.BigDecimal threshold);

    /**
     * Finds all approved leave requests for an employee that overlap a pay period and
     * involve unpaid days — either from an UNPAID policy (Case A) or earned leave
     * excess overflow (Case B: unpaidDaysUsed > 0).
     */
    @Query("""
        SELECT lr FROM LeaveRequest lr
        JOIN lr.leavePolicy lp
        WHERE lr.employee.id = :employeeId
          AND lr.leaveRequestStatus = 'APPROVED'
          AND lr.startDate <= :periodEnd
          AND lr.endDate >= :periodStart
          AND (lp.paymentType = 'UNPAID' OR lr.unpaidDaysUsed > 0)
        ORDER BY lr.startDate ASC
        """)
    List<LeaveRequest> findUnpaidLeaveRequestsForPeriod(
            @Param("employeeId") UUID employeeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}
