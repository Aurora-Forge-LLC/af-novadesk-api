package com.af.novadesk.api.asset.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DepreciationScheduleDto {
    private UUID id;
    private UUID assetId;
    private int fiscalYear;
    private BigDecimal annualDepreciation;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netBookValue;
    private boolean posted;
    private UUID journalEntryId;
    private LocalDateTime postedAt;
}
