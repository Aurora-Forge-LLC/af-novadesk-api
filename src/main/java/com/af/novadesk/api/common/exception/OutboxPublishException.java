package com.af.novadesk.api.common.exception;

import java.util.UUID;

/**
 * Thrown when the transactional outbox fails to persist an event row
 * within the business transaction. This is a fatal condition — the
 * service layer should let it propagate to roll back the entire transaction.
 */
public class OutboxPublishException extends FinanceBaseException {
    public OutboxPublishException(String eventType, UUID aggregateId, Throwable cause) {
        super("FIN_OUTBOX_001",
                String.format("Failed to publish outbox event '%s' for aggregate %s",
                        eventType, aggregateId),
                cause);
    }
}

