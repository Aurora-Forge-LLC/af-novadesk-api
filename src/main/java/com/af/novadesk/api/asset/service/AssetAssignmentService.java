package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.dto.*;

import java.util.List;
import java.util.UUID;

public interface AssetAssignmentService {

    /** Assign an asset to an employee. Sends acknowledgment email if required. */
    AssetAssignmentDto assign(UUID assetId, AssetAssignmentRequest request);

    /** Called from the public acknowledgment page — validates token, records IP + timestamp. */
    AssetAssignmentDto acknowledge(String token, String ipAddress);

    /** Reassign an asset from one employee to another. */
    AssetAssignmentDto reassign(UUID assetId, AssetAssignmentRequest request);

    AssetAssignmentDto getById(UUID id);

    /** All active assignments for an employee — drives the "My Assets" self-service view. */
    List<AssetAssignmentDto> listByEmployee(UUID employeeId);

    /** Offboarding gate: returns check result including list of unreturned assets. */
    OffboardingAssetCheckDto checkOffboarding(UUID employeeId);
}
