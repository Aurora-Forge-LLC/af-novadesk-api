package com.af.novadesk.api.asset.controller;

import com.af.novadesk.api.asset.api.WriteOffApi;
import com.af.novadesk.api.asset.dto.AssetDto;
import com.af.novadesk.api.asset.dto.WriteOffRequest;
import com.af.novadesk.api.asset.service.AssetWriteOffService;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WriteOffController implements WriteOffApi {

    private final AssetWriteOffService writeOffService;

    @Override
    public ResponseEntity<ApiResponse<AssetDto>> approve(UUID id, WriteOffRequest request) {
        return ResponseBuilder.ok(writeOffService.approveWriteOff(id, request), "Write-off approved");
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> reject(UUID id, String reason) {
        writeOffService.rejectWriteOff(id, reason);
        return ResponseBuilder.ok(null, "Write-off rejected");
    }
}
