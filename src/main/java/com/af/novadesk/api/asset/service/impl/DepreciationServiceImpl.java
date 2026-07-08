package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.DepreciationMethod;
import com.af.novadesk.api.asset.dto.DepreciationScheduleDto;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.DepreciationSchedule;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.repository.DepreciationScheduleRepository;
import com.af.novadesk.api.asset.service.DepreciationService;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepreciationServiceImpl implements DepreciationService {

    private final AssetRepository               assetRepository;
    private final DepreciationScheduleRepository scheduleRepository;
    private final AssetMapper                   assetMapper;
    private final FinanceSecurityContext        securityContext;
    private final EntityAccessGuard             entityAccessGuard;
    private final AssetOutboxServiceImpl        outboxService;

    @Override
    @Transactional
    public List<DepreciationScheduleDto> generateSchedule(UUID assetId) {
        UUID orgId = securityContext.getOrganizationId();
        Asset asset = assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        List<DepreciationSchedule> schedules = buildSchedule(asset);
        scheduleRepository.saveAll(schedules);
        log.info("Depreciation schedule generated for asset {} ({} years)",
                assetId, asset.getUsefulLifeYears());
        return assetMapper.toDepreciationDtoList(schedules);
    }

    @Override
    public List<DepreciationScheduleDto> getSchedule(UUID assetId) {
        // Ownership + entity-scope check: previously this looked up the schedule by
        // asset id alone with no org/entity guard (a cross-org/entity IDOR).
        UUID orgId = securityContext.getOrganizationId();
        Asset asset = assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));
        entityAccessGuard.assertCanAccessEntity(asset.getLegalEntity().getId());
        return assetMapper.toDepreciationDtoList(
                scheduleRepository.findAllByAssetIdOrderByFiscalYearAsc(assetId));
    }

    @Override
    @Transactional
    public void postDepreciation(int fiscalYear) {
        UUID orgId = securityContext.getOrganizationId();
        List<DepreciationSchedule> unposted = scheduleRepository.findUnpostedByOrgAndYear(orgId, fiscalYear);

        for (DepreciationSchedule schedule : unposted) {
            Asset asset = schedule.getAsset();
            if (asset.getAssetStatus() == AssetStatus.DISPOSED ||
                asset.getAssetStatus() == AssetStatus.FULLY_DEPRECATED) {
                continue;
            }

            // Publish accounting event (Debit Depreciation Expense / Credit Accumulated Depreciation)
            outboxService.publishDepreciationPosted(asset, schedule);
            schedule.setPosted(true);
            schedule.setPostedAt(java.time.LocalDateTime.now());

            // Update asset net book value
            asset.setAccumulatedDepreciation(schedule.getAccumulatedDepreciation());
            asset.setNetBookValue(schedule.getNetBookValue());
            if (schedule.getNetBookValue().compareTo(BigDecimal.ZERO) <= 0) {
                asset.setAssetStatus(AssetStatus.FULLY_DEPRECATED);
            }
            assetRepository.save(asset);
        }

        scheduleRepository.saveAll(unposted);
        log.info("Depreciation posted for fiscal year {} — {} assets", fiscalYear, unposted.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<DepreciationSchedule> buildSchedule(Asset asset) {
        BigDecimal cost         = asset.getPurchaseCost();
        int        usefulLife   = asset.getUsefulLifeYears();
        int        startYear    = asset.getPurchaseDate().getYear();
        BigDecimal accumulated  = BigDecimal.ZERO;
        BigDecimal bookValue    = cost;
        List<DepreciationSchedule> schedules = new ArrayList<>();

        for (int i = 0; i < usefulLife; i++) {
            int year = startYear + i;
            BigDecimal annual;

            if (asset.getDepreciationMethod() == DepreciationMethod.STRAIGHT_LINE) {
                annual = cost.divide(BigDecimal.valueOf(usefulLife), 4, RoundingMode.HALF_UP);
            } else {
                // Declining balance — 2x straight-line rate
                BigDecimal rate = BigDecimal.valueOf(2.0 / usefulLife);
                annual = bookValue.multiply(rate).setScale(4, RoundingMode.HALF_UP);
            }

            // Pro-rate first year if purchased mid-year
            if (i == 0) {
                int purchaseMonth  = asset.getPurchaseDate().getMonthValue();
                int monthsRemaining = 13 - purchaseMonth;
                annual = annual.multiply(BigDecimal.valueOf(monthsRemaining))
                               .divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            }

            // Cap at remaining book value
            annual     = annual.min(bookValue);
            accumulated = accumulated.add(annual);
            bookValue   = cost.subtract(accumulated).max(BigDecimal.ZERO);

            schedules.add(DepreciationSchedule.builder()
                    .asset(asset)
                    .organizationId(asset.getOrganizationId())
                    .fiscalYear(year)
                    .annualDepreciation(annual)
                    .accumulatedDepreciation(accumulated)
                    .netBookValue(bookValue)
                    .posted(false)
                    .build());

            if (bookValue.compareTo(BigDecimal.ZERO) == 0) break;
        }
        return schedules;
    }
}
