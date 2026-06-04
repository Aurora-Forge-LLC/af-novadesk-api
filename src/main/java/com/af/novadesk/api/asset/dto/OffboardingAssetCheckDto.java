package com.af.novadesk.api.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.UUID;

/** Result of the offboarding asset gate check for an employee. */
@Data
@AllArgsConstructor
public class OffboardingAssetCheckDto {
    private UUID employeeId;
    /** True when the employee has no unreturned ACTIVE assignments. */
    private boolean cleared;
    private long unreturnedCount;
    private List<AssetAssignmentDto> unreturnedAssets;
}
