package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.DepreciationMethod;
import com.af.novadesk.api.asset.constants.WriteOffReason;
import com.af.novadesk.api.asset.constants.WriteOffStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AssetDto {
    private UUID id;
    private UUID legalEntityId;
    private AssetCategory category;
    private String assetType;
    private String manufacturer;
    private String modelNumber;
    private String serialNumber;
    private LocalDate purchaseDate;
    private BigDecimal purchaseCost;
    private String currencyCode;
    private String vendor;
    private LocalDate warrantyExpiryDate;
    private DepreciationMethod depreciationMethod;
    private Integer usefulLifeYears;
    private BigDecimal netBookValue;
    private BigDecimal accumulatedDepreciation;
    private String currentLocation;
    private AssetStatus assetStatus;
    private WriteOffReason writeOffReason;
    private WriteOffStatus writeOffStatus;
    private String notes;
    private String photoUrl;
    private String qrCodeUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
