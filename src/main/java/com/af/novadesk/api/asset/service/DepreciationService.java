package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.dto.DepreciationScheduleDto;

import java.util.List;
import java.util.UUID;

public interface DepreciationService {

    /** Generates the full depreciation schedule for an asset at registration time. */
    List<DepreciationScheduleDto> generateSchedule(UUID assetId);

    /** Returns the depreciation schedule for display on the asset detail page. */
    List<DepreciationScheduleDto> getSchedule(UUID assetId);

    /**
     * Posts depreciation for all unposted schedules in a given fiscal year.
     * Called by the year-end scheduler — idempotent.
     */
    void postDepreciation(int fiscalYear);
}
