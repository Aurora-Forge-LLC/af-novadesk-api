package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.CustodianType;
import com.af.novadesk.api.asset.constants.CustodyTransferType;
import com.af.novadesk.api.asset.dto.*;
import com.af.novadesk.api.asset.entity.*;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.exception.InvalidAssetStateException;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.*;
import com.af.novadesk.api.asset.service.AssetReturnService;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetReturnServiceImpl implements AssetReturnService {

    private final AssetRepository                assetRepository;
    private final AssetAssignmentRepository      assignmentRepository;
    private final AssetReturnRepository          returnRepository;
    private final AssetCustodyTransferRepository custodyRepository;
    private final AssetMapper                    assetMapper;
    private final FinanceSecurityContext         securityContext;
    private final AssetOutboxServiceImpl         outboxService;

    @Override
    @Transactional
    public AssetDto recordReturn(UUID assetId, AssetReturnRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        Asset asset = assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        if (asset.getAssetStatus() != AssetStatus.ASSIGNED) {
            throw new InvalidAssetStateException(assetId, asset.getAssetStatus().name(), "return");
        }

        AssetAssignment assignment = assignmentRepository.findActiveByAssetId(assetId)
                .orElseThrow(() -> new BadRequestException("No active assignment found for asset: " + assetId));

        UUID returnedByEmployee = assignment.getEmployeeId();
        UUID receivedBy = request.getReceivedByEmployeeId();
        UUID approvedBy = securityContext.getAuthUserId();

        // Create return record
        AssetReturn assetReturn = AssetReturn.builder()
                .asset(asset)
                .assignment(assignment)
                .organizationId(orgId)
                .returnDate(request.getReturnDate())
                .returnedByEmployeeId(returnedByEmployee)
                .receivedByEmployeeId(receivedBy)
                .conditionAtReturn(request.getConditionAtReturn())
                .repairRequired(request.isRepairRequired())
                .notes(request.getNotes())
                .build();
        returnRepository.save(assetReturn);

        // Close assignment
        assignment.setAssignmentStatus(AssignmentStatus.RETURNED);
        assignmentRepository.save(assignment);

        // Update asset status
        asset.setAssetStatus(AssetStatus.RETURNED);
        Asset saved = assetRepository.save(asset);

        // Record custody transfer
        AssetCustodyTransfer transfer = AssetCustodyTransfer.builder()
                .asset(asset)
                .organizationId(orgId)
                .fromCustodianType(CustodianType.EMPLOYEE)
                .fromCustodianId(returnedByEmployee)
                .toCustodianType(CustodianType.IT_DEPARTMENT)
                .toCustodianId(null)
                .transferType(CustodyTransferType.RETURN)
                .transferDate(LocalDate.now())
                .approvedBy(approvedBy)
                .build();
        custodyRepository.save(transfer);

        // Publish outbox event in same transaction
        outboxService.publishAssetReturned(saved, returnedByEmployee, approvedBy);
        log.info("Asset {} returned by employee {}", assetId, returnedByEmployee);
        return assetMapper.toDto(saved);
    }

    @Override
    public List<CustodyTransferDto> getCustodyHistory(UUID assetId) {
        UUID orgId = securityContext.getOrganizationId();
        assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));
        return assetMapper.toCustodyTransferDtoList(
                custodyRepository.findAllByAssetIdOrderByCreatedAtAsc(assetId));
    }
}
