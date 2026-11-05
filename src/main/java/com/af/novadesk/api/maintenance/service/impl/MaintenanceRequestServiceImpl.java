package com.af.novadesk.api.maintenance.service.impl;

import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.AssetAssignment;
import com.af.novadesk.api.asset.repository.AssetAssignmentRepository;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.maintenance.constants.MaintenancePriority;
import com.af.novadesk.api.maintenance.constants.MaintenanceStatus;
import com.af.novadesk.api.maintenance.dto.MaintenanceApprovalRequest;
import com.af.novadesk.api.maintenance.dto.MaintenanceRequestCreateRequest;
import com.af.novadesk.api.maintenance.dto.MaintenanceRequestDto;
import com.af.novadesk.api.maintenance.dto.TechnicianAssignmentRequest;
import com.af.novadesk.api.maintenance.entity.MaintenanceRequest;
import com.af.novadesk.api.maintenance.exception.InvalidMaintenanceStatusTransitionException;
import com.af.novadesk.api.maintenance.exception.MaintenanceRequestNotFoundException;
import com.af.novadesk.api.maintenance.mapper.MaintenanceRequestMapper;
import com.af.novadesk.api.maintenance.repository.MaintenanceRequestRepository;
import com.af.novadesk.api.maintenance.service.MaintenanceRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceRequestServiceImpl implements MaintenanceRequestService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final AssetRepository              assetRepository;
    private final AssetAssignmentRepository    assignmentRepository;
    private final CmEmployeeRepository         cmEmployeeRepository;
    private final MaintenanceRequestMapper     maintenanceRequestMapper;
    private final FinanceSecurityContext       securityContext;
    private final EntityAccessGuard            entityAccessGuard;

    @Override
    @Transactional
    public MaintenanceRequestDto create(MaintenanceRequestCreateRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID authUserId = securityContext.getAuthUserId();

        Asset asset = assetRepository.findByIdAndOrganizationId(request.getAssetId(), orgId)
                .orElseThrow(() -> new BadRequestException("Asset not found: " + request.getAssetId()));
        entityAccessGuard.assertCanAccessEntity(asset.getLegalEntity().getId());

        CmEmployee employee = cmEmployeeRepository.findByAuthUserIdAndOrganizationId(authUserId, orgId)
                .orElseThrow(() -> new BadRequestException("No employee record found for the current user"));

        AssetAssignment activeAssignment = assignmentRepository.findActiveByAssetId(asset.getId())
                .orElseThrow(() -> new BadRequestException("Asset is not currently assigned to an employee"));
        if (!activeAssignment.getEmployeeId().equals(employee.getId())) {
            throw new BadRequestException("You can only request maintenance for assets assigned to you");
        }

        MaintenanceRequest maintenanceRequest = MaintenanceRequest.builder()
                .asset(asset)
                .organizationId(orgId)
                .requestedBy(employee.getId())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : MaintenancePriority.MEDIUM)
                .maintenanceStatus(MaintenanceStatus.SUBMITTED)
                .build();

        MaintenanceRequest saved = maintenanceRequestRepository.save(maintenanceRequest);

        log.info("Maintenance request {} submitted by employee {}", saved.getId(), employee);

        return maintenanceRequestMapper.toDto(saved);
    }

    @Override
    public MaintenanceRequestDto fetchById(UUID id) {
        MaintenanceRequest maintenanceRequest = maintenanceRequestRepository.findById(id)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(id));
        return maintenanceRequestMapper.toDto(maintenanceRequest);
    }

    @Override
    public List<MaintenanceRequestDto> list(UUID assetId, MaintenanceStatus status) {
        UUID orgId = securityContext.getOrganizationId();

        List<MaintenanceRequest> results;
        if (assetId != null) {
            results = maintenanceRequestRepository.findByOrganizationIdAndAssetIdOrderByCreatedAtDesc(orgId, assetId);
        } else if (status != null) {
            results = maintenanceRequestRepository.findByOrganizationIdAndMaintenanceStatusOrderByCreatedAtDesc(orgId, status);
        } else {
            results = maintenanceRequestRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        }

        return results.stream()
                .map(this::enrichWithRequesterName)
                .toList();
    }

    @Override
    @Transactional
    public MaintenanceRequestDto approve(UUID id, MaintenanceApprovalRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        MaintenanceRequest maintenanceRequest = requireInOrg(id, orgId);

        if (maintenanceRequest.getMaintenanceStatus() != MaintenanceStatus.SUBMITTED
                && maintenanceRequest.getMaintenanceStatus() != MaintenanceStatus.IN_REVIEW) {
            throw new InvalidMaintenanceStatusTransitionException(id, maintenanceRequest.getMaintenanceStatus(), MaintenanceStatus.APPROVED);
        }

        maintenanceRequest.setMaintenanceStatus(MaintenanceStatus.APPROVED);
        maintenanceRequest.setCostEstimate(request.getCostEstimate());
        if (request.getCostEstimate().compareTo(new BigDecimal("5000")) > 0) {
            maintenanceRequest.setPriority(MaintenancePriority.HIGH);
        }
        maintenanceRequest.setReviewNotes(request.getNotes());

        MaintenanceRequest saved = maintenanceRequestRepository.save(maintenanceRequest);
        log.info("Maintenance request {} approved — cost estimate {}", id, request.getCostEstimate());
        return maintenanceRequestMapper.toDto(saved);
    }

    @Override
    @Transactional
    public MaintenanceRequestDto reject(UUID id, String reason) {
        UUID orgId = securityContext.getOrganizationId();
        MaintenanceRequest maintenanceRequest = requireInOrg(id, orgId);

        if (maintenanceRequest.getMaintenanceStatus() != MaintenanceStatus.SUBMITTED
                && maintenanceRequest.getMaintenanceStatus() != MaintenanceStatus.IN_REVIEW) {
            throw new InvalidMaintenanceStatusTransitionException(id, maintenanceRequest.getMaintenanceStatus(), MaintenanceStatus.REJECTED);
        }

        maintenanceRequest.setMaintenanceStatus(MaintenanceStatus.REJECTED);
        maintenanceRequest.setReviewNotes(reason);

        MaintenanceRequest saved = maintenanceRequestRepository.save(maintenanceRequest);
        log.info("Maintenance request {} rejected — reason: {}", id, reason);
        return maintenanceRequestMapper.toDto(saved);
    }

    @Override
    @Transactional
    public MaintenanceRequestDto assignTechnician(UUID id, TechnicianAssignmentRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        MaintenanceRequest maintenanceRequest = requireInOrg(id, orgId);

        maintenanceRequest.setAssignedTechnicianId(request.getTechnicianId());
        maintenanceRequest.setMaintenanceStatus(MaintenanceStatus.IN_PROGRESS);

        MaintenanceRequest saved = maintenanceRequestRepository.save(maintenanceRequest);
        log.info("Technician {} assigned to maintenance request {}", request.getTechnicianId(), id);
        return maintenanceRequestMapper.toDto(saved);
    }

    @Override
    @Transactional
    public MaintenanceRequestDto complete(UUID id, String notes) {
        UUID orgId = securityContext.getOrganizationId();
        MaintenanceRequest maintenanceRequest = requireInOrg(id, orgId);

        if (maintenanceRequest.getMaintenanceStatus() != MaintenanceStatus.IN_PROGRESS) {
            throw new InvalidMaintenanceStatusTransitionException(id, maintenanceRequest.getMaintenanceStatus(), MaintenanceStatus.COMPLETED);
        }

        maintenanceRequest.setMaintenanceStatus(MaintenanceStatus.COMPLETED);
        maintenanceRequest.setResolvedAt(LocalDateTime.now());
        if (notes != null) {
            maintenanceRequest.setReviewNotes(notes);
        }

        MaintenanceRequest saved = maintenanceRequestRepository.save(maintenanceRequest);
        log.info("Maintenance request {} marked complete", id);
        return maintenanceRequestMapper.toDto(saved);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MaintenanceRequest requireInOrg(UUID id, UUID orgId) {
        return maintenanceRequestRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(id));
    }

    private MaintenanceRequestDto enrichWithRequesterName(MaintenanceRequest maintenanceRequest) {
        MaintenanceRequestDto dto = maintenanceRequestMapper.toDto(maintenanceRequest);
        CmEmployee requester = cmEmployeeRepository.findById(maintenanceRequest.getRequestedBy()).orElse(null);
        dto.setRequestedByName(requester != null ? requester.getDisplayName() : null);
        return dto;
    }
}
