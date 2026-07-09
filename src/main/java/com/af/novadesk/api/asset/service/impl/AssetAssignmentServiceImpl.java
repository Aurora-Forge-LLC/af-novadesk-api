package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.AcknowledgmentStatus;
import com.af.novadesk.api.asset.constants.ConditionGrade;
import com.af.novadesk.api.asset.constants.CustodianType;
import com.af.novadesk.api.asset.constants.CustodyTransferType;
import com.af.novadesk.api.asset.dto.*;
import com.af.novadesk.api.asset.entity.*;
import com.af.novadesk.api.asset.exception.*;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.*;
import com.af.novadesk.api.asset.repository.AssetAssignmentRepository;
import com.af.novadesk.api.asset.service.AssetAssignmentService;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final EntityAccessGuard          entityAccessGuard;
    private final AssetEmailServiceImpl      emailService;
    private final AssetOutboxServiceImpl     outboxService;
    private final AssetReturnRepository      returnRepository;
    private final CmEmployeeRepository       cmEmployeeRepository;

    @Override
    @Transactional
    public AssetAssignmentDto assign(UUID assetId, AssetAssignmentRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        Asset asset = requireAsset(assetId, orgId);
        entityAccessGuard.assertCanAccessEntity(asset.getLegalEntity().getId());

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
                .orElseThrow(() -> new InvalidAcknowledgmentTokenException(token));

        if (assignment.getAcknowledgmentTokenExpiresAt() != null &&
                LocalDateTime.now().isAfter(assignment.getAcknowledgmentTokenExpiresAt())) {
            throw new InvalidAcknowledgmentTokenException(token, "token has expired");
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
        entityAccessGuard.assertCanAccessEntity(asset.getLegalEntity().getId());

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
    public PageResponse<AssetAssignmentDto> listFiltered(
            UUID employeeId, AssignmentStatus status, UUID assetId,
            LocalDate fromDate, LocalDate toDate,
            int page, int size, String sortBy, String sortDir) {
        UUID orgId = securityContext.getOrganizationId();
        Sort.Direction dir = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy : "assignmentDate";
        PageRequest pageable = PageRequest.of(page, size, Sort.by(dir, field));
        Page<AssetAssignment> resultPage = assignmentRepository.findAll(
                AssetAssignmentRepository.filterSpec(orgId, employeeId, status, assetId, fromDate, toDate),
                pageable);
        return PageResponse.of(resultPage.map(assetMapper::toAssignmentDto));
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

    @Override
    @Transactional
    public BulkAssetOffboardResponse offboardAllAssets(UUID employeeId, BulkAssetOffboardRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID approvedBy = securityContext.getAuthUserId();
        ConditionGrade condition = request.getConditionAtReturn() != null
                ? request.getConditionAtReturn() : ConditionGrade.GOOD;

        // Resolve receivedBy: admin's CmEmployee ID or fall back to authUserId
        UUID receivedBy = cmEmployeeRepository
                .findByAuthUserIdAndOrganizationId(approvedBy, orgId)
                .map(CmEmployee::getId)
                .orElse(approvedBy);

        // Get all ACTIVE assignments for this employee
        List<AssetAssignment> activeAssignments = assignmentRepository
                .findByEmployeeIdAndStatus(employeeId, orgId, AssignmentStatus.ACTIVE);

        List<AssetAssignmentDto> returnedAssets = new ArrayList<>();

        for (AssetAssignment assignment : activeAssignments) {
            Asset asset = assignment.getAsset();

            // Create return record
            AssetReturn assetReturn = AssetReturn.builder()
                    .asset(asset)
                    .assignment(assignment)
                    .organizationId(orgId)
                    .returnDate(LocalDate.now())
                    .returnedByEmployeeId(employeeId)
                    .receivedByEmployeeId(receivedBy)
                    .conditionAtReturn(condition)
                    .notes(request.getNotes())
                    .build();
            returnRepository.save(assetReturn);

            // Close assignment
            assignment.setAssignmentStatus(AssignmentStatus.RETURNED);
            assignmentRepository.save(assignment);

            // Update asset status
            asset.setAssetStatus(AssetStatus.RETURNED);
            assetRepository.save(asset);

            // Record custody transfer
            AssetCustodyTransfer transfer = AssetCustodyTransfer.builder()
                    .asset(asset)
                    .organizationId(orgId)
                    .fromCustodianType(CustodianType.EMPLOYEE)
                    .fromCustodianId(employeeId)
                    .toCustodianType(CustodianType.IT_DEPARTMENT)
                    .toCustodianId(null)
                    .transferType(CustodyTransferType.RETURN)
                    .transferDate(LocalDate.now())
                    .approvedBy(approvedBy)
                    .build();
            custodyRepository.save(transfer);

            // Publish outbox event
            outboxService.publishAssetReturned(asset, employeeId, approvedBy);

            returnedAssets.add(assetMapper.toAssignmentDto(assignment));
            log.info("Asset {} returned during bulk offboard for employee {}", asset.getId(), employeeId);
        }

        // Count remaining LOST assignments
        long lostCount = assignmentRepository.countActiveByEmployeeId(employeeId, orgId);

        log.info("Bulk asset offboard completed for employee {}: {} returned, {} lost remaining",
                employeeId, returnedAssets.size(), lostCount);

        return BulkAssetOffboardResponse.builder()
                .employeeId(employeeId)
                .returnedCount(returnedAssets.size())
                .lostCount((int) lostCount)
                .returnedAssets(returnedAssets)
                .build();
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
