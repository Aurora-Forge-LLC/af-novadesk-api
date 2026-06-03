package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.dto.AggregateSum;
import com.af.novadesk.api.finance.dto.EntityBalanceResponse;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.repository.CapitalInjectionRepository;
import com.af.novadesk.api.finance.repository.ExpenseTransactionRepository;
import com.af.novadesk.api.finance.repository.LedgerEntryRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.EntityBalanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Default implementation of {@link EntityBalanceService}.
 *
 * <p>Aggregates capital injections directly from the {@code fa_capital_injections}
 * table (which stores both local and USD amounts) and expenses from the
 * {@code exp_expense_transactions} table (local amount) plus the associated
 * {@code fa_ledger_entries} (USD amount via DEBIT legs with reference type
 * {@code "EXPENSE"}).</p>
 */
@Service
@Transactional(readOnly = true)
public class EntityBalanceServiceImpl implements EntityBalanceService {

    /** Reference type used by capital-injection ledger entries. */
    private static final String REFERENCE_TYPE_CAPITAL_INJECTION = "CAPITAL_INJECTION";

    /** Reference type used by expense ledger entries (LLR-FIN-03). */
    private static final String REFERENCE_TYPE_EXPENSE = "EXPENSE";

    private final LegalEntityRepository         legalEntityRepository;
    private final CapitalInjectionRepository     capitalInjectionRepository;
    private final ExpenseTransactionRepository   expenseTransactionRepository;
    private final LedgerEntryRepository          ledgerEntryRepository;
    private final FinanceSecurityContext          securityContext;

    public EntityBalanceServiceImpl(
            LegalEntityRepository legalEntityRepository,
            CapitalInjectionRepository capitalInjectionRepository,
            ExpenseTransactionRepository expenseTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            FinanceSecurityContext securityContext
    ) {
        this.legalEntityRepository         = legalEntityRepository;
        this.capitalInjectionRepository    = capitalInjectionRepository;
        this.expenseTransactionRepository  = expenseTransactionRepository;
        this.ledgerEntryRepository         = ledgerEntryRepository;
        this.securityContext               = securityContext;
    }

    @Override
    public EntityBalanceResponse getEntityBalance(String entityCode) {
        String normalizedCode = normalize(entityCode);

        LegalEntity entity = legalEntityRepository
                .findByEntityCodeAndOrganizationId(normalizedCode, securityContext.getOrganizationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        java.util.UUID.nameUUIDFromBytes(normalizedCode.getBytes())));

        String baseCurrency = entity.getBaseCurrency();

        // ── Capital Injections ─────────────────────────────────────────────────
        AggregateSum ciSums = capitalInjectionRepository.sumByTargetEntity(entity);
        BigDecimal ciLocal = ciSums.local();
        BigDecimal ciUsd   = ciSums.usd();

        // ── Expenses ───────────────────────────────────────────────────────────
        // Local amount: sum from exp_expense_transactions (future-proof —
        // returns 0 while the expense module is stubbed, will pick up real
        // data once LLR-FIN-03 is implemented).
        BigDecimal expLocal = expenseTransactionRepository.sumAmountByLegalEntity(entity);
        if (expLocal == null) {
            expLocal = BigDecimal.ZERO;
        }

        // USD amount: sum DEBIT ledger entries for the expense reference type.
        // This also returns 0 while expenses are stubbed.
        AggregateSum expSums = ledgerEntryRepository.sumByEntityAndReferenceType(
                entity, REFERENCE_TYPE_EXPENSE);
        BigDecimal expUsd = expSums.usd();

        // ── Net Available Capital ──────────────────────────────────────────────
        BigDecimal netLocal = ciLocal.subtract(expLocal);
        BigDecimal netUsd   = ciUsd.subtract(expUsd);

        return new EntityBalanceResponse(
                entity.getEntityCode(),
                entity.getEntityName(),
                baseCurrency,
                ciLocal, ciUsd,
                expLocal, expUsd,
                netLocal, netUsd
        );
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
