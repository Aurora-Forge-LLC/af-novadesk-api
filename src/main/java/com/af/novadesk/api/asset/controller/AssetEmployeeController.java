package com.af.novadesk.api.asset.controller;

import com.af.novadesk.api.asset.api.AssetEmployeeApi;
import com.af.novadesk.api.asset.dto.AssetAssignmentDto;
import com.af.novadesk.api.asset.dto.BulkAssetOffboardRequest;
import com.af.novadesk.api.asset.dto.BulkAssetOffboardResponse;
import com.af.novadesk.api.asset.dto.OffboardingAssetCheckDto;
import com.af.novadesk.api.asset.service.AssetAssignmentService;
import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.security.CallerContext;
import com.af.novadesk.api.common.util.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssetEmployeeController implements AssetEmployeeApi {

    private final AssetAssignmentService assignmentService;
    private final CallerContext          callerContext;

    @Override
    public ResponseEntity<ApiResponse<List<AssetAssignmentDto>>> listByEmployee(UUID employeeId) {
        assertEmployeeAccess(employeeId);
        return ResponseBuilder.ok(assignmentService.listByEmployee(employeeId), ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<OffboardingAssetCheckDto>> offboardingCheck(UUID employeeId) {
        assertEmployeeAccess(employeeId);
        return ResponseBuilder.ok(assignmentService.checkOffboarding(employeeId), "Offboarding check completed");
    }

    @Override
    public ResponseEntity<ApiResponse<BulkAssetOffboardResponse>> offboardAllAssets(
            UUID employeeId, BulkAssetOffboardRequest request) {
        if (request == null) {
            request = new BulkAssetOffboardRequest();
        }
        BulkAssetOffboardResponse result = assignmentService.offboardAllAssets(employeeId, request);
        return ResponseBuilder.ok(result, "Asset offboarding completed");
    }

    private void assertEmployeeAccess(UUID employeeId) {
        if (callerContext.canReadAllAssets()) return;
        if (callerContext.isSelf(employeeId)) return;
        if (callerContext.isManager() && callerContext.isDirectReport(employeeId)) return;
        throw new IllegalArgumentException("Access denied: you do not have access to this employee's assets");
    }
}
