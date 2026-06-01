package com.af.novadesk.api.finance.exception;

import java.util.UUID;

/**
 * Thrown when an {@link com.af.novadesk.api.finance.entity.ExpenseAttachment}
 * with the given ID does not exist or does not belong to the specified
 * expense transaction. Maps to HTTP 404 Not Found.
 */
public class AttachmentNotFoundException extends FinanceBaseException {

    public AttachmentNotFoundException(UUID attachmentId) {
        super("FIN_EXPENSE_003",
                String.format("Attachment not found: %s", attachmentId));
    }

    public AttachmentNotFoundException(UUID attachmentId, UUID transactionId) {
        super("FIN_EXPENSE_003",
                String.format("Attachment %s not found on transaction %s", attachmentId, transactionId));
    }
}
