package com.af.novadesk.api.maintenance.scheduler;

import com.af.novadesk.api.maintenance.constants.MaintenanceConstants;
import com.af.novadesk.api.maintenance.constants.MaintenancePriority;
import com.af.novadesk.api.maintenance.constants.MaintenanceStatus;
import com.af.novadesk.api.maintenance.entity.MaintenanceRequest;
import com.af.novadesk.api.maintenance.repository.MaintenanceRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Escalates the priority of maintenance requests that have sat unreviewed
 * in SUBMITTED status for too long, so they surface higher in ops queues.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceEscalationScheduler {

    private final MaintenanceRequestRepository maintenanceRequestRepository;

    /**
     * Runs hourly. Bumps SUBMITTED requests open at least
     * {@link MaintenanceConstants#ESCALATION_THRESHOLD_DAYS} days to HIGH
     * priority so they don't get lost in the queue.
     */
    @Scheduled(cron = "${app.maintenance.escalation-cron:0 0 * * * ?}")
    @Transactional
    public void escalateStaleRequests() {
        LocalDateTime now = LocalDateTime.now();
        List<MaintenanceRequest> allRequests = maintenanceRequestRepository.findAll();

        int escalatedCount = 0;
        for (MaintenanceRequest request : allRequests) {
            if (request.getMaintenanceStatus() != MaintenanceStatus.SUBMITTED) {
                continue;
            }
            if (request.getPriority() == MaintenancePriority.URGENT) {
                continue;
            }

            long daysOpen = Duration.between(request.getCreatedAt(), now).toDays();
            if (daysOpen > MaintenanceConstants.ESCALATION_THRESHOLD_DAYS) {
                request.setPriority(MaintenancePriority.HIGH);
                maintenanceRequestRepository.save(request);
                escalatedCount++;
            }
        }

        log.info("Maintenance escalation scheduler completed — {} request(s) escalated", escalatedCount);
    }
}
