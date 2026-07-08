package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.PaymentMethod;
import com.af.novadesk.api.finance.dto.ExpenseAttachmentDto;
import com.af.novadesk.api.finance.dto.ExpenseLedgerResponse;
import com.af.novadesk.api.finance.dto.ExpenseTransactionDto;
import com.af.novadesk.api.finance.dto.ExpenseTransactionPageDto;
import com.af.novadesk.api.finance.dto.VoidExpenseDto;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for Manual Expense Recording (LLR-FIN-03).
 *
 * <p>All operations are scoped to the caller's {@code organizationId} from the JWT.
 * Every {@link #recordExpense} call produces exactly two {@code fa_ledger_entries}
 * rows and one {@code exp_expense_outbox_events} row in the same database transaction
 * (Transactional Outbox Pattern, LLR-FIN-03.2).</p>
 */
public interface ExpenseTransactionService {

    /**
     * Records a new expense, posts double-entry ledger entries (CREDIT source,
     * DEBIT destination), and publishes {@code EXPENSE_CREATED} to the outbox —
     * all within a single transaction (LLR-FIN-03.2).
     */
    ExpenseTransactionDto recordExpense(ExpenseTransactionDto request);

    /**
     * Returns a paginated, filtered list of expense transactions for the caller's organization.
     */
    ExpenseTransactionPageDto listExpenses(
            int page, int size, String sortBy, String sortDir,
            String q, String status,
            UUID legalEntityId, UUID vendorId,
            PaymentMethod paymentMethod,
            LocalDate fromDate, LocalDate toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String reconciliationStatus);

    /**
     * Returns a single expense transaction with all relations and attachments.
     * Throws {@link com.af.novadesk.api.finance.exception.ExpenseTransactionNotFoundException}
     * if not found or belongs to a different organization.
     */
    ExpenseTransactionDto getExpense(UUID id);

    /**
     * Voids a POSTED expense: sets status to VOID, posts two offsetting reversal
     * ledger entries (DEBIT source, CREDIT destination), and publishes
     * {@code EXPENSE_VOIDED} to the outbox — all within a single transaction.
     * Throws {@link com.af.novadesk.api.finance.exception.InvalidExpenseStateException}
     * if the transaction is already VOID.
     */
    ExpenseTransactionDto voidExpense(UUID id, VoidExpenseDto request);

    /**
     * Returns all ledger journals posted for a single expense transaction, grouped by
     * {@code journalId} in chronological order.
     *
     * <p>A POSTED expense has one journal (ORIGINAL).
     * A VOID expense has two journals (ORIGINAL + VOID_REVERSAL).</p>
     *
     * <p>Scoped to the caller's organization — throws
     * {@link com.af.novadesk.api.finance.exception.ExpenseTransactionNotFoundException}
     * if the transaction does not exist or belongs to a different organization.</p>
     */
    ExpenseLedgerResponse getExpenseLedger(UUID id);

    /**
     * Validates, stores, and links a file attachment to an expense transaction (LLR-FIN-03.4).
     * Allowed types: PDF, PNG, JPG, JPEG. Maximum size: 5 242 880 bytes (5 MB).
     * Only POSTED transactions accept new attachments.
     */
    ExpenseAttachmentDto uploadAttachment(UUID transactionId, MultipartFile file);

    /**
     * Lists all attachment metadata for an expense transaction.
     * Does not return file content — use the {@code downloadUrl} field to retrieve the file.
     */
    List<ExpenseAttachmentDto> listAttachments(UUID transactionId);

    /**
     * Deletes an attachment record and removes the file from object storage.
     * Only allowed on POSTED (non-voided) transactions.
     * Throws {@link com.af.novadesk.api.finance.exception.AttachmentNotFoundException}
     * if the attachment does not exist or does not belong to the given transaction.
     */
    void deleteAttachment(UUID transactionId, UUID attachmentId);
}
