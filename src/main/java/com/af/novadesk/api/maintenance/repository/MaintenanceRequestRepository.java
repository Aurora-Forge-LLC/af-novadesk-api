package com.af.novadesk.api.maintenance.repository;

import com.af.novadesk.api.maintenance.constants.MaintenanceStatus;
import com.af.novadesk.api.maintenance.entity.MaintenanceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, UUID> {

    Optional<MaintenanceRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** All requests for an org, most recent first. No paging — see MaintenanceRequestServiceImpl#list. */
    List<MaintenanceRequest> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<MaintenanceRequest> findByOrganizationIdAndMaintenanceStatusOrderByCreatedAtDesc(
            UUID organizationId, MaintenanceStatus maintenanceStatus);

    List<MaintenanceRequest> findByOrganizationIdAndAssetIdOrderByCreatedAtDesc(
            UUID organizationId, UUID assetId);
}
