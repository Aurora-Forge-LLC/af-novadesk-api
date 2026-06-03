package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.DepreciationMethod;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssetRegistrationRequest {

    @NotNull(message = "Legal entity is required")
    private UUID legalEntityId;

    @NotNull(message = "Category is required")
    private AssetCategory category;

    @NotBlank(message = "Asset type is required")
    @Size(max = 150)
    private String assetType;

    @Size(max = 150)
    private String manufacturer;

    @Size(max = 150)
    private String modelNumber;

    @NotBlank(message = "Serial number is required")
    @Size(max = 100)
    private String serialNumber;

    @NotNull(message = "Purchase date is required")
    @PastOrPresent(message = "Purchase date cannot be in the future")
    private LocalDate purchaseDate;

    @NotNull(message = "Purchase cost is required")
    @DecimalMin(value = "0.01", message = "Purchase cost must be greater than zero")
    private BigDecimal purchaseCost;

    @Size(max = 200)
    private String vendor;

    private LocalDate warrantyExpiryDate;

    @NotNull(message = "Depreciation method is required")
    private DepreciationMethod depreciationMethod;

    @NotNull(message = "Useful life is required")
    @Min(value = 1, message = "Useful life must be at least 1 year")
    private Integer usefulLifeYears;

    @Size(max = 300)
    private String currentLocation;

    @Size(max = 1000)
    private String notes;
}
