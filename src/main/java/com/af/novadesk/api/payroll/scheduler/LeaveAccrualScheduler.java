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
import com.af.novadesk.api.payroll.service.LeaveRuleEngine;
import org.slf4j.Logger;

import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Monthly scheduled job that accrues earned leave days for all employees
 * under policies where {@code is_earned = true}.
 *
 * <p>Runs on the 1st of every month at 3:00 AM. For each earned leave policy,
 * adds the monthly accrual rate to every active employee's {@code earnedDays}
 * and recalculates their available balance.</p>
 *
 * <p>Mid-month hires: accrual is prorated based on the number of days remaining
 * in the hire month. The scheduler checks {@code accrualStartDate} on the
 * balance record to determine pro-ration.</p>
 */
@Component
public class LeaveAccrualScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaveAccrualScheduler.class);

    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final FiscalYearSettingRepository fiscalYearSettingRepository;
    private final LeaveRuleEngine leaveRuleEngine;

    public LeaveAccrualScheduler(LeavePolicyRepository leavePolicyRepository,
                                  LeaveBalanceRepository leaveBalanceRepository,
                                  EmployeeRepository employeeRepository,
                                  FiscalYearSettingRepository fiscalYearSettingRepository,
                                  LeaveRuleEngine leaveRuleEngine) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeRepository = employeeRepository;
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
        this.leaveRuleEngine = leaveRuleEngine;
    }

    /**
     * Runs on the 1st of every month at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 1 * ?")
    @Transactional
    public void accrueEarnedLeave() {
        log.info("Starting monthly earned leave accrual...");

        List<LeavePolicy> earnedPolicies = leavePolicyRepository
                .findByIsEarnedTrueAndStatus(Status.ACTIVE);

        if (earnedPolicies.isEmpty()) {
            log.info("No earned leave policies found. Skipping accrual.");
            return;
        }

        int totalAccrued = 0;
        int errorCount = 0;

        for (LeavePolicy policy : earnedPolicies) {
            try {
                int accrued = accrueForPolicy(policy);
                totalAccrued += accrued;
                log.debug("Accrued leave for policy '{}': {} employees", policy.getName(), accrued);
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to accrue leave for policy '{}': {}", policy.getName(), e.getMessage(), e);
            }
        }

        log.info("Monthly leave accrual completed. Employees accrued: {}, Errors: {}", totalAccrued, errorCount);
    }

    private int accrueForPolicy(LeavePolicy policy) {
        BigDecimal monthlyRate = leaveRuleEngine.calculateMonthlyAccrual(policy);
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        UUID legalEntityId = policy.getLegalEntity().getId();
        FiscalYearSetting fiscalYear = fiscalYearSettingRepository.findByLegalEntityId(legalEntityId)
                .stream().findFirst().orElse(null);

        if (fiscalYear == null) {
            log.warn("No fiscal year setting for entity {}", legalEntityId);
            return 0;
        }

        List<Employee> employees = employeeRepository.findByLegalEntityId(legalEntityId);
        LocalDate today = LocalDate.now();
        int accrued = 0;

        for (Employee emp : employees) {
            if (emp.getTerminationDate() != null && emp.getTerminationDate().isBefore(today)) {
                continue;
            }

            Optional<LeaveBalance> balanceOpt = leaveBalanceRepository
                    .findByEmployeeIdAndLeavePolicyIdAndFiscalYearSettingId(
                            emp.getId(), policy.getId(), fiscalYear.getId());

            if (balanceOpt.isPresent()) {
                LeaveBalance balance = balanceOpt.get();
                BigDecimal accrualAmount = calculateProratedAccrual(balance, monthlyRate, today);
                balance.setEarnedDays(balance.getEarnedDays().add(accrualAmount));

                // Recalculate available days: for earned leaves it's earnedDays - usedDays - pendingDays
                // (borrowing is calculated at request time)
                balance.setAvailableDays(
                        balance.getEarnedDays()
                                .subtract(balance.getUsedDays())
                                .subtract(balance.getPendingDays()));

                leaveBalanceRepository.save(balance);
                accrued++;
            }
        }

        return accrued;
    }

    /**
     * Calculates prorated accrual for the first month. If the employee was hired
     * mid-month, the accrual is proportional to the remaining days.
     */
    private BigDecimal calculateProratedAccrual(LeaveBalance balance, BigDecimal monthlyRate, LocalDate today) {
        LocalDate accrualStart = balance.getAccrualStartDate();
        if (accrualStart == null) {
            return monthlyRate;
        }

        // If hire date is in the current month, prorate
        if (accrualStart.getYear() == today.getYear() && accrualStart.getMonth() == today.getMonth()) {
            int daysInMonth = today.lengthOfMonth();
            int remainingDays = daysInMonth - accrualStart.getDayOfMonth() + 1;
            BigDecimal fraction = BigDecimal.valueOf(remainingDays)
                    .divide(BigDecimal.valueOf(daysInMonth), 2, java.math.RoundingMode.HALF_UP);
            return monthlyRate.multiply(fraction).setScale(1, java.math.RoundingMode.HALF_UP);
        }

        return monthlyRate;
    }
}
