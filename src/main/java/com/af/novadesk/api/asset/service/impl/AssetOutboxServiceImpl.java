package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.constants.AssetEventType;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.AssetAssignment;
import com.af.novadesk.api.asset.entity.AssetOutboxEvent;
import com.af.novadesk.api.asset.entity.AssetWriteOff;
import com.af.novadesk.api.asset.entity.DepreciationSchedule;
import com.af.novadesk.api.asset.repository.AssetOutboxEventRepository;
import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes Asset module domain events to the transactional outbox
 * ({@code af_novadesk_outbox.ast_outbox_events}).
 *
 * <p>Every publish method must be called <strong>inside the caller's active
 * transaction</strong> so that the outbox row and the aggregate change are
 * committed atomically. If the business transaction rolls back, the event row
 * is rolled back too — no phantom events.</p>
 *
 * <p>The {@link com.af.novadesk.api.asset.scheduler.AssetOutboxPublisher}
 * polls for {@code PENDING} rows and delivers them to downstream consumers
 * after commit (at-least-once delivery).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetOutboxServiceImpl {

    private static final String AGGREGATE_ASSET       = "ASSET";
    private static final String AGGREGATE_ASSIGNMENT  = "ASSET_ASSIGNMENT";
    private static final String AGGREGATE_WRITE_OFF   = "ASSET_WRITE_OFF";

    private final AssetOutboxEventRepository outboxRepository;
    private final ObjectMapper               objectMapper;

    // =========================================================================
    // LLR-AST-01: Asset purchased (registration)
    // =========================================================================

    /**
     * Publishes {@code ASSET_PURCHASED} — triggers Fixed-Asset journal entry
     * (Debit Fixed Assets / Credit Cash or AP) in the finance GL.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAssetPurchased(Asset asset) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId",        str(asset.getId()));
        payload.put("category",       asset.getCategory().name());
        payload.put("assetType",      asset.getAssetType());
        payload.put("serialNumber",   asset.getSerialNumber());
        payload.put("purchaseCost",   asset.getPurchaseCost());
        payload.put("currencyCode",   asset.getCurrencyCode());
        payload.put("purchaseDate",   asset.getPurchaseDate().toString());
        payload.put("legalEntityId",  str(asset.getLegalEntity().getId()));
        payload.put("organizationId", str(asset.getOrganizationId()));

        persist(asset.getId(), AGGREGATE_ASSET,
                AssetEventType.ASSET_PURCHASED, payload,
                asset.getOrganizationId(), null);
    }

    // =========================================================================
    // LLR-AST-04: Depreciation posted
    // =========================================================================

    /**
     * Publishes {@code ASSET_DEPRECIATION_POSTED} — triggers Depreciation
     * Expense journal entry (Debit Depreciation Expense / Credit Accumulated
     * Depreciation) in the finance GL.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishDepreciationPosted(Asset asset, DepreciationSchedule schedule) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId",                 str(asset.getId()));
        payload.put("category",                asset.getCategory().name());
        payload.put("fiscalYear",              schedule.getFiscalYear());
        payload.put("annualDepreciation",      schedule.getAnnualDepreciation());
        payload.put("accumulatedDepreciation", schedule.getAccumulatedDepreciation());
        payload.put("netBookValue",            schedule.getNetBookValue());
        payload.put("currencyCode",            asset.getCurrencyCode());
        payload.put("legalEntityId",           str(asset.getLegalEntity().getId()));
        payload.put("organizationId",          str(asset.getOrganizationId()));

        String idempotencyKey = AssetEventType.ASSET_DEPRECIATION_POSTED
                + ":" + asset.getId()
                + ":" + schedule.getFiscalYear();

        persist(asset.getId(), AGGREGATE_ASSET,
                AssetEventType.ASSET_DEPRECIATION_POSTED, payload,
                asset.getOrganizationId(), null, idempotencyKey);
    }

    // =========================================================================
    // LLR-AST-02: Asset assigned
    // =========================================================================

    /**
     * Publishes {@code ASSET_ASSIGNED} — used for audit trail and employee
     * acknowledgment notification.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAssetAssigned(AssetAssignment assignment, UUID approvedBy) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("assignmentId",    str(assignment.getId()));
        payload.put("assetId",         str(assignment.getAsset().getId()));
        payload.put("employeeId",      str(assignment.getEmployeeId()));
        payload.put("assignmentDate",  assignment.getAssignmentDate().toString());
        payload.put("purpose",         assignment.getPurpose().name());
        payload.put("requiresAck",     assignment.isRequiresAcknowledgment());
        payload.put("organizationId",  str(assignment.getOrganizationId()));

        persist(assignment.getId(), AGGREGATE_ASSIGNMENT,
                AssetEventType.ASSET_ASSIGNED, payload,
                assignment.getOrganizationId(), approvedBy);
    }

    // =========================================================================
    // LLR-AST-03: Asset returned
    // =========================================================================

    /**
     * Publishes {@code ASSET_RETURNED} — used for audit trail.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAssetReturned(Asset asset, UUID returnedByEmployeeId, UUID approvedBy) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId",            str(asset.getId()));
        payload.put("returnedByEmployee", str(returnedByEmployeeId));
        payload.put("returnDate",         java.time.LocalDate.now().toString());
        payload.put("assetStatus",        asset.getAssetStatus().name());
        payload.put("organizationId",     str(asset.getOrganizationId()));

        persist(asset.getId(), AGGREGATE_ASSET,
                AssetEventType.ASSET_RETURNED, payload,
                asset.getOrganizationId(), approvedBy);
    }

    // =========================================================================
    // LLR-AST-03.5: Write-off lifecycle
    // =========================================================================

    /**
     * Publishes {@code ASSET_WRITTEN_OFF} — IT admin has requested a write-off.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishWriteOffRequested(AssetWriteOff writeOff) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("writeOffId",    str(writeOff.getId()));
        payload.put("assetId",       str(writeOff.getAsset().getId()));
        payload.put("reason",        writeOff.getReason());
        payload.put("requestedBy",   str(writeOff.getRequestedBy()));
        payload.put("organizationId", str(writeOff.getOrganizationId()));

        persist(writeOff.getId(), AGGREGATE_WRITE_OFF,
                AssetEventType.ASSET_WRITTEN_OFF, payload,
                writeOff.getOrganizationId(), writeOff.getRequestedBy());
    }

    /**
     * Publishes {@code ASSET_WRITE_OFF_APPROVED} — triggers disposal journal entry.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishWriteOffApproved(AssetWriteOff writeOff) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("writeOffId",       str(writeOff.getId()));
        payload.put("assetId",          str(writeOff.getAsset().getId()));
        payload.put("action",           writeOff.getAction().name());
        payload.put("depreciatedValue", writeOff.getDepreciatedValue());
        payload.put("approvedBy",       str(writeOff.getApprovedBy()));
        payload.put("organizationId",   str(writeOff.getOrganizationId()));

        persist(writeOff.getId(), AGGREGATE_WRITE_OFF,
                AssetEventType.ASSET_WRITE_OFF_APPROVED, payload,
                writeOff.getOrganizationId(), writeOff.getApprovedBy());
    }

    /**
     * Publishes {@code ASSET_WRITE_OFF_REJECTED} — asset reverted to ASSIGNED.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishWriteOffRejected(AssetWriteOff writeOff) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("writeOffId",    str(writeOff.getId()));
        payload.put("assetId",       str(writeOff.getAsset().getId()));
        payload.put("rejectedBy",    str(writeOff.getApprovedBy()));
        payload.put("auditNotes",    writeOff.getAuditNotes());
        payload.put("organizationId", str(writeOff.getOrganizationId()));

        persist(writeOff.getId(), AGGREGATE_WRITE_OFF,
                AssetEventType.ASSET_WRITE_OFF_REJECTED, payload,
                writeOff.getOrganizationId(), writeOff.getApprovedBy());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Persist with auto-generated idempotency key. */
    private void persist(UUID aggregateId, String aggregateType,
                         AssetEventType eventType, Map<String, Object> payloadMap,
                         UUID organizationId, UUID triggeredBy) {
        String key = eventType.name() + ":" + aggregateId + ":" + UUID.randomUUID();
        persist(aggregateId, aggregateType, eventType, payloadMap, organizationId, triggeredBy, key);
    }

    /** Persist with a caller-supplied idempotency key (for deterministic events). */
    private void persist(UUID aggregateId, String aggregateType,
                         AssetEventType eventType, Map<String, Object> payloadMap,
                         UUID organizationId, UUID triggeredBy, String idempotencyKey) {
        try {
            String json = objectMapper.writeValueAsString(payloadMap);

            AssetOutboxEvent event = AssetOutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .eventType(eventType)
                    .payload(json)
                    .organizationId(organizationId)
                    .triggeredByAuthUserId(triggeredBy)
                    .idempotencyKey(idempotencyKey)
                    .outboxEventStatus(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .build();

            outboxRepository.save(event);
            log.debug("Outbox event persisted: type={} aggregate={}:{}", eventType, aggregateType, aggregateId);

        } catch (JsonProcessingException e) {
            // Serialisation failure is a programming error — fail the business transaction
            throw new com.af.novadesk.api.common.exception.OutboxPublishException(
                    eventType.name(), aggregateId, e);
        }
    }

    private static String str(UUID id) {
        return id != null ? id.toString() : null;
    }
}
