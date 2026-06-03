package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.CustodianType;
import com.af.novadesk.api.asset.constants.CustodyTransferType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CustodyTransferDto {
    private UUID id;
    private UUID assetId;
    private CustodianType fromCustodianType;
    private UUID fromCustodianId;
    private CustodianType toCustodianType;
    private UUID toCustodianId;
    private CustodyTransferType transferType;
    private LocalDate transferDate;
    private UUID approvedBy;
    private String notes;
    private LocalDateTime createdAt;
}
