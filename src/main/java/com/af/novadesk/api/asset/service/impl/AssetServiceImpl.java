package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.dto.*;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.exception.DuplicateSerialNumberException;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.service.AssetService;
import com.af.novadesk.api.asset.service.DepreciationService;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetServiceImpl implements AssetService {

    private final AssetRepository          assetRepository;
    private final LegalEntityRepository    legalEntityRepository;
    private final AssetMapper              assetMapper;
    private final FinanceSecurityContext   securityContext;
    private final DepreciationService      depreciationService;
    private final QrCodeServiceImpl        qrCodeService;
    private final AssetOutboxServiceImpl   outboxService;
    private final com.af.novadesk.api.common.service.FileStorageService fileStorageService;

    @Override
    @Transactional
    public AssetDto register(AssetRegistrationRequest request) {
        UUID orgId = securityContext.getOrganizationId();

        LegalEntity entity = legalEntityRepository
                .findByIdAndOrganizationId(request.getLegalEntityId(), orgId)
                .orElseThrow(() -> new EntityNotFoundException(request.getLegalEntityId()));

        String trimmedSerial = request.getSerialNumber().trim().toUpperCase();
        if (assetRepository.existsBySerialNumberAndOrganizationId(trimmedSerial, orgId)) {
            throw new DuplicateSerialNumberException(trimmedSerial);
        }

        Asset asset = Asset.builder()
                .legalEntity(entity)
                .organizationId(orgId)
                .category(request.getCategory())
                .assetType(request.getAssetType())
                .manufacturer(request.getManufacturer())
                .modelNumber(request.getModelNumber())
                .serialNumber(trimmedSerial)
                .purchaseDate(request.getPurchaseDate())
                .purchaseCost(request.getPurchaseCost())
                .currencyCode(entity.getBaseCurrency())
                .vendor(request.getVendor())
                .warrantyExpiryDate(request.getWarrantyExpiryDate())
                .depreciationMethod(request.getDepreciationMethod())
                .usefulLifeYears(request.getUsefulLifeYears())
                .netBookValue(request.getPurchaseCost())
                .currentLocation(request.getCurrentLocation())
                .assetStatus(AssetStatus.AVAILABLE)
                .notes(request.getNotes())
                .build();

        Asset saved = assetRepository.save(asset);
        log.info("Asset registered: id={} serial={} entity={}", saved.getId(), trimmedSerial, entity.getEntityCode());

        // Generate depreciation schedule
        depreciationService.generateSchedule(saved.getId());

        // Publish accounting event (Debit Fixed Assets / Credit Cash or AP)
        outboxService.publishAssetPurchased(saved);

        // Generate QR code label
        try {
            qrCodeService.generateAndStore(saved.getId());
            saved = assetRepository.findById(saved.getId()).orElse(saved);
        } catch (Exception e) {
            log.warn("QR code generation failed for asset {} — continuing without it: {}", saved.getId(), e.getMessage(), e);
        }

        return assetMapper.toDto(saved);
    }

    @Override
    public AssetDto getById(UUID id) {
        return assetMapper.toDto(requireAssetInOrg(id));
    }

    @Override
    public AssetPageDto list(UUID legalEntityId, AssetStatus status, AssetCategory category, Pageable pageable) {
        UUID orgId = securityContext.getOrganizationId();
        Page<Asset> page;

        if (status != null) {
            page = assetRepository.findAllByOrganizationIdAndStatus(orgId, status, pageable);
        } else if (category != null) {
            page = assetRepository.findAllByOrganizationIdAndCategory(orgId, category, pageable);
        } else if (legalEntityId != null) {
            page = assetRepository.findAllByLegalEntityIdAndOrganizationId(legalEntityId, orgId, pageable);
        } else {
            page = assetRepository.findAllByOrganizationId(orgId, pageable);
        }

        return new AssetPageDto(
                assetMapper.toDtoList(page.getContent()),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional
    public AssetDto uploadPhoto(UUID assetId, MultipartFile file) {
        Asset asset = requireAssetInOrg(assetId);
        try {
            String key = "assets/photos/" + assetId + "." + getExtension(file.getOriginalFilename());
            fileStorageService.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
            String url = fileStorageService.generatePresignedUrl(key, java.time.Duration.ofDays(3650));
            asset.setPhotoUrl(url);
            Asset saved = assetRepository.save(asset);
            return assetMapper.toDto(saved);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to upload photo for asset: " + assetId, e);
        }
    }

    @Override
    public String getQrCodeUrl(UUID assetId) {
        Asset asset = requireAssetInOrg(assetId);
        if (asset.getQrCodeUrl() == null) {
            return qrCodeService.generateAndStore(assetId);
        }
        return asset.getQrCodeUrl();
    }

    private static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Asset requireAssetInOrg(UUID assetId) {
        return assetRepository
                .findByIdAndOrganizationId(assetId, securityContext.getOrganizationId())
                .orElseThrow(() -> new AssetNotFoundException(assetId));
    }
}
