package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.AcknowledgmentStatus;
import com.af.novadesk.api.asset.constants.CustodianType;
import com.af.novadesk.api.asset.constants.CustodyTransferType;
import com.af.novadesk.api.asset.dto.*;
import com.af.novadesk.api.asset.entity.*;
import com.af.novadesk.api.asset.exception.*;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.*;
import com.af.novadesk.api.asset.service.AssetAssignmentService;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetAssignmentServiceImpl implements AssetAssignmentService {

    private final AssetRepository            assetRepository;
    private final AssetAssignmentRepository  assignmentRepository;
    private final AssetCustodyTransferRepository custodyRepository;
    private final AssetMapper                assetMapper;
    private final FinanceSecurityContext     securityContext;
    private final AssetEmailServiceImpl      emailService;
    private final AssetOutboxServiceImpl     outboxService;

    @Override
    @Transactional
    public AssetAssignmentDto assign(UUID assetId, AssetAssignmentRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        Asset asset = requireAsset(assetId, orgId);

        if (asset.getAssetStatus() != AssetStatus.AVAILABLE && asset.getAssetStatus() != AssetStatus.RETURNED) {
            throw new InvalidAssetStateException(assetId, asset.getAssetStatus().name(), "assign");
        }

        UUID assignedBy = securityContext.getAuthUserId();

        AssetAssignment assignment = AssetAssignment.builder()
                .asset(asset)
                .employeeId(request.getEmployeeId())
                .assignedBy(assignedBy)
                .organizationId(orgId)
                .assignmentDate(request.getAssignmentDate())
                .expectedReturnDate(request.getExpectedReturnDate())
                .purpose(request.getPurpose())
                .conditionAtAssignment(request.getConditionAtAssignment())
                .requiresAcknowledgment(request.isRequiresAcknowledgment())
                .acknowledgmentStatus(request.isRequiresAcknowledgment()
                        ? AcknowledgmentStatus.PENDING
                        : AcknowledgmentStatus.WAIVED)
                .acknowledgmentToken(request.isRequiresAcknowledgment()
                        ? UUID.randomUUID().toString()
                        : null)
                .acknowledgmentTokenExpiresAt(request.isRequiresAcknowledgment()
                        ? LocalDateTime.now().plusDays(7)
                        : null)
                .assignmentStatus(AssignmentStatus.ACTIVE)
                .notes(request.getNotes())
                .build();

        assignmentRepository.save(assignment);

        // Update asset status
        asset.setAssetStatus(AssetStatus.ASSIGNED);
        assetRepository.save(asset);

        // Record custody transfer
        recordTransfer(asset, orgId,
                CustodianType.IT_DEPARTMENT, null,
                CustodianType.EMPLOYEE, request.getEmployeeId(),
                CustodyTransferType.ASSIGNMENT, assignedBy);

        outboxService.publishAssetAssigned(assignment, assignedBy);
        log.info("Asset {} assigned to employee {}", assetId, request.getEmployeeId());

        // Send acknowledgment email asynchronously (non-blocking)
        // Employee email/name resolved from identity layer — using employeeId as placeholder for now
        if (request.isRequiresAcknowledgment()) {
            try {
                // TODO: resolve real email/displayName from ShadowUser by employeeId (Phase 3 polish)
                emailService.sendAcknowledgmentEmail(
                        assignment,
                        request.getEmployeeId() + "@placeholder.novadesk.com",
                        "Employee " + request.getEmployeeId().toString().substring(0, 8));
            } catch (Exception e) {
                log.warn("Email dispatch failed for assignment {} — continuing: {}", assignment.getId(), e.getMessage());
            }
        }

        return assetMapper.toAssignmentDto(assignment);
    }

    @Override
    @Transactional
    public AssetAssignmentDto acknowledge(String token, String ipAddress) {
        AssetAssignment assignment = assignmentRepository
                .findByAcknowledgmentToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired acknowledgment token"));

        if (assignment.getAcknowledgmentTokenExpiresAt() != null &&
                LocalDateTime.now().isAfter(assignment.getAcknowledgmentTokenExpiresAt())) {
            throw new BadRequestException("Acknowledgment token has expired");
        }

        assignment.setAcknowledgmentStatus(AcknowledgmentStatus.ACKNOWLEDGED);
        assignment.setAcknowledgmentAt(LocalDateTime.now());
        assignment.setAcknowledgmentIp(ipAddress);
        assignment.setAcknowledgmentToken(null);
        assignment.setAcknowledgmentTokenExpiresAt(null);

        assignmentRepository.save(assignment);
        log.info("Assignment {} acknowledged from IP {}", assignment.getId(), ipAddress);
        return assetMapper.toAssignmentDto(assignment);
    }

    @Override
    @Transactional
    public AssetAssignmentDto reassign(UUID assetId, AssetAssignmentRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        Asset asset = requireAsset(assetId, orgId);

        if (asset.getAssetStatus() != AssetStatus.ASSIGNED) {
            throw new InvalidAssetStateException(assetId, asset.getAssetStatus().name(), "reassign");
        }

        // Close current assignment
        AssetAssignment current = assignmentRepository.findActiveByAssetId(assetId)
                .orElseThrow(() -> new BadRequestException("No active assignment found for asset"));
        current.setAssignmentStatus(AssignmentStatus.TRANSFERRED);
        assignmentRepository.save(current);

        UUID approvedBy = securityContext.getAuthUserId();

        // Record transfer between employees
        recordTransfer(asset, orgId,
                CustodianType.EMPLOYEE, current.getEmployeeId(),
                CustodianType.EMPLOYEE, request.getEmployeeId(),
                CustodyTransferType.REASSIGNMENT, approvedBy);

        // Create new assignment
        return assign(assetId, request);
    }

    @Override
    public AssetAssignmentDto getById(UUID id) {
        UUID orgId = securityContext.getOrganizationId();
        AssetAssignment assignment = assignmentRepository
                .findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new BadRequestException("Assignment not found: " + id));
        return assetMapper.toAssignmentDto(assignment);
    }

    @Override
    public List<AssetAssignmentDto> listByEmployee(UUID employeeId) {
        UUID orgId = securityContext.getOrganizationId();
        return assignmentRepository
                .findUnresolvedByEmployeeId(employeeId, orgId)
                .stream()
                .map(assetMapper::toAssignmentDto)
                .collect(Collectors.toList());
    }

    @Override
    public OffboardingAssetCheckDto checkOffboarding(UUID employeeId) {
        UUID orgId = securityContext.getOrganizationId();
        long count = assignmentRepository.countActiveByEmployeeId(employeeId, orgId);
        List<AssetAssignmentDto> unreturned = count > 0 ? listByEmployee(employeeId) : List.of();
        return new OffboardingAssetCheckDto(employeeId, count == 0, count, unreturned);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Asset requireAsset(UUID assetId, UUID orgId) {
        return assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));
    }

    private void recordTransfer(Asset asset, UUID orgId,
                                CustodianType fromType, UUID fromId,
                                CustodianType toType, UUID toId,
                                CustodyTransferType transferType, UUID approvedBy) {
        AssetCustodyTransfer transfer = AssetCustodyTransfer.builder()
                .asset(asset)
                .organizationId(orgId)
                .fromCustodianType(fromType)
                .fromCustodianId(fromId)
                .toCustodianType(toType)
                .toCustodianId(toId)
                .transferType(transferType)
                .transferDate(LocalDate.now())
                .approvedBy(approvedBy)
                .build();
        custodyRepository.save(transfer);
    }
}
