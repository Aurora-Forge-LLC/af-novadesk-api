package com.af.novadesk.api.payroll.scheduler;

import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.constants.LeaveType;
import com.af.novadesk.api.payroll.repository.EmployeeRepository;
import com.af.novadesk.api.payroll.repository.LeaveBalanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that detects fiscal year transitions per entity and creates
 * new LeaveBalance records for all active employees for the new fiscal year.
 * Old balances remain immutable for audit trails.
 */
@Component
public class LeaveBalanceRolloverJob {

    private static final Logger log = LoggerFactory.getLogger(LeaveBalanceRolloverJob.class);
    private static final BigDecimal DEFAULT_PAID_LEAVE = BigDecimal.valueOf(10);
    private static final BigDecimal DEFAULT_SICK_LEAVE = BigDecimal.valueOf(5);
    private static final BigDecimal UNLIMITED_UNPAID = BigDecimal.valueOf(999);

    private final FiscalYearSettingRepository fiscalYearSettingRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    public LeaveBalanceRolloverJob(FiscalYearSettingRepository fiscalYearSettingRepository,
                                   EmployeeRepository employeeRepository,
                                   LeaveBalanceRepository leaveBalanceRepository) {
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
        this.employeeRepository = employeeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void rolloverLeaveBalances() {
        log.info("Starting leave balance fiscal year rollover check...");
        List<FiscalYearSetting> fiscalYears = fiscalYearSettingRepository.findAll();

        for (FiscalYearSetting fy : fiscalYears) {
            LocalDate today = LocalDate.now();
            // Check if fiscal year just started today
            if (fy.getFiscalStartMonth() == today.getMonthValue() && fy.getFiscalStartDay() == today.getDayOfMonth()) {
                log.info("Fiscal year rollover detected for entity with fiscal year setting: {}", fy.getId());
                List<Employee> activeEmployees = employeeRepository.findByLegalEntityId(fy.getLegalEntity().getId());

                int newBalances = 0;
                for (Employee emp : activeEmployees) {
                    if (emp.getTerminationDate() == null || emp.getTerminationDate().isAfter(today)) {
                        createIfNotExists(emp, LeaveType.PAID, DEFAULT_PAID_LEAVE, fy);
                        createIfNotExists(emp, LeaveType.SICK, DEFAULT_SICK_LEAVE, fy);
                        createIfNotExists(emp, LeaveType.UNPAID, UNLIMITED_UNPAID, fy);
                        newBalances++;
                    }
                }
                log.info("Created new leave balances for {} employees in fiscal year setting {}", newBalances, fy.getId());
            }
        }
        log.info("Leave balance rollover check completed.");
    }

    private void createIfNotExists(Employee employee, LeaveType type, BigDecimal allocated, FiscalYearSetting fy) {
        boolean exists = leaveBalanceRepository.findByEmployeeIdAndLeaveType(employee.getId(), type)
                .stream().anyMatch(b -> b.getFiscalYearSetting() != null
                        && b.getFiscalYearSetting().getId().equals(fy.getId()));
        if (!exists) {
            LeaveBalance balance = LeaveBalance.builder()
                    .legalEntity(employee.getLegalEntity())
                    .employee(employee)
                    .leaveType(type)
                    .totalAllocated(allocated)
                    .usedDays(BigDecimal.ZERO)
                    .pendingDays(BigDecimal.ZERO)
                    .availableDays(allocated)
                    .accrualStartDate(employee.getHireDate())
                    .fiscalYearSetting(fy)
                    .build();
            leaveBalanceRepository.save(balance);
        }
    }
}
