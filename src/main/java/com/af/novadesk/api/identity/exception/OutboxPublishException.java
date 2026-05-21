package com.af.novadesk.api.identity.exception;

import java.util.UUID;

/**
 * This file is deprecated - use com.af.novadesk.api.common.exception.OutboxPublishException instead.
 * Kept for backward compatibility.
 */
public class OutboxPublishException extends com.af.novadesk.api.common.exception.OutboxPublishException {
    public OutboxPublishException(String eventType, UUID aggregateId, Throwable cause) {
        super(eventType, aggregateId, cause);
    }
}
