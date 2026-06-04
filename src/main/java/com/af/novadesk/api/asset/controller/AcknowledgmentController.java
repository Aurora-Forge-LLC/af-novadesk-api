package com.af.novadesk.api.asset.controller;

import com.af.novadesk.api.asset.api.AcknowledgmentApi;
import com.af.novadesk.api.asset.dto.AssetAssignmentDto;
import com.af.novadesk.api.asset.service.AssetAssignmentService;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AcknowledgmentController implements AcknowledgmentApi {

    private final AssetAssignmentService assignmentService;

    @Override
    public ResponseEntity<ApiResponse<AssetAssignmentDto>> acknowledge(
            String token, HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ResponseBuilder.ok(
                assignmentService.acknowledge(token, ip),
                "Asset receipt acknowledged successfully");
    }
}
