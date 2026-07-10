package com.af.novadesk.api.asset.controller;

import com.af.novadesk.api.asset.api.AssetAssignmentApi;
import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.dto.AssetAssignmentDto;
import com.af.novadesk.api.asset.service.AssetAssignmentService;
import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.common.security.CallerContext;
import com.af.novadesk.api.common.util.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssetAssignmentController implements AssetAssignmentApi {

    private final AssetAssignmentService assetAssignmentService;
    private final CallerContext          callerContext;

    @Override
    public ResponseEntity<ApiResponse<PageResponse<AssetAssignmentDto>>> listAssignments(
            UUID employeeId, AssignmentStatus status, UUID assetId,
            LocalDate fromDate, LocalDate toDate,
            int page, int size, String sortBy, String sortDir) {

        UUID effectiveEmployeeId = employeeId;
        List<UUID> employeeIdIn = null;

        if (callerContext.canReadAllAssets()) {
            // IT_ADMIN / ENTITY_ADMIN — pass filters through unchanged
        } else if (callerContext.isManager()) {
            if (effectiveEmployeeId != null) {
                // Explicit filter — verify the requested employee is a direct report
                if (!callerContext.isDirectReport(effectiveEmployeeId)) {
                    throw new IllegalArgumentException(
                            "Access denied: employee is not one of your direct reports");
                }
            } else {
                // No filter — scope to all direct reports
                employeeIdIn = callerContext.getDirectReportIds();
            }
        } else {
            // EMPLOYEE — own assignments only
            effectiveEmployeeId = callerContext.getEmployeeId();
        }

        PageResponse<AssetAssignmentDto> result = assetAssignmentService.listFiltered(
                effectiveEmployeeId, employeeIdIn, status, assetId, fromDate, toDate, page, size, sortBy, sortDir);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
