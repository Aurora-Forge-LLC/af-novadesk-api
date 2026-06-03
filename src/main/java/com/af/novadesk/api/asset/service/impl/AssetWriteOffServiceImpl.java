package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.CustodianType;
import com.af.novadesk.api.asset.constants.CustodyTransferType;
import com.af.novadesk.api.asset.constants.WriteOffStatus;
import com.af.novadesk.api.asset.dto.AssetDto;
import com.af.novadesk.api.asset.dto.WriteOffRequest;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.AssetCustodyTransfer;
import com.af.novadesk.api.asset.entity.AssetWriteOff;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.exception.InvalidAssetStateException;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.AssetCustodyTransferRepository;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.repository.AssetWriteOffRepository;
import com.af.novadesk.api.asset.service.AssetWriteOffService;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetWriteOffServiceImpl implements AssetWriteOffService {

    private final AssetRepository                assetRepository;
    private final AssetWriteOffRepository        writeOffRepository;
    private final AssetCustodyTransferRepository custodyRepository;
    private final AssetMapper                    assetMapper;
    private final FinanceSecurityContext         securityContext;

    @Override
    @Transactional
    public AssetWriteOff requestWriteOff(UUID assetId, WriteOffRequest request) {
        UUID orgId     = securityContext.getOrganizationId();
        UUID requestedBy = securityContext.getAuthUserId();

        Asset asset = assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        if (asset.getAssetStatus() != AssetStatus.ASSIGNED && asset.getAssetStatus() != AssetStatus.LOST) {
            throw new InvalidAssetStateException(assetId, asset.getAssetStatus().name(), "write-off");
        }

        if (writeOffRepository.findByAssetId(assetId).isPresent()) {
            throw new BadRequestException("A write-off request already exists for asset: " + assetId);
        }

        // Determine last custodian from the most recent assignment
        UUID lastCustodian = requestedBy; // fallback — Phase 3 will resolve from assignment

        AssetWriteOff writeOff = AssetWriteOff.builder()
                .asset(asset)
                .organizationId(orgId)
                .requestedBy(requestedBy)
                .lastCustodianId(lastCustodian)
                .reason(request.getReason())
                .depreciatedValue(asset.getNetBookValue())
                .writeOffStatus(WriteOffStatus.PENDING)
                .auditNotes(request.getAuditNotes())
                .build();

        asset.setAssetStatus(AssetStatus.LOST);
        assetRepository.save(asset);

        AssetWriteOff saved = writeOffRepository.save(writeOff);
        log.info("Write-off requested for asset {} by {}", assetId, requestedBy);
        return saved;
    }

    @Override
    @Transactional
    public AssetDto approveWriteOff(UUID writeOffId, WriteOffRequest request) {
        UUID orgId     = securityContext.getOrganizationId();
        UUID approvedBy = securityContext.getAuthUserId();

        AssetWriteOff writeOff = writeOffRepository.findByIdAndOrganizationId(writeOffId, orgId)
                .orElseThrow(() -> new BadRequestException("Write-off not found: " + writeOffId));

        if (writeOff.getWriteOffStatus() != WriteOffStatus.PENDING) {
            throw new BadRequestException("Write-off is already " + writeOff.getWriteOffStatus());
        }
        if (request.getAction() == null) {
            throw new BadRequestException("Action is required when approving a write-off");
        }

        writeOff.setAction(request.getAction());
        writeOff.setApprovedBy(approvedBy);
        writeOff.setApprovedAt(LocalDateTime.now());
        writeOff.setWriteOffStatus(WriteOffStatus.APPROVED);
        writeOff.setAuditNotes(request.getAuditNotes());
        writeOffRepository.save(writeOff);

        Asset asset = writeOff.getAsset();
        asset.setAssetStatus(AssetStatus.DISPOSED);
        Asset saved = assetRepository.save(asset);

        // Record custody transfer — final disposal
        custodyRepository.save(AssetCustodyTransfer.builder()
                .asset(asset)
                .organizationId(orgId)
                .fromCustodianType(CustodianType.EMPLOYEE)
                .fromCustodianId(writeOff.getLastCustodianId())
                .toCustodianType(CustodianType.IT_DEPARTMENT)
                .toCustodianId(null)
                .transferType(CustodyTransferType.WRITE_OFF)
                .transferDate(LocalDate.now())
                .approvedBy(approvedBy)
                .notes(request.getAction().name())
                .build());

        log.info("Write-off {} approved — action={}", writeOffId, request.getAction());
        return assetMapper.toDto(saved);
    }

    @Override
    @Transactional
    public AssetWriteOff rejectWriteOff(UUID writeOffId, String reason) {
        UUID orgId = securityContext.getOrganizationId();
        AssetWriteOff writeOff = writeOffRepository.findByIdAndOrganizationId(writeOffId, orgId)
                .orElseThrow(() -> new BadRequestException("Write-off not found: " + writeOffId));

        if (writeOff.getWriteOffStatus() != WriteOffStatus.PENDING) {
            throw new BadRequestException("Write-off is already " + writeOff.getWriteOffStatus());
        }

        writeOff.setWriteOffStatus(WriteOffStatus.REJECTED);
        writeOff.setAuditNotes(reason);
        writeOff.setApprovedBy(securityContext.getAuthUserId());
        writeOff.setApprovedAt(LocalDateTime.now());

        // Revert asset to ASSIGNED if it was lost
        Asset asset = writeOff.getAsset();
        if (asset.getAssetStatus() == AssetStatus.LOST) {
            asset.setAssetStatus(AssetStatus.ASSIGNED);
            assetRepository.save(asset);
        }

        AssetWriteOff saved = writeOffRepository.save(writeOff);
        log.info("Write-off {} rejected", writeOffId);
        return saved;
    }
}
