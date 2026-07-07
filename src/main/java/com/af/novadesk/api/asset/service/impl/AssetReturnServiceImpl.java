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
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final CmEmployeeRepository           cmEmployeeRepository;

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
        UUID approvedBy = securityContext.getAuthUserId();

        // Admins processing returns may not have a CmEmployee record — fall back
        // to their authUserId so the (non-FK) received_by_employee_id column is
        // still populated with a meaningful identifier.
        UUID receivedBy = cmEmployeeRepository
                .findByAuthUserIdAndOrganizationId(approvedBy, orgId)
                .map(CmEmployee::getId)
                .orElse(approvedBy);

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

        List<AssetCustodyTransfer> transfers = custodyRepository.findAllByAssetIdOrderByCreatedAtAsc(assetId);
        List<CustodyTransferDto> dtos = assetMapper.toCustodyTransferDtoList(transfers);

        // Resolve custodian/employee display names (fromCustodianId/toCustodianId are cm_employees.id)
        Set<UUID> employeeIds = transfers.stream()
                .flatMap(t -> Stream.of(t.getFromCustodianId(), t.getToCustodianId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> namesByEmployeeId = employeeIds.isEmpty() ? Map.of()
                : cmEmployeeRepository.findAllById(employeeIds).stream()
                        .collect(Collectors.toMap(CmEmployee::getId, CmEmployee::getDisplayName));

        // Resolve approver display names (approvedBy is the JWT authUserId, not cm_employees.id)
        Set<UUID> approverAuthUserIds = transfers.stream()
                .map(AssetCustodyTransfer::getApprovedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> namesByAuthUserId = approverAuthUserIds.isEmpty() ? Map.of()
                : cmEmployeeRepository.findAllByAuthUserIdInAndOrganizationId(approverAuthUserIds, orgId).stream()
                        .collect(Collectors.toMap(CmEmployee::getAuthUserId, CmEmployee::getDisplayName));

        for (CustodyTransferDto dto : dtos) {
            if (dto.getFromCustodianId() != null) dto.setFromCustodianName(namesByEmployeeId.get(dto.getFromCustodianId()));
            if (dto.getToCustodianId() != null) dto.setToCustodianName(namesByEmployeeId.get(dto.getToCustodianId()));
            if (dto.getApprovedBy() != null) dto.setApprovedByName(namesByAuthUserId.get(dto.getApprovedBy()));
        }

        return dtos;
    }
}
