package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.constants.SuggestionStatus;
import com.af.novadesk.api.finance.entity.SuggestedMatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SuggestedMatchRepository extends JpaRepository<SuggestedMatch, UUID> {

    boolean existsByBankTransactionIdAndExpenseTransactionId(UUID bankTxnId, UUID expenseTxnId);

    @Query("""
        SELECT sm FROM SuggestedMatch sm
        JOIN FETCH sm.bankTransaction bt
        JOIN FETCH sm.expenseTransaction et
        JOIN FETCH et.vendor
        WHERE sm.suggestionStatus = :status
        ORDER BY sm.matchingScore DESC, sm.createdAt DESC
    """)
    Page<SuggestedMatch> findAllPending(@Param("status") SuggestionStatus status, Pageable pageable);
}
