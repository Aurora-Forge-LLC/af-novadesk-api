package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.dto.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AssetService {

    /** Register a new asset, generate depreciation schedule, and publish HardwarePurchased event. */
    AssetDto register(AssetRegistrationRequest request);

    AssetDto getById(UUID id);

    AssetPageDto list(UUID legalEntityId, AssetStatus status, AssetCategory category, Pageable pageable);

    /** Upload a photo to MinIO and attach the URL to the asset. */
    AssetDto uploadPhoto(UUID assetId, MultipartFile file);

    /** Return the QR code image URL; regenerates if missing. */
    String getQrCodeUrl(UUID assetId);

    /** Stream the QR code PNG bytes; regenerates if not yet stored. */
    byte[] getQrCodeBytes(UUID assetId);
}
