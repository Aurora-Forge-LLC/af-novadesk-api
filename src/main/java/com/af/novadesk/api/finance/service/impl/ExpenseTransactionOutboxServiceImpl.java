package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.finance.constants.ExpenseEventType;
import com.af.novadesk.api.finance.entity.ExpenseTransaction;
import com.af.novadesk.api.finance.entity.ExpenseTransactionOutboxEvent;
import com.af.novadesk.api.finance.repository.ExpenseTransactionOutboxEventRepository;
import com.af.novadesk.api.finance.service.ExpenseTransactionOutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Default implementation of {@link ExpenseTransactionOutboxService} (LLR-FIN-03).
 * Persists outbox event rows inside the caller's active transaction,
 * guaranteeing at-least-once delivery without dual-write risk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseTransactionOutboxServiceImpl implements ExpenseTransactionOutboxService {

    private final ExpenseTransactionOutboxEventRepository outboxRepository;
    private final ObjectMapper                            objectMapper;

    @Override
    @Transactional
    public void publishExpenseCreated(ExpenseTransaction transaction, UUID journalId,
                                      UUID orgId, UUID authUserId) {
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

    @Override
    @Transactional
    public void publishExpenseVoided(ExpenseTransaction transaction, String voidReason,
                                     UUID orgId, UUID authUserId) {
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
            throw new com.af.novadesk.api.common.exception.OutboxPublishException(
                    "EXPENSE_OUTBOX_SERIALIZATION", java.util.UUID.randomUUID(), ex);
        }
    }

    // =========================================================================
    // Payload records (internal)
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
