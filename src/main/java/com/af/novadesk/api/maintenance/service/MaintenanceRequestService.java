package com.af.novadesk.api.maintenance.service;

import com.af.novadesk.api.maintenance.constants.MaintenanceStatus;
import com.af.novadesk.api.maintenance.dto.MaintenanceApprovalRequest;
import com.af.novadesk.api.maintenance.dto.MaintenanceRequestCreateRequest;
import com.af.novadesk.api.maintenance.dto.MaintenanceRequestDto;
import com.af.novadesk.api.maintenance.dto.TechnicianAssignmentRequest;

import java.util.List;
import java.util.UUID;

public interface MaintenanceRequestService {

    /** Employee submits a new maintenance request against an asset assigned to them. */
    MaintenanceRequestDto create(MaintenanceRequestCreateRequest request);

    /** Fetch a single maintenance request by ID. */
    MaintenanceRequestDto fetchById(UUID id);

    /** List maintenance requests, optionally filtered by asset and/or status. */
    List<MaintenanceRequestDto> list(UUID assetId, MaintenanceStatus status);

    /** Ops approves a SUBMITTED/IN_REVIEW request and records the estimated repair cost. */
    MaintenanceRequestDto approve(UUID id, MaintenanceApprovalRequest request);

    /** Ops rejects a SUBMITTED/IN_REVIEW request. */
    MaintenanceRequestDto reject(UUID id, String reason);

    /** Ops assigns a technician to an APPROVED request, moving it to IN_PROGRESS. */
    MaintenanceRequestDto assignTechnician(UUID id, TechnicianAssignmentRequest request);

    /** Ops marks an IN_PROGRESS request as COMPLETED. */
    MaintenanceRequestDto complete(UUID id, String notes);
}
