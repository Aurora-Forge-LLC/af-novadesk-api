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
    List<LeaveBalance> findByEmployeeId(UUID employeeId);
    List<LeaveBalance> findByFiscalYearSettingId(UUID fiscalYearSettingId);
}
