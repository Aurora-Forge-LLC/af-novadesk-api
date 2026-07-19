package com.af.novadesk.api.asset.reservation.scheduler;

import com.af.novadesk.api.asset.reservation.constants.ReservationConstants;
import com.af.novadesk.api.asset.reservation.constants.ReservationStatus;
import com.af.novadesk.api.asset.reservation.entity.AssetReservation;
import com.af.novadesk.api.asset.reservation.repository.AssetReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Auto-expires reservations whose window has passed without an approval decision
 * (LLR-AST-05.8).
 *
 * <p>Runs on {@code app.asset.reservation-expiry-cron} — default every 15 minutes.
 * Each reservation is expired in its own transaction so a single failure does not
 * abandon the rest of the sweep.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final AssetReservationRepository reservationRepository;

    @Scheduled(cron = "${app.asset.reservation-expiry-cron:" + ReservationConstants.DEFAULT_EXPIRY_CRON + "}")
    public void sweepExpiredReservations() {
        List<AssetReservation> due = reservationRepository.findExpirable(LocalDateTime.now());
        if (due.isEmpty()) {
            return;
        }
        log.info("Reservation expiry sweep — {} reservation(s) to expire", due.size());
        int expired = 0;
        for (AssetReservation reservation : due) {
            try {
                this.expireReservation(reservation.getId());
                expired++;
            } catch (RuntimeException e) {
                log.error("Failed to expire reservation {}: {}", reservation.getId(), e.getMessage(), e);
            }
        }
        log.info("Reservation expiry sweep completed — {} expired", expired);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireReservation(UUID reservationId) {
        reservationRepository.findById(reservationId).ifPresent(reservation -> {
            if (reservation.getReservationStatus() == ReservationStatus.PENDING) {
                reservation.setReservationStatus(ReservationStatus.EXPIRED);
                reservationRepository.save(reservation);
                log.info("Reservation {} expired", reservationId);
            }
        });
    }
}
