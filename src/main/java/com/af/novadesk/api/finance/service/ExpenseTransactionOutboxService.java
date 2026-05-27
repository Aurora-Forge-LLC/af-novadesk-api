package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.finance.constants.ExpenseEventType;
import com.af.novadesk.api.finance.entity.ExpenseTransaction;
import com.af.novadesk.api.finance.entity.ExpenseTransactionOutboxEvent;
import com.af.novadesk.api.finance.repository.ExpenseTransactionOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional Outbox publisher for the {@link ExpenseTransaction} aggregate (LLR-FIN-03).
 *
 * <p>Persists {@link ExpenseTransactionOutboxEvent} rows inside the caller's active
 * transaction, guaranteeing at-least-once delivery without dual-write risk.
 * The polling publisher reads {@code PENDING} rows after commit and delivers them
 * to the message broker.</p>
 *
 * <h2>Idempotency key conventions</h2>
 * <ul>
 *   <li>EXPENSE_CREATED  — {@code "EXPENSE_CREATED:<transactionId>:<journalId>"}</li>
 *   <li>EXPENSE_VOIDED   — {@code "EXPENSE_VOIDED:<transactionId>:<randomUUID>"}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseTransactionOutboxService {

    private final ExpenseTransactionOutboxEventRepository outboxRepository;
    private final ObjectMapper                            objectMapper;

    // =========================================================================
    // Publishers
    // =========================================================================

    /**
     * Persists an {@code EXPENSE_CREATED} outbox event inside the caller's transaction.
     *
     * @param transaction  the saved expense transaction
     * @param journalId    UUID shared by both ledger entry lines for this expense
     * @param orgId        organization context (denormalised for filtering without payload parsing)
     * @param authUserId   JWT {@code sub} of the user who submitted the expense
     */
    @Transactional
    public void publishExpenseCreated(
            ExpenseTransaction transaction,
            UUID journalId,
            UUID orgId,
            UUID authUserId
    ) {
        String idempotencyKey = ExpenseEventType.EXPENSE_CREATED
                + ":" + transaction.getId()
                + ":" + journalId;

        String payload = serialize(buildCreatedPayload(transaction, journalId, orgId, authUserId));

        ExpenseTransactionOutboxEvent event = ExpenseTransactionOutboxEvent.builder()
                .expenseTransaction(transaction)
                .eventType(ExpenseEventType.EXPENSE_CREATED)
                .payload(payload)
                .organizationId(orgId)
                .triggeredByAuthUserId(authUserId)
                .idempotencyKey(idempotencyKey)
                .outboxEventStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();

        outboxRepository.save(event);
        log.debug("Outbox event persisted: type=EXPENSE_CREATED, transaction={}, journal={}",
                transaction.getId(), journalId);
    }

    /**
     * Persists an {@code EXPENSE_VOIDED} outbox event inside the caller's transaction.
     *
     * @param transaction  the voided expense transaction
     * @param voidReason   mandatory reason provided by the user
     * @param orgId        organization context
     * @param authUserId   JWT {@code sub} of the user who voided the expense
     */
    @Transactional
    public void publishExpenseVoided(
            ExpenseTransaction transaction,
            String voidReason,
            UUID orgId,
            UUID authUserId
    ) {
        // Random UUID as trace suffix — void is a one-time action, no journal ID available
        String idempotencyKey = ExpenseEventType.EXPENSE_VOIDED
                + ":" + transaction.getId()
                + ":" + UUID.randomUUID();

        String payload = serialize(buildVoidedPayload(transaction, voidReason, orgId, authUserId));

        ExpenseTransactionOutboxEvent event = ExpenseTransactionOutboxEvent.builder()
                .expenseTransaction(transaction)
                .eventType(ExpenseEventType.EXPENSE_VOIDED)
                .payload(payload)
                .organizationId(orgId)
                .triggeredByAuthUserId(authUserId)
                .idempotencyKey(idempotencyKey)
                .outboxEventStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();

        outboxRepository.save(event);
        log.debug("Outbox event persisted: type=EXPENSE_VOIDED, transaction={}",
                transaction.getId());
    }

    // =========================================================================
    // Payload builders
    // =========================================================================

    private ExpenseCreatedPayload buildCreatedPayload(
            ExpenseTransaction t, UUID journalId, UUID orgId, UUID authUserId) {
        return new ExpenseCreatedPayload(
                t.getId().toString(),
                journalId.toString(),
                t.getLegalEntity().getId().toString(),
                t.getVendor().getId().toString(),
                t.getVendor().getVendorName(),
                t.getAmount().toPlainString(),
                t.getCurrencyCode(),
                t.getExpenseDate().toString(),
                authUserId != null ? authUserId.toString() : null,
                orgId.toString()
        );
    }

    private ExpenseVoidedPayload buildVoidedPayload(
            ExpenseTransaction t, String voidReason, UUID orgId, UUID authUserId) {
        return new ExpenseVoidedPayload(
                t.getId().toString(),
                t.getLegalEntity().getId().toString(),
                voidReason,
                authUserId != null ? authUserId.toString() : null,
                LocalDateTime.now().toString(),
                orgId.toString()
        );
    }

    // =========================================================================
    // Serialisation
    // =========================================================================

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Failed to serialize expense outbox payload: " + ex.getMessage(), ex);
        }
    }

    // =========================================================================
    // Payload records (internal — not part of the public API)
    // =========================================================================

    private record ExpenseCreatedPayload(
            String transactionId,
            String journalId,
            String legalEntityId,
            String vendorId,
            String vendorName,
            String amount,
            String currencyCode,
            String expenseDate,
            String createdByAuthUserId,
            String organizationId
    ) {}

    private record ExpenseVoidedPayload(
            String transactionId,
            String legalEntityId,
            String voidReason,
            String voidedByAuthUserId,
            String voidedAt,
            String organizationId
    ) {}
}
