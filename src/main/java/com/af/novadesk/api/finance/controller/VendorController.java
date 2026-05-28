package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.api.VendorApi;
import com.af.novadesk.api.finance.dto.VendorDto;
import com.af.novadesk.api.finance.dto.VendorPageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Stub controller for {@link VendorApi}.
 *
 * <p>Routes are registered and visible in Swagger UI.
 * Service-layer implementation is pending (LLR-FIN-03.3).</p>
 */
@RestController
public class VendorController implements VendorApi {

    @Override
    public ResponseEntity<ApiResponse<VendorDto>> createVendor(VendorDto request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<VendorPageDto>> listVendors(int page, int size, String sortBy, String search) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<VendorDto>> getVendor(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<VendorDto>> updateVendor(UUID id, VendorDto request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<VendorDto>> updateVendorStatus(UUID id, VendorDto request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
