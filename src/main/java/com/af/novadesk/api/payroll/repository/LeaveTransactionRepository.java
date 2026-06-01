package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.LeaveTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveTransactionRepository extends JpaRepository<LeaveTransaction, UUID> {
    List<LeaveTransaction> findByLeaveRequestIdOrderByCreatedAtAsc(UUID leaveRequestId);
    List<LeaveTransaction> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
