package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.event.EmployeeOnboardedEvent;
import com.af.novadesk.api.finance.dto.EntityUserAccessDto;
import com.af.novadesk.api.finance.exception.DuplicateUserAccessException;
import com.af.novadesk.api.finance.service.EntityUserAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Consumes {@link EmployeeOnboardedEvent} published by the Payroll module
 * and grants {@link com.af.novadesk.api.finance.entity.EntityUserAccess}
 * for the employee within their legal entity.
 *
 * <p><b>2026-07-08:</b> This service is intentionally <b>disabled</b>.
 * Employee entity access is now derived from
 * {@link com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment}
 * records rather than creating a separate {@code EntityUserAccess} grant.
 * The {@code EntityAccessGuard} checks both tables.
 *
 * <p>See the professional fix applied to
 * {@link com.af.novadesk.api.finance.security.EntityAccessGuard}
 * which now falls back to checking
 * {@code CmEmployeeEntityAssignmentRepository.existsByEmployeeAuthUserIdAndLegalEntityId()}
 * when no {@code EntityUserAccess} grant is found.</p>
 *
 * <p>Keeping this class compiled (but inactive) so the
 * {@code EmployeeOnboardedEvent} listener re-registration is a one-line
 * uncomment if the behaviour needs to be restored.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityAccessSyncService {

    private final EntityUserAccessService accessService;

    /**
     * DISABLED — see class-level javadoc.
     *
     * Employee entity access is now derived from
     * {@code CmEmployeeEntityAssignment} records instead.
     */
    // @Transactional(propagation = Propagation.REQUIRES_NEW)
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeOnboarded(EmployeeOnboardedEvent event) {
        log.info("Processing EmployeeOnboardedEvent: authUserId={}, entityId={}, role={}, isManager={}",
                event.getAuthUserId(), event.getLegalEntityId(), event.getEntityRole(), event.isManager());

        try {
            EntityUserAccessDto request = new EntityUserAccessDto();
            request.setAuthUserId(event.getAuthUserId());
            request.setEntityRole(event.getEntityRole());

            accessService.grantAccess(event.getLegalEntityId(), request);

            log.info("Entity access granted: authUserId={}, entityId={}, role={}",
                    event.getAuthUserId(), event.getLegalEntityId(), event.getEntityRole());

        } catch (DuplicateUserAccessException e) {
            // Idempotent: already has access — this is not an error.
            log.info("Entity access already exists for authUserId={} on entityId={} — skipping",
                    event.getAuthUserId(), event.getLegalEntityId());
        }
    }
}
