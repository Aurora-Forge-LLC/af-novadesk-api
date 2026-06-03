package com.af.novadesk.api.asset.controller;

import com.af.novadesk.api.asset.api.AssetApi;
import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.dto.*;
import com.af.novadesk.api.asset.service.*;
import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssetController implements AssetApi {

    private final AssetService           assetService;
    private final AssetReturnService     assetReturnService;
    private final AssetAssignmentService assignmentService;
    private final DepreciationService    depreciationService;
    private final AssetWriteOffService   writeOffService;

    @Override
    public ResponseEntity<ApiResponse<AssetDto>> register(AssetRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Asset registered successfully", assetService.register(request)));
    }

    @Override
    public ResponseEntity<ApiResponse<AssetDto>> getById(UUID id) {
        return ResponseBuilder.ok(assetService.getById(id), ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<AssetPageDto>> list(
            UUID legalEntityId, AssetStatus status, AssetCategory category, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseBuilder.ok(assetService.list(legalEntityId, status, category, pageable), ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<AssetDto>> uploadPhoto(UUID id, MultipartFile file) {
        return ResponseBuilder.ok(assetService.uploadPhoto(id, file), "Photo uploaded successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<String>> getQrCode(UUID id) {
        return ResponseBuilder.ok(assetService.getQrCodeUrl(id), "QR code URL retrieved");
    }

    @Override
    public ResponseEntity<ApiResponse<List<DepreciationScheduleDto>>> getDepreciationSchedule(UUID id) {
        return ResponseBuilder.ok(depreciationService.getSchedule(id), ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<CustodyTransferDto>>> getCustodyHistory(UUID id) {
        return ResponseBuilder.ok(assetReturnService.getCustodyHistory(id), ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<AssetAssignmentDto>> assign(UUID id, AssetAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Asset assigned successfully", assignmentService.assign(id, request)));
    }

    @Override
    public ResponseEntity<ApiResponse<AssetAssignmentDto>> reassign(UUID id, AssetAssignmentRequest request) {
        return ResponseBuilder.ok(assignmentService.reassign(id, request), "Asset reassigned successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<AssetDto>> recordReturn(UUID id, AssetReturnRequest request) {
        return ResponseBuilder.ok(assetReturnService.recordReturn(id, request), "Asset return recorded successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> requestWriteOff(UUID id, WriteOffRequest request) {
        writeOffService.requestWriteOff(id, request);
        return ResponseBuilder.ok(null, "Write-off request submitted successfully");
    }
}
