package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.BulkCategorizeRequest;
import com.af.novadesk.api.finance.dto.CategorizeTransactionRequest;
import com.af.novadesk.api.finance.dto.ExpenseTransactionDto;
import com.af.novadesk.api.finance.dto.SplitTransactionRequest;

import java.util.List;
import java.util.UUID;

/**
 * Service for manual categorization of unmatched bank transactions (LLR-BNK-03).
 *
 * <p>Creates expense transactions with proper double-entry ledger entries
 * and marks the bank transaction as matched.</p>
 */
public interface BankCategorizationService {

    /**
     * Categorize a single unmatched bank transaction by creating an expense
     * transaction with double-entry ledger entries.
     *
     * @param bankTransactionId UUID of the UNMATCHED bank transaction
     * @param request           categorization details (vendor, chart of account, notes)
     * @return the created expense transaction DTO
     */
    ExpenseTransactionDto categorizeTransaction(UUID bankTransactionId, CategorizeTransactionRequest request);

    /**
     * Bulk-categorize multiple unmatched bank transactions with the same
     * vendor and expense category (LLR-BNK-03.4).
     *
     * @param request list of transaction IDs with shared vendor/COA/notes
     * @return list of created expense transaction DTOs
     */
    List<ExpenseTransactionDto> bulkCategorize(BulkCategorizeRequest request);

    /**
     * Split a single bank transaction into multiple expense transactions
     * with different vendors and expense categories (LLR-BNK-03.5).
     *
     * <p>Validation: {@code SUM(all split lines' amounts)} must equal
     * {@code ABS(bank transaction amount)} within a tolerance of 0.001.</p>
     *
     * @param bankTransactionId UUID of the UNMATCHED bank transaction
     * @param request           split lines with vendor, COA, and amount per line
     * @return list of created expense transaction DTOs (one per split line)
     */
    List<ExpenseTransactionDto> splitTransaction(UUID bankTransactionId, SplitTransactionRequest request);
}
