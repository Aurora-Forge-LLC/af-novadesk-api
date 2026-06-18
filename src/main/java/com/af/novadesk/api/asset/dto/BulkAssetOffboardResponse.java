package com.af.novadesk.api.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Result of a bulk asset offboarding operation for an employee.
 */
@Data
@Builder
@AllArgsConstructor
public class BulkAssetOffboardResponse {

    private UUID employeeId;

    /** Number of assets successfully returned in this operation. */
    private int returnedCount;

    /** Number of assets that remain as LOST (pending write-off) — not auto-returned. */
    private int lostCount;

    /** List of assignment DTOs for the assets that were returned. */
    private List<AssetAssignmentDto> returnedAssets;
}
