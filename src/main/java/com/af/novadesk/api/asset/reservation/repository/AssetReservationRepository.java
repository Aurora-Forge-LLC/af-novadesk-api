package com.af.novadesk.api.asset.reservation.repository;

import com.af.novadesk.api.asset.reservation.entity.AssetReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetReservationRepository extends JpaRepository<AssetReservation, UUID> {

    Optional<AssetReservation> findByIdAndOrganizationId(UUID id, UUID orgId);

    /** The requester's own reservations, newest window first — drives the "My reservations" view. */
    @Query("""
           SELECT r FROM AssetReservation r
           WHERE r.requestedBy = :requestedBy
             AND r.organizationId = :orgId
           ORDER BY r.startsAt DESC
           """)
    List<AssetReservation> findByRequestedByAndOrganizationId(
            @Param("requestedBy") UUID requestedBy,
            @Param("orgId") UUID orgId);

    /** Org-wide reservation feed, newest window first — ops console. */
    @Query("""
           SELECT r FROM AssetReservation r
           WHERE r.organizationId = :orgId
           ORDER BY r.startsAt DESC
           """)
    List<AssetReservation> findByOrganizationId(@Param("orgId") UUID orgId);

    /** Live reservations for an asset that clash with the requested window. */
    @Query("""
           SELECT r FROM AssetReservation r
           WHERE r.asset.id = :assetId
             AND r.organizationId = :orgId
             AND r.reservationStatus IN ('PENDING', 'APPROVED')
             AND r.startsAt >= :from AND r.startsAt < :to
           """)
    List<AssetReservation> findOverlapping(
            @Param("assetId") UUID assetId,
            @Param("orgId") UUID orgId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Live reservations for an asset intersecting a calendar window — availability view. */
    @Query("""
           SELECT r FROM AssetReservation r
           WHERE r.asset.id = :assetId
             AND r.organizationId = :orgId
             AND r.reservationStatus IN ('PENDING', 'APPROVED')
             AND r.startsAt < :to AND r.endsAt > :from
           ORDER BY r.startsAt ASC
           """)
    List<AssetReservation> findActiveForAssetInWindow(
            @Param("assetId") UUID assetId,
            @Param("orgId") UUID orgId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Pending reservations whose window has elapsed — swept to EXPIRED. */
    @Query("""
           SELECT r FROM AssetReservation r
           WHERE r.reservationStatus = 'PENDING'
             AND r.endsAt < :cutoff
           """)
    List<AssetReservation> findExpirable(@Param("cutoff") LocalDateTime cutoff);
}
