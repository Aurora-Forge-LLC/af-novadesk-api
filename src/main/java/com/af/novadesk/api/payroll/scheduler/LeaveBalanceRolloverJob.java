package com.af.novadesk.api.payroll.scheduler;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.entity.LeavePolicy;
import com.af.novadesk.api.payroll.repository.EmployeeRepository;
import com.af.novadesk.api.payroll.repository.LeaveBalanceRepository;
import com.af.novadesk.api.payroll.repository.LeavePolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled job that detects fiscal year transitions per entity and creates
 * new LeaveBalance records for all active employees for the new fiscal year
 * using the configurable leave policy system. Old balances remain immutable
 * for audit trails.
 *
 * <p>Migrated from hardcoded {@code LeaveType} constants to the dynamic
 * {@link LeavePolicy} system. Each active leave policy is iterated per
 * employee, and balances are only created when they don't already exist
 * for the given (employee, policy, fiscal year) combination.</p>
 */
@Component
public class LeaveBalanceRolloverJob {

    private static final Logger log = LoggerFactory.getLogger(LeaveBalanceRolloverJob.class);

    private final FiscalYearSettingRepository fiscalYearSettingRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeavePolicyRepository leavePolicyRepository;

    public LeaveBalanceRolloverJob(FiscalYearSettingRepository fiscalYearSettingRepository,
                                   EmployeeRepository employeeRepository,
                                   LeaveBalanceRepository leaveBalanceRepository,
                                   LeavePolicyRepository leavePolicyRepository) {
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
        this.employeeRepository = employeeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leavePolicyRepository = leavePolicyRepository;
    }

    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void rolloverLeaveBalances() {
        log.info("Starting leave balance fiscal year rollover check (policy-based)...");
        List<FiscalYearSetting> fiscalYears = fiscalYearSettingRepository.findAll();

        for (FiscalYearSetting fy : fiscalYears) {
            LocalDate today = LocalDate.now();
            // Check if fiscal year just started today
            if (fy.getFiscalStartMonth() == today.getMonthValue()
                    && fy.getFiscalStartDay() == today.getDayOfMonth()) {
                log.info("Fiscal year rollover detected for entity with fiscal year setting: {}", fy.getId());
                rolloverForFiscalYear(fy, today);
            }
        }
        log.info("Leave balance rollover check completed.");
    }

    private void rolloverForFiscalYear(FiscalYearSetting fy, LocalDate today) {
        List<Employee> activeEmployees = employeeRepository
                .findByLegalEntityId(fy.getLegalEntity().getId());

        List<LeavePolicy> activePolicies = leavePolicyRepository
                .findByLegalEntityIdAndStatus(fy.getLegalEntity().getId(), Status.ACTIVE);

        if (activePolicies.isEmpty()) {
            log.warn("No active leave policies for entity {}. Skipping rollover.",
                    fy.getLegalEntity().getId());
            return;
        }

        int newBalances = 0;
        for (Employee emp : activeEmployees) {
            if (emp.getTerminationDate() != null
                    && !emp.getTerminationDate().isAfter(today)) {
                continue; // skip terminated employees
            }

            for (LeavePolicy policy : activePolicies) {
                Optional<LeaveBalance> existing = leaveBalanceRepository
                        .findByEmployeeIdAndLeavePolicyIdAndFiscalYearSettingId(
                                emp.getId(), policy.getId(), fy.getId());

                if (existing.isEmpty()) {
                    BigDecimal allocated = policy.getAllowedDaysAsDecimal();
                    boolean isEarned = Boolean.TRUE.equals(policy.getIsEarned());

                    LeaveBalance balance = LeaveBalance.builder()
                            .legalEntity(emp.getLegalEntity())
                            .employee(emp)
                            .leavePolicy(policy)
                            .totalAllocated(allocated)
                            .usedDays(BigDecimal.ZERO)
                            .pendingDays(BigDecimal.ZERO)
                            .availableDays(isEarned ? BigDecimal.ZERO : allocated)
                            .earnedDays(isEarned ? BigDecimal.ZERO : allocated)
                            .accrualStartDate(emp.getHireDate())
                            .fiscalYearSetting(fy)
                            .build();
                    leaveBalanceRepository.save(balance);
                    newBalances++;
                }
            }
        }
        log.info("Created {} new leave balances for fiscal year setting {} across {} policies",
                newBalances, fy.getId(), activePolicies.size());
    }
}
