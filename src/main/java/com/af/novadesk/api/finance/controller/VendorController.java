package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.api.VendorApi;
import com.af.novadesk.api.finance.dto.UpdateVendorStatusRequest;
import com.af.novadesk.api.finance.dto.VendorDto;
import com.af.novadesk.api.finance.dto.VendorPageDto;
import com.af.novadesk.api.finance.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for Vendor Management (LLR-FIN-03.3).
 *
 * <p>Implements {@link VendorApi} — all routing, security, and Swagger annotations
 * are declared on the interface. This class only wires the service calls.</p>
 */
@RestController
@RequiredArgsConstructor
public class VendorController implements VendorApi {

    private final VendorService vendorService;

    @Override
    public ResponseEntity<ApiResponse<VendorDto>> createVendor(VendorDto request) {
        VendorDto created = vendorService.createVendor(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Vendor created successfully", created));
    }

    @Override
    public ResponseEntity<ApiResponse<VendorPageDto>> listVendors(int page, int size, String sortBy, String search) {
        VendorPageDto result = vendorService.listVendors(page, size, sortBy, search);
        return ResponseEntity.ok(ApiResponse.success(200, "Vendors retrieved successfully", result));
    }

    @Override
    public ResponseEntity<ApiResponse<VendorDto>> getVendor(UUID id) {
        VendorDto vendor = vendorService.getVendor(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Vendor retrieved successfully", vendor));
    }

    @Override
    public ResponseEntity<ApiResponse<VendorDto>> updateVendor(UUID id, VendorDto request) {
        VendorDto updated = vendorService.updateVendor(id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Vendor updated successfully", updated));
    }

    @Override
    public ResponseEntity<ApiResponse<VendorDto>> updateVendorStatus(UUID id, UpdateVendorStatusRequest request) {
        VendorDto updated = vendorService.updateVendorStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Vendor status updated successfully", updated));
    }
}
