package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.AssetAssignment;
import com.af.novadesk.api.common.specification.SpecUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID>,
        JpaSpecificationExecutor<Asset> {

    static Specification<Asset> filterSpec(
            UUID orgId, UUID legalEntityId, AssetStatus status, AssetCategory category,
            String q, String manufacturer, String location,
            LocalDate purchaseDateFrom, LocalDate purchaseDateTo,
            LocalDate warrantyExpiryFrom, LocalDate warrantyExpiryTo) {
        return filterSpec(orgId, legalEntityId, status, category, q, manufacturer, location,
                purchaseDateFrom, purchaseDateTo, warrantyExpiryFrom, warrantyExpiryTo, null);
    }

    static Specification<Asset> filterSpec(
            UUID orgId, UUID legalEntityId, AssetStatus status, AssetCategory category,
            String q, String manufacturer, String location,
            LocalDate purchaseDateFrom, LocalDate purchaseDateTo,
            LocalDate warrantyExpiryFrom, LocalDate warrantyExpiryTo,
            List<UUID> assignedToEmployeeIdIn) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), orgId));
            SpecUtils.addIfPresent(p, legalEntityId,    () -> cb.equal(root.get("legalEntity").get("id"), legalEntityId));
            SpecUtils.addIfPresent(p, status,           () -> cb.equal(root.get("assetStatus"), status));
            SpecUtils.addIfPresent(p, category,         () -> cb.equal(root.get("category"), category));
            SpecUtils.addLikeIfPresent(p, q, () -> cb.or(
                    SpecUtils.likeLower(cb, root, "assetType", q),
                    SpecUtils.likeLower(cb, root, "serialNumber", q)
            ));
            SpecUtils.addLikeIfPresent(p, manufacturer, () -> SpecUtils.likeLower(cb, root, "manufacturer", manufacturer));
            SpecUtils.addLikeIfPresent(p, location,     () -> SpecUtils.likeLower(cb, root, "currentLocation", location));
            SpecUtils.addIfPresent(p, purchaseDateFrom, () -> cb.greaterThanOrEqualTo(root.get("purchaseDate"), purchaseDateFrom));
            SpecUtils.addIfPresent(p, purchaseDateTo,   () -> cb.lessThanOrEqualTo(root.get("purchaseDate"), purchaseDateTo));
            SpecUtils.addIfPresent(p, warrantyExpiryFrom, () -> cb.greaterThanOrEqualTo(root.get("warrantyExpiryDate"), warrantyExpiryFrom));
            SpecUtils.addIfPresent(p, warrantyExpiryTo,   () -> cb.lessThanOrEqualTo(root.get("warrantyExpiryDate"), warrantyExpiryTo));
            if (assignedToEmployeeIdIn != null && !assignedToEmployeeIdIn.isEmpty()) {
                var sub = query.subquery(UUID.class);
                var asgn = sub.from(AssetAssignment.class);
                sub.select(asgn.get("asset").get("id"))
                   .where(cb.and(
                       asgn.get("employeeId").in(assignedToEmployeeIdIn),
                       cb.equal(asgn.get("assignmentStatus"), AssignmentStatus.ACTIVE)
                   ));
                p.add(root.get("id").in(sub));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    Optional<Asset> findByIdAndOrganizationId(UUID id, UUID orgId);

    boolean existsBySerialNumberAndOrganizationId(String serialNumber, UUID orgId);

    @Query("SELECT a FROM Asset a WHERE a.organizationId = :orgId")
    Page<Asset> findAllByOrganizationId(@Param("orgId") UUID orgId, Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.organizationId = :orgId AND a.assetStatus = :status")
    Page<Asset> findAllByOrganizationIdAndStatus(
            @Param("orgId") UUID orgId,
            @Param("status") AssetStatus status,
            Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.organizationId = :orgId AND a.category = :category")
    Page<Asset> findAllByOrganizationIdAndCategory(
            @Param("orgId") UUID orgId,
            @Param("category") AssetCategory category,
            Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.legalEntity.id = :entityId AND a.organizationId = :orgId")
    Page<Asset> findAllByLegalEntityIdAndOrganizationId(
            @Param("entityId") UUID entityId,
            @Param("orgId") UUID orgId,
            Pageable pageable);
}
