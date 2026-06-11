package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.ResolveSuggestionRequest;
import com.af.novadesk.api.finance.dto.SuggestedMatchDto;
import com.af.novadesk.api.finance.dto.SuggestedMatchPageDto;

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
}
