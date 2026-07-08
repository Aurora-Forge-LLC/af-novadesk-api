package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

public interface AssetService {

    /** Register a new asset, generate depreciation schedule, and publish HardwarePurchased event. */
    AssetDto register(AssetRegistrationRequest request);

    AssetDto getById(UUID id);

    AssetPageDto list(UUID legalEntityId, AssetStatus status, AssetCategory category,
                      String q, String manufacturer, String location,
                      LocalDate purchaseDateFrom, LocalDate purchaseDateTo,
                      LocalDate warrantyExpiryFrom, LocalDate warrantyExpiryTo,
                      int page, int size, String sortBy, String sortDir);

    /** Upload a photo to MinIO and attach the URL to the asset. */
    AssetDto uploadPhoto(UUID assetId, MultipartFile file);

    /** Return the QR code image URL; regenerates if missing. */
    String getQrCodeUrl(UUID assetId);
}
