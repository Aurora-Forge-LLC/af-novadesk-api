package com.af.novadesk.api.payroll.scheduler;

import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import com.af.novadesk.api.payroll.repository.LeaveRequestRepository;
import com.af.novadesk.api.payroll.service.LeaveRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that finds {@link LeaveRequestStatus#PENDING} and
 * {@link LeaveRequestStatus#MODIFICATION_REQUESTED} leave requests
 * whose start date has passed and auto-expires them.
 *
 * <p>This prevents leave balance days from being permanently locked when a
 * manager fails to act on a leave request before the start date. The job
 * restores pending days to the employee's available balance and transitions
 * the request to {@link LeaveRequestStatus#EXPIRED}.</p>
 *
 * <p>Runs daily at 1:00 AM to clean up any requests that became stale
 * during the previous day. Follows the same pattern as
 * {@link LeaveBalanceRolloverJob}.</p>
 */
@Component
public class LeaveExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(LeaveExpiryJob.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestService leaveRequestService;

    public LeaveExpiryJob(LeaveRequestRepository leaveRequestRepository,
                          LeaveRequestService leaveRequestService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveRequestService = leaveRequestService;
    }

    /**
     * Runs daily at 1:00 AM. Finds all stale leave requests (PENDING or
     * MODIFICATION_REQUESTED with start_date before today) and expires them.
     *
     * <p>Each request is processed individually with per-request error handling
     * to prevent a single failure from blocking the entire batch.</p>
     */
    @Scheduled(cron = "0 0 1 * * ?") // Daily at 1:00 AM
    @Transactional
    public void expireStaleLeaveRequests() {
        log.info("Starting stale leave request expiry check...");

        LocalDate today = LocalDate.now();
        List<LeaveRequestStatus> staleStatuses = List.of(
                LeaveRequestStatus.PENDING,
                LeaveRequestStatus.MODIFICATION_REQUESTED
        );

        List<LeaveRequest> staleRequests;
        try {
            staleRequests = leaveRequestRepository
                    .findByStatusesAndStartDateBefore(staleStatuses, today);
        } catch (Exception e) {
            log.error("Failed to query stale leave requests: {}", e.getMessage(), e);
            return;
        }

        if (staleRequests.isEmpty()) {
            log.info("No stale leave requests found for expiry.");
            return;
        }

        log.info("Found {} stale leave request(s) to expire.", staleRequests.size());

        int expiredCount = 0;
        int errorCount = 0;

        for (LeaveRequest lr : staleRequests) {
            try {
                leaveRequestService.expireLeaveRequest(lr.getId());
                expiredCount++;
                log.debug("Expired leave request {} for employee {} (start: {}, status: {})",
                        lr.getId(), lr.getEmployee().getId(), lr.getStartDate(),
                        lr.getLeaveRequestStatus());
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to expire leave request {}: {}", lr.getId(), e.getMessage(), e);
            }
        }

        log.info("Leave expiry completed. Expired: {}, Errors: {}", expiredCount, errorCount);
    }
}
