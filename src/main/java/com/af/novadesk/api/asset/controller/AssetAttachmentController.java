package com.af.novadesk.api.asset.controller;

import com.af.novadesk.api.asset.api.AssetAttachmentApi;
import com.af.novadesk.api.asset.dto.AssetAttachmentDto;
import com.af.novadesk.api.asset.service.AssetAttachmentService;
import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssetAttachmentController implements AssetAttachmentApi {

    private final AssetAttachmentService attachmentService;

    @Override
    public ResponseEntity<ApiResponse<AssetAttachmentDto>> upload(UUID assetId, MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Attachment uploaded successfully",
                        attachmentService.upload(assetId, file)));
    }

    @Override
    public ResponseEntity<ApiResponse<List<AssetAttachmentDto>>> list(UUID assetId) {
        return ResponseBuilder.ok(attachmentService.list(assetId), ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> delete(UUID assetId, UUID attachmentId) {
        attachmentService.delete(assetId, attachmentId);
        return ResponseBuilder.ok(null, "Attachment deleted successfully");
    }
}
