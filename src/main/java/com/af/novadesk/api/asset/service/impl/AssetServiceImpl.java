package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.dto.*;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.exception.DuplicateSerialNumberException;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.repository.AssetWriteOffRepository;
import com.af.novadesk.api.asset.service.AssetService;
import com.af.novadesk.api.asset.service.DepreciationService;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public   class AssetServiceImpl implements AssetService {

    private final AssetRepository          assetRepository;
    private final AssetWriteOffRepository  writeOffRepository;
    private final LegalEntityRepository    legalEntityRepository;
    private final AssetMapper              assetMapper;
    private final FinanceSecurityContext   securityContext;
    private final EntityAccessGuard        entityAccessGuard;
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

        // Entity-scope guard: caller must be able to register assets for this entity.
        entityAccessGuard.assertCanAccessEntity(entity.getId());

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

        // Publish accounting event inside same transaction (Debit Fixed Assets / Credit Cash or AP)
        outboxService.publishAssetPurchased(saved);
        log.info("ASSET_PURCHASED outbox event persisted for asset {}", saved.getId());

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
        Asset asset = requireAssetInOrg(id);
        return enrichWithWriteOff(assetMapper.toDto(asset), asset.getId());
    }

    @Override
    public AssetPageDto list(UUID legalEntityId, AssetStatus status, AssetCategory category,
                             String q, String manufacturer, String location,
                             LocalDate purchaseDateFrom, LocalDate purchaseDateTo,
                             LocalDate warrantyExpiryFrom, LocalDate warrantyExpiryTo,
                             int page, int size, String sortBy, String sortDir) {
        UUID orgId = securityContext.getOrganizationId();
        Sort sort = "ASC".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Specification<Asset> spec = AssetRepository.filterSpec(
                orgId, legalEntityId, status, category,
                q, manufacturer, location,
                purchaseDateFrom, purchaseDateTo,
                warrantyExpiryFrom, warrantyExpiryTo);
        Page<Asset> result = assetRepository.findAll(spec, pageable);
        List<AssetDto> dtos = result.getContent().stream()
                .map(a -> enrichWithWriteOff(assetMapper.toDto(a), a.getId()))
                .toList();
        return new AssetPageDto(dtos, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public AssetDto uploadPhoto(UUID assetId, MultipartFile file) {
        Asset asset = requireAssetInOrg(assetId);
        entityAccessGuard.assertCanAccessEntity(asset.getLegalEntity().getId());
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

    @Override
    public byte[] getQrCodeBytes(UUID assetId) {
        Asset asset = requireAssetInOrg(assetId);
        String key = "assets/qr-codes/" + assetId + ".png";
        if (asset.getQrCodeUrl() == null) {
            qrCodeService.generateAndStore(assetId);
        }
        try (java.io.InputStream in = fileStorageService.download(key)) {
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to download QR code for asset: " + assetId, e);
        }
    }

    private AssetDto enrichWithWriteOff(AssetDto dto, UUID assetId) {
        if (dto.getAssetStatus() == AssetStatus.LOST
                || dto.getAssetStatus() == AssetStatus.WRITE_OFF_PENDING
                || dto.getAssetStatus() == AssetStatus.DISPOSED) {
            writeOffRepository.findByAssetId(assetId).ifPresent(w -> {
                dto.setWriteOffReason(w.getReason());
                dto.setWriteOffStatus(w.getWriteOffStatus());
            });
        }
        return dto;
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
