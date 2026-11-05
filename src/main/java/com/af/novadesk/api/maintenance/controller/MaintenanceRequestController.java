package com.af.novadesk.api.maintenance.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.maintenance.api.MaintenanceRequestApi;
import com.af.novadesk.api.maintenance.constants.MaintenanceStatus;
import com.af.novadesk.api.maintenance.dto.MaintenanceApprovalRequest;
import com.af.novadesk.api.maintenance.dto.MaintenanceRequestCreateRequest;
import com.af.novadesk.api.maintenance.dto.MaintenanceRequestDto;
import com.af.novadesk.api.maintenance.dto.TechnicianAssignmentRequest;
import com.af.novadesk.api.maintenance.service.MaintenanceRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MaintenanceRequestController implements MaintenanceRequestApi {

    private final MaintenanceRequestService maintenanceRequestService;

    @Override
    public ResponseEntity<ApiResponse<MaintenanceRequestDto>> create(MaintenanceRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Maintenance request submitted successfully", maintenanceRequestService.create(request)));
    }

    @Override
    public ResponseEntity<ApiResponse<MaintenanceRequestDto>> getById(UUID id) {
        return ResponseBuilder.ok(maintenanceRequestService.fetchById(id), ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<MaintenanceRequestDto>>> list(UUID assetId, MaintenanceStatus status) {
        return ResponseBuilder.ok(maintenanceRequestService.list(assetId, status), ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<MaintenanceRequestDto>> approve(UUID id, MaintenanceApprovalRequest request) {
        return ResponseBuilder.ok(maintenanceRequestService.approve(id, request), "Maintenance request approved");
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> reject(UUID id, String reason) {
        maintenanceRequestService.reject(id, reason);
        return ResponseBuilder.ok(null, "Maintenance request rejected");
    }

    @Override
    public ResponseEntity<ApiResponse<MaintenanceRequestDto>> assignTechnician(UUID id, TechnicianAssignmentRequest request) {
        return ResponseBuilder.ok(maintenanceRequestService.assignTechnician(id, request), "Technician assigned");
    }

    @Override
    public ResponseEntity<ApiResponse<MaintenanceRequestDto>> complete(UUID id, String notes) {
        return ResponseBuilder.ok(maintenanceRequestService.complete(id, notes), "Maintenance request marked complete");
    }
}
