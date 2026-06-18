package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.dto.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface AssetService {

    /** Register a new asset, generate depreciation schedule, and publish HardwarePurchased event. */
    AssetDto register(AssetRegistrationRequest request);

    AssetDto getById(UUID id);

    AssetPageDto list(UUID legalEntityId, AssetStatus status, AssetCategory category, Pageable pageable);

    /** Generate an unused serial number suggestion for the given category (e.g. {@code LAP-2026-0007}). */
    String generateNextSerialNumber(AssetCategory category);

    /** Manufacturers previously used by this organization for the given category, most-used first. */
    List<String> getManufacturerSuggestions(AssetCategory category);

    /** Upload a photo to MinIO and attach the URL to the asset. */
    AssetDto uploadPhoto(UUID assetId, MultipartFile file);

    /** Return the QR code image URL; regenerates if missing. */
    String getQrCodeUrl(UUID assetId);
}
