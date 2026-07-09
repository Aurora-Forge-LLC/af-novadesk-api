package com.af.novadesk.api.asset.controller;

import com.af.novadesk.api.asset.api.AssetAssignmentApi;
import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.dto.AssetAssignmentDto;
import com.af.novadesk.api.asset.service.AssetAssignmentService;
import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssetAssignmentController implements AssetAssignmentApi {

    private final AssetAssignmentService assetAssignmentService;

    @Override
    public ResponseEntity<ApiResponse<PageResponse<AssetAssignmentDto>>> listAssignments(
            UUID employeeId, AssignmentStatus status, UUID assetId,
            LocalDate fromDate, LocalDate toDate,
            int page, int size, String sortBy, String sortDir) {
        PageResponse<AssetAssignmentDto> result = assetAssignmentService.listFiltered(
                employeeId, status, assetId, fromDate, toDate, page, size, sortBy, sortDir);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
