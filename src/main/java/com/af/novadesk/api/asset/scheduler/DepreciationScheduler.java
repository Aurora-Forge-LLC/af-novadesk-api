package com.af.novadesk.api.asset.scheduler;

import com.af.novadesk.api.asset.service.DepreciationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Fiscal-year-end depreciation scheduler (LLR-AST-04.2).
 *
 * <p>Runs at 23:59 on December 31 (configurable via {@code app.asset.depreciation-cron}).
 * Calls {@link DepreciationService#postDepreciation} which is idempotent — safe to
 * re-run if the job fails and is retried.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepreciationScheduler {

    private final DepreciationService depreciationService;

    /**
     * Posts depreciation for all unposted schedules in the current fiscal year.
     * Cron: {@code app.asset.depreciation-cron} — default 23:59 on Dec 31.
     */
    @Scheduled(cron = "${app.asset.depreciation-cron:0 59 23 31 12 ?}")
    public void runFiscalYearEndDepreciation() {
        int currentYear = Year.now().getValue();
        log.info("Depreciation scheduler triggered — fiscal year {}", currentYear);
        try {
            depreciationService.postDepreciation(currentYear);
            log.info("Depreciation scheduler completed successfully for fiscal year {}", currentYear);
        } catch (Exception e) {
            log.error("Depreciation scheduler failed for fiscal year {}: {}", currentYear, e.getMessage(), e);
        }
    }
}
