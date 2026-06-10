package com.af.novadesk.api.payroll.scheduler;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.FiscalYearSetting;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.constants.LeaveType;
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
    private final CmEmployeeRepository cmEmployeeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    public LeaveBalanceRolloverJob(FiscalYearSettingRepository fiscalYearSettingRepository,
                                   CmEmployeeRepository cmEmployeeRepository,
                                   LeaveBalanceRepository leaveBalanceRepository) {
        this.fiscalYearSettingRepository = fiscalYearSettingRepository;
        this.cmEmployeeRepository = cmEmployeeRepository;
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
                List<CmEmployee> activeEmployees = cmEmployeeRepository.findAllByLegalEntityIdAndStatus(
                        fy.getLegalEntity().getId(), EmployeeStatus.ACTIVE);

                int newBalances = 0;
                for (CmEmployee emp : activeEmployees) {
                    createIfNotExists(emp, LeaveType.PAID, DEFAULT_PAID_LEAVE, fy);
                    createIfNotExists(emp, LeaveType.SICK, DEFAULT_SICK_LEAVE, fy);
                    createIfNotExists(emp, LeaveType.UNPAID, UNLIMITED_UNPAID, fy);
                    newBalances++;
                }
                log.info("Created new leave balances for {} employees in fiscal year setting {}", newBalances, fy.getId());
            }
        }
        log.info("Leave balance rollover check completed.");
    }

    private void createIfNotExists(CmEmployee employee, LeaveType type, BigDecimal allocated, FiscalYearSetting fy) {
        boolean exists = leaveBalanceRepository.findByEmployeeIdAndLeaveType(employee.getId(), type)
                .stream().anyMatch(b -> b.getFiscalYearSetting() != null
                        && b.getFiscalYearSetting().getId().equals(fy.getId()));
        if (!exists) {
            LeaveBalance balance = LeaveBalance.builder()
                    .organizationId(employee.getOrganizationId())
                    .employee(employee)
                    .leaveType(type)
                    .totalAllocated(allocated)
                    .usedDays(BigDecimal.ZERO)
                    .pendingDays(BigDecimal.ZERO)
                    .availableDays(allocated)
                    .accrualStartDate(java.time.LocalDate.now())
                    .fiscalYearSetting(fy)
                    .build();
            leaveBalanceRepository.save(balance);
        }
    }
}
