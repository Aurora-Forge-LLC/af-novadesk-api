package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.constants.FlagAction;
import com.af.novadesk.api.payroll.entity.PayrollFlaggedEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayrollFlaggedEmployeeRepository extends JpaRepository<PayrollFlaggedEmployee, UUID> {
    List<PayrollFlaggedEmployee> findByPayrollBatchId(UUID payrollBatchId);
    List<PayrollFlaggedEmployee> findByPayrollBatchIdAndFlagAction(UUID payrollBatchId, FlagAction flagAction);
    List<PayrollFlaggedEmployee> findByFlagAction(FlagAction flagAction);
}
