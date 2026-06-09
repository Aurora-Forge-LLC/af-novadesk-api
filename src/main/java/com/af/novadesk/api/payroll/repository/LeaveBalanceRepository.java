package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.constants.LeaveType;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeAndFiscalYearSettingId(
            UUID employeeId, LeaveType leaveType, UUID fiscalYearSettingId);
    List<LeaveBalance> findByEmployeeIdAndLeaveType(UUID employeeId, LeaveType leaveType);
    List<LeaveBalance> findByEmployeeId(UUID employeeId);
    List<LeaveBalance> findByFiscalYearSettingId(UUID fiscalYearSettingId);

    // Policy-based queries
    List<LeaveBalance> findByEmployeeIdAndLeavePolicyId(UUID employeeId, UUID leavePolicyId);
    List<LeaveBalance> findByLeavePolicyId(UUID leavePolicyId);
    Optional<LeaveBalance> findByEmployeeIdAndLeavePolicyIdAndFiscalYearSettingId(
            UUID employeeId, UUID leavePolicyId, UUID fiscalYearSettingId);
}
