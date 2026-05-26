package com.af.novadesk.api.organization.repository;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.organization.entity.OrganizationBankInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link OrganizationBankInfo} entity.
 */
@Repository
public interface OrganizationBankInfoRepository extends JpaRepository<OrganizationBankInfo, UUID> {

    /**
     * Find all bank info records for a given organization, ordered by creation date.
     */
    List<OrganizationBankInfo> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    /**
     * Find a single bank info record by org ID and record ID.
     */
    Optional<OrganizationBankInfo> findByOrgIdAndId(UUID orgId, UUID id);

    /**
     * Find the primary bank account for an organization.
     */
    Optional<OrganizationBankInfo> findByOrgIdAndPrimaryTrue(UUID orgId);

    /**
     * Check if an account number already exists within an organization.
     */
    boolean existsByOrgIdAndAccountNumber(UUID orgId, String accountNumber);

    /**
     * Check if an account number already exists within an organization, excluding a specific record.
     */
    boolean existsByOrgIdAndAccountNumberAndIdNot(UUID orgId, String accountNumber, UUID excludeId);

    /**
     * Reset the primary flag for all bank accounts of an organization.
     * Used when setting a new primary account.
     */
    @Modifying
    @Query("UPDATE OrganizationBankInfo b SET b.primary = false WHERE b.orgId = :orgId AND b.primary = true")
    void resetPrimaryForOrganization(@Param("orgId") UUID orgId);

    /**
     * Find all active bank info records for an organization.
     */
    List<OrganizationBankInfo> findByOrgIdAndStatusOrderByCreatedAtDesc(UUID orgId, Status status);
}
