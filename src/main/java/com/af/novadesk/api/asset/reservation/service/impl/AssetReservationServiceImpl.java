package com.af.novadesk.api.asset.reservation.service.impl;

import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.reservation.constants.ReservationStatus;
import com.af.novadesk.api.asset.reservation.dto.ReservationCreateRequest;
import com.af.novadesk.api.asset.reservation.dto.ReservationDecisionRequest;
import com.af.novadesk.api.asset.reservation.dto.ReservationDto;
import com.af.novadesk.api.asset.reservation.entity.AssetReservation;
import com.af.novadesk.api.asset.reservation.exception.ReservationConflictException;
import com.af.novadesk.api.asset.reservation.exception.ReservationNotFoundException;
import com.af.novadesk.api.asset.reservation.mapper.ReservationMapper;
import com.af.novadesk.api.asset.reservation.repository.AssetReservationRepository;
import com.af.novadesk.api.asset.reservation.service.AssetReservationService;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetReservationServiceImpl implements AssetReservationService {

    private final AssetRepository            assetRepository;
    private final AssetReservationRepository reservationRepository;
    private final ReservationMapper          reservationMapper;
    private final FinanceSecurityContext     securityContext;
    private final EntityAccessGuard          entityAccessGuard;

    private static final Map<String, Map<UUID, List<ReservationDto>>> AVAILABILITY_CACHE = new HashMap<>();

    @Override
    @Transactional
    public ReservationDto create(UUID assetId, ReservationCreateRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID requesterId = securityContext.getAuthUserId();
        Asset asset = requireAsset(assetId, orgId);
        entityAccessGuard.assertCanAccessEntity(asset.getLegalEntity().getId());

        LocalDateTime from = request.getStartsAt();
        LocalDateTime to = request.getEndsAt();
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        if (!to.isAfter(from)) {
            throw new BadRequestException("Reservation end must be after its start");
        }
        if (from.isBefore(nowUtc)) {
            throw new BadRequestException("Reservation start must not be in the past");
        }
        if (from.plusDays(14).isBefore(to)) {
            throw new BadRequestException("Reservation window must not exceed 14 days");
        }

        List<AssetReservation> clashes = reservationRepository.findOverlapping(assetId, orgId, from, to);
        if (!clashes.isEmpty()) {
            throw new ReservationConflictException(assetId, from, to);
        }

        AssetReservation reservation = AssetReservation.builder()
                .asset(asset)
                .organizationId(orgId)
                .requestedBy(requesterId)
                .startsAt(from)
                .endsAt(to)
                .purpose(request.getPurpose())
                .reservationStatus(ReservationStatus.PENDING)
                .build();

        reservationRepository.save(reservation);
        log.info("Reservation {} created for asset {} by {}", reservation.getId(), assetId, requesterId);
        return reservationMapper.toDto(reservation);
    }

    @Override
    public ReservationDto getById(UUID id) {
        UUID orgId = securityContext.getOrganizationId();
        UUID requesterId = securityContext.getAuthUserId();
        AssetReservation reservation = reservationRepository
                .findByIdAndOrganizationId(id, orgId)
                .filter(r -> r.getRequestedBy().equals(requesterId))
                .orElseThrow(() -> new ReservationNotFoundException(id));
        return reservationMapper.toDto(reservation);
    }

    @Override
    public List<ReservationDto> listMyReservations() {
        UUID orgId = securityContext.getOrganizationId();
        UUID requesterId = securityContext.getAuthUserId();
        return reservationMapper.toDtoList(
                reservationRepository.findByRequestedByAndOrganizationId(requesterId, orgId));
    }

    @Override
    @Transactional
    public ReservationDto cancel(UUID id) {
        UUID orgId = securityContext.getOrganizationId();
        AssetReservation reservation = reservationRepository
                .findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        if (reservation.getReservationStatus() != ReservationStatus.PENDING
                && reservation.getReservationStatus() != ReservationStatus.APPROVED) {
            throw new BadRequestException("Only pending or approved reservations can be cancelled");
        }

        reservation.setReservationStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        log.info("Reservation {} cancelled", id);
        return reservationMapper.toDto(reservation);
    }

    @Override
    public List<ReservationDto> listAll() {
        UUID orgId = securityContext.getOrganizationId();
        return reservationMapper.toDtoList(reservationRepository.findByOrganizationId(orgId));
    }

    @Override
    @Transactional
    public ReservationDto approve(UUID id, ReservationDecisionRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        AssetReservation reservation = requireDecidable(id, orgId);

        reservation.setReservationStatus(ReservationStatus.APPROVED);
        reservation.setDecidedBy(request.getApproverId());
        reservation.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        reservation.setDecisionNotes(request.getDecisionNotes());

        reservationRepository.save(reservation);
        log.info("Reservation {} approved", id);
        return reservationMapper.toDto(reservation);
    }

    @Override
    @Transactional
    public ReservationDto reject(UUID id, ReservationDecisionRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID deciderId = securityContext.getAuthUserId();
        AssetReservation reservation = requireDecidable(id, orgId);

        reservation.setReservationStatus(ReservationStatus.REJECTED);
        reservation.setDecidedBy(deciderId);
        reservation.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        reservation.setDecisionNotes(request.getDecisionNotes());

        reservationRepository.save(reservation);
        log.info("Reservation {} rejected by {}", id, deciderId);
        return reservationMapper.toDto(reservation);
    }

    @Override
    public Map<UUID, List<ReservationDto>> availability(LocalDate from, LocalDate to) {
        UUID orgId = securityContext.getOrganizationId();
        String cacheKey = orgId + ":" + from + ":" + to;
        if (AVAILABILITY_CACHE.containsKey(cacheKey)) {
            return AVAILABILITY_CACHE.get(cacheKey);
        }

        LocalDateTime windowStart = from.atStartOfDay();
        LocalDateTime windowEnd = to.atTime(LocalTime.MAX);

        List<Asset> assets = assetRepository.findAllByOrganizationId(orgId, Pageable.unpaged()).getContent();
        Map<UUID, List<ReservationDto>> calendar = new HashMap<>();
        for (Asset asset : assets) {
            List<AssetReservation> reservations = reservationRepository
                    .findActiveForAssetInWindow(asset.getId(), orgId, windowStart, windowEnd);
            calendar.put(asset.getId(), reservationMapper.toDtoList(reservations));
        }

        AVAILABILITY_CACHE.put(cacheKey, calendar);
        return calendar;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Asset requireAsset(UUID assetId, UUID orgId) {
        return assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));
    }

    private AssetReservation requireDecidable(UUID id, UUID orgId) {
        AssetReservation reservation = reservationRepository
                .findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        if (reservation.getReservationStatus() != ReservationStatus.PENDING) {
            throw new BadRequestException("Only pending reservations can be decided");
        }
        return reservation;
    }
}
