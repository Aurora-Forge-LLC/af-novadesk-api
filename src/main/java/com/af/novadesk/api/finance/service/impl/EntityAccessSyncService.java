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
 * <p>This is the cross-module bridge between Payroll and Finance.
 * The event class lives in {@code com.af.novadesk.api.common.event} so
 * neither module needs to import the other's entity classes.</p>
 *
 * <p>Processing is idempotent: if the {@code EntityUserAccess} record
 * already exists (duplicate access), the handler treats it as success.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityAccessSyncService {

    private final EntityUserAccessService accessService;

    /**
     * Handles {@link EmployeeOnboardedEvent} AFTER the Payroll module's
     * {@code @Transactional} commits, so the employee record is fully
     * persisted before we grant entity access.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
