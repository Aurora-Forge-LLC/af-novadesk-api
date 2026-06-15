package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.BankTransactionPageDto;
import com.af.novadesk.api.finance.dto.ResolveSuggestionRequest;
import com.af.novadesk.api.finance.dto.SuggestedMatchDto;
import com.af.novadesk.api.finance.dto.SuggestedMatchPageDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for automatically matching bank transactions to expense transactions (LLR-BNK-02).
 */
public interface BankMatchingService {

    /** Run matching for all UNMATCHED transactions in a statement (async). */
    void matchStatement(UUID statementId);

    /** Run matching for a single bank transaction (async). */
    void matchTransaction(UUID bankTransactionId);

    /** Get paginated list of pending suggested matches for user review. */
    SuggestedMatchPageDto getSuggestedMatches(int page, int size);

    /** Accept or reject a suggested match. */
    SuggestedMatchDto resolveSuggestion(UUID suggestionId, ResolveSuggestionRequest request);

    /** Get paginated unmatched transactions with filters (LLR-BNK-03.1). */
    BankTransactionPageDto getUnmatchedTransactions(
            UUID entityId, UUID bankAccountId,
            LocalDate dateFrom, LocalDate dateTo,
            BigDecimal amountMin, BigDecimal amountMax,
            String search, int page, int size, String sortBy, String sortDir);
}
