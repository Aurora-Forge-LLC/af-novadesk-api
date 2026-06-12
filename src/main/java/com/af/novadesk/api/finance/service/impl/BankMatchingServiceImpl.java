package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.dto.BankTransactionDto;
import com.af.novadesk.api.finance.dto.BankTransactionPageDto;

import com.af.novadesk.api.finance.constants.MatchingMethod;
import com.af.novadesk.api.finance.constants.ReconciliationStatus;
import com.af.novadesk.api.finance.constants.SuggestionStatus;
import com.af.novadesk.api.finance.constants.VendorMappingConfidence;
import com.af.novadesk.api.finance.dto.MatchingScoreBreakdown;
import com.af.novadesk.api.finance.dto.ResolveSuggestionRequest;
import com.af.novadesk.api.finance.dto.SuggestedMatchDto;
import com.af.novadesk.api.finance.dto.SuggestedMatchPageDto;
import com.af.novadesk.api.finance.mapper.BankTransactionMapper;
import com.af.novadesk.api.finance.entity.BankTransaction;
import com.af.novadesk.api.finance.entity.ExpenseTransaction;
import com.af.novadesk.api.finance.entity.SuggestedMatch;
import com.af.novadesk.api.finance.entity.VendorMapping;
import com.af.novadesk.api.finance.repository.BankTransactionRepository;
import com.af.novadesk.api.finance.repository.ExpenseTransactionRepository;
import com.af.novadesk.api.finance.repository.SuggestedMatchRepository;
import com.af.novadesk.api.finance.repository.VendorMappingRepository;
import com.af.novadesk.api.finance.service.BankMatchingService;
import com.af.novadesk.api.finance.service.MatchingScoreCalculator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankMatchingServiceImpl implements BankMatchingService {

    private final BankTransactionRepository bankTransactionRepository;
    private final ExpenseTransactionRepository expenseTransactionRepository;
    private final SuggestedMatchRepository suggestedMatchRepository;
    private final VendorMappingRepository vendorMappingRepository;
    private final MatchingScoreCalculator scoreCalculator;
    private final ObjectMapper objectMapper;
    private final BankTransactionMapper transactionMapper;

    private static final int BATCH_SIZE = 200;

    @Override
    @Async
    @Transactional
    public void matchStatement(UUID statementId) {
        log.info("Starting auto-matching for statement {}", statementId);
        List<BankTransaction> toMatch = bankTransactionRepository
                .findByStatementIdOrderByTransactionDateAsc(statementId).stream()
                .filter(tx -> tx.getReconciliationStatus() == ReconciliationStatus.UNMATCHED)
                .toList();
        if (toMatch.isEmpty()) return;

        UUID entityId = toMatch.get(0).getLegalEntity().getId();
        List<ExpenseTransaction> expenses = expenseTransactionRepository.findUnreconciledByLegalEntity(entityId);
        List<VendorMapping> vendorPatterns = vendorMappingRepository.findByLegalEntityIdAndStatus(entityId, "ACTIVE");
        if (expenses.isEmpty()) return;

        log.info("Matching {} bank txns against {} expenses for entity {}", toMatch.size(), expenses.size(), entityId);

        for (int i = 0; i < toMatch.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, toMatch.size());
            processBatch(toMatch.subList(i, end), expenses, vendorPatterns);
        }
        log.info("Auto-matching complete for statement {}", statementId);
    }

    @Override
    @Async
    @Transactional
    public void matchTransaction(UUID bankTransactionId) {
        BankTransaction bankTxn = bankTransactionRepository.findById(bankTransactionId).orElse(null);
        if (bankTxn == null || bankTxn.getReconciliationStatus() != ReconciliationStatus.UNMATCHED) return;

        UUID entityId = bankTxn.getLegalEntity().getId();
        List<ExpenseTransaction> expenses = expenseTransactionRepository.findUnreconciledByLegalEntity(entityId);

        for (ExpenseTransaction expense : expenses) {
            MatchingScoreBreakdown breakdown = scoreCalculator.calculate(bankTxn, expense);
            if (breakdown.getTotalScore() >= MatchingScoreCalculator.AUTO_MATCH_THRESHOLD) {
                executeAutoMatch(bankTxn, expense, breakdown);
                return;
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SuggestedMatchPageDto getSuggestedMatches(int page, int size) {
        Page<SuggestedMatch> result = suggestedMatchRepository.findAllPending(
                SuggestionStatus.PENDING, PageRequest.of(page, size));

        List<SuggestedMatchDto> content = result.getContent().stream()
                .map(this::toDto)
                .toList();

        return SuggestedMatchPageDto.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BankTransactionPageDto getUnmatchedTransactions(
            UUID entityId, UUID bankAccountId,
            LocalDate dateFrom, LocalDate dateTo,
            BigDecimal amountMin, BigDecimal amountMax,
            String search, int page, int size, String sortBy, String sortDir) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<BankTransaction> result = bankTransactionRepository.findUnmatchedWithFilters(
                entityId, bankAccountId, dateFrom, dateTo,
                amountMin, amountMax, search,
                sortBy != null ? sortBy : "transactionDate",
                sortDir != null ? sortDir : "DESC",
                pageRequest);

        List<BankTransactionDto> content = result.getContent().stream()
                .map(transactionMapper::toDto)
                .toList();

        return BankTransactionPageDto.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public SuggestedMatchDto resolveSuggestion(UUID suggestionId, ResolveSuggestionRequest request) {
        SuggestedMatch suggestion = suggestedMatchRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Suggested match not found: " + suggestionId));

        if (suggestion.getSuggestionStatus() != SuggestionStatus.PENDING) {
            throw new IllegalArgumentException("Suggestion is already " + suggestion.getSuggestionStatus());
        }

        if (request.isAccept()) {
            return acceptSuggestion(suggestion);
        } else if (request.isReject()) {
            return rejectSuggestion(suggestion);
        } else {
            throw new IllegalArgumentException("Action must be ACCEPT or REJECT");
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void processBatch(List<BankTransaction> batch, List<ExpenseTransaction> expenses,
                               List<VendorMapping> vendorPatterns) {
        for (BankTransaction bankTxn : batch) {
            if (bankTxn.getAmount().signum() >= 0) continue; // skip credits

            BankTransaction fresh = bankTransactionRepository.findById(bankTxn.getId()).orElse(null);
            if (fresh == null || fresh.getReconciliationStatus() != ReconciliationStatus.UNMATCHED) continue;

            MatchingScoreBreakdown bestBreakdown = null;
            ExpenseTransaction bestMatch = null;

            for (ExpenseTransaction expense : expenses) {
                if (expense.getMatchedBankTransactionId() != null) continue;
                MatchingScoreBreakdown bd = scoreCalculator.calculate(fresh, expense, vendorPatterns);
                if (bestBreakdown == null || bd.getTotalScore() > bestBreakdown.getTotalScore()) {
                    bestBreakdown = bd;
                    bestMatch = expense;
                }
            }

            if (bestMatch == null) continue;

            if (bestBreakdown.getTotalScore() >= MatchingScoreCalculator.AUTO_MATCH_THRESHOLD) {
                executeAutoMatch(fresh, bestMatch, bestBreakdown);
            } else if (bestBreakdown.getTotalScore() >= MatchingScoreCalculator.SUGGEST_THRESHOLD) {
                createSuggestion(fresh, bestMatch, bestBreakdown);
            }
        }
    }

    private void executeAutoMatch(BankTransaction bankTxn, ExpenseTransaction expenseTxn, MatchingScoreBreakdown breakdown) {
        log.info("Auto-match: bankTxn={} → expense={} score={}", bankTxn.getId(), expenseTxn.getId(), breakdown.getTotalScore());
        bankTxn.setReconciliationStatus(ReconciliationStatus.MATCHED);
        bankTxn.setMatchingScore(breakdown.getTotalScore());
        bankTxn.setMatchingMethod(MatchingMethod.AUTO_MATCHED);
        bankTxn.setMatchedExpenseId(expenseTxn.getId());
        bankTransactionRepository.save(bankTxn);
        expenseTxn.setReconciliationStatus("RECONCILED");
        expenseTxn.setMatchedBankTransactionId(bankTxn.getId());
        expenseTransactionRepository.save(expenseTxn);

        // LLR-BNK-02.5: Learn vendor pattern from this match
        learnVendorMapping(bankTxn, expenseTxn);
    }

    private void createSuggestion(BankTransaction bankTxn, ExpenseTransaction expenseTxn, MatchingScoreBreakdown breakdown) {
        if (suggestedMatchRepository.existsByBankTransactionIdAndExpenseTransactionId(
                bankTxn.getId(), expenseTxn.getId())) {
            return; // Don't create duplicates
        }

        bankTxn.setReconciliationStatus(ReconciliationStatus.SUGGESTED);
        bankTxn.setMatchingScore(breakdown.getTotalScore());
        bankTransactionRepository.save(bankTxn);

        SuggestedMatch sm = SuggestedMatch.builder()
                .bankTransaction(bankTxn)
                .expenseTransaction(expenseTxn)
                .matchingScore(breakdown.getTotalScore())
                .scoreBreakdown(toJson(breakdown))
                .suggestedBy(SuggestedMatch.SuggestedBy.SYSTEM)
                .suggestionStatus(SuggestionStatus.PENDING)
                .build();
        suggestedMatchRepository.save(sm);

        log.info("Suggestion created: bankTxn={} → expense={} score={}", bankTxn.getId(), expenseTxn.getId(), breakdown.getTotalScore());
    }

    private SuggestedMatchDto acceptSuggestion(SuggestedMatch sm) {
        MatchingScoreBreakdown breakdown = parseBreakdown(sm.getScoreBreakdown());
        executeAutoMatch(sm.getBankTransaction(), sm.getExpenseTransaction(), breakdown);
        sm.setSuggestionStatus(SuggestionStatus.ACCEPTED);
        sm.setResolvedAt(LocalDateTime.now());
        suggestedMatchRepository.save(sm);
        return toDto(sm);
    }

    private SuggestedMatchDto rejectSuggestion(SuggestedMatch sm) {
        sm.getBankTransaction().setReconciliationStatus(ReconciliationStatus.UNMATCHED);
        sm.getBankTransaction().setMatchingScore(null);
        bankTransactionRepository.save(sm.getBankTransaction());
        sm.setSuggestionStatus(SuggestionStatus.REJECTED);
        sm.setResolvedAt(LocalDateTime.now());
        suggestedMatchRepository.save(sm);
        return toDto(sm);
    }

    // ── Mapping helpers ───────────────────────────────────────

    // ── Vendor Pattern Learning (LLR-BNK-02.5) ───────────────

    /**
     * Derive a SQL LIKE pattern from bank description and upsert a VendorMapping.
     * After 3+ matches, confidence upgrades to AUTO_LEARNED.
     */
    private void learnVendorMapping(BankTransaction bankTxn, ExpenseTransaction expenseTxn) {
        if (bankTxn.getDescription() == null || expenseTxn.getVendor() == null) return;

        String pattern = derivePattern(bankTxn.getDescription());
        UUID entityId = bankTxn.getLegalEntity().getId();

        Optional<VendorMapping> existing = vendorMappingRepository
                .findByLegalEntityIdAndBankDescriptionPattern(entityId, pattern);

        if (existing.isPresent()) {
            VendorMapping vm = existing.get();
            vm.setMatchCount(vm.getMatchCount() + 1);
            if (vm.getMatchCount() >= 3) {
                vm.setConfidenceLevel(VendorMappingConfidence.AUTO_LEARNED);
            }
            vendorMappingRepository.save(vm);
            log.debug("Updated vendor mapping: pattern={} count={} confidence={}",
                    pattern, vm.getMatchCount(), vm.getConfidenceLevel());
        } else {
            VendorMapping vm = VendorMapping.builder()
                    .legalEntity(bankTxn.getLegalEntity())
                    .bankDescriptionPattern(pattern)
                    .vendor(expenseTxn.getVendor())
                    .confidenceLevel(VendorMappingConfidence.USER_CONFIRMED)
                    .matchCount(1)
                    .build();
            vendorMappingRepository.save(vm);
            log.info("Learned new vendor mapping: '{}' → pattern='{}' vendor='{}'",
                    bankTxn.getDescription(), pattern, expenseTxn.getVendor().getVendorName());
        }
    }

    /**
     * Derive a SQL LIKE pattern from a bank description.
     * Strategy: take the first all-alphabetic token ≥3 characters, append '%'.
     *
     * Examples: "AMZN MKTP US*1234ABC" → "AMZN%", "AWS*AMAZON" → "AWS%"
     */
    private String derivePattern(String description) {
        String[] tokens = description.trim().split("[\\s\\*]+");
        for (String token : tokens) {
            String cleaned = token.replaceAll("[^A-Za-z]", "");
            if (cleaned.length() >= 3) {
                return cleaned.toUpperCase() + "%";
            }
        }
        // Fallback: first 5 chars + %
        return description.trim().substring(0, Math.min(5, description.trim().length())).toUpperCase() + "%";
    }

    // ── Mapping helpers ───────────────────────────────────────

    private SuggestedMatchDto toDto(SuggestedMatch sm) {
        BankTransaction bt = sm.getBankTransaction();
        ExpenseTransaction et = sm.getExpenseTransaction();
        return SuggestedMatchDto.builder()
                .id(sm.getId())
                .bankTransaction(SuggestedMatchDto.BankTxnSummary.builder()
                        .id(bt.getId())
                        .transactionDate(bt.getTransactionDate())
                        .description(bt.getDescription())
                        .amount(bt.getAmount())
                        .balance(bt.getBalance())
                        .build())
                .suggestedExpense(SuggestedMatchDto.ExpenseSummary.builder()
                        .id(et.getId())
                        .expenseDate(et.getExpenseDate())
                        .amount(et.getAmount())
                        .vendorName(et.getVendor() != null ? et.getVendor().getVendorName() : null)
                        .currencyCode(et.getCurrencyCode())
                        .build())
                .matchingScore(sm.getMatchingScore())
                .scoreBreakdown(sm.getScoreBreakdown())
                .suggestedBy(sm.getSuggestedBy().name())
                .suggestionStatus(sm.getSuggestionStatus().name())
                .createdAt(sm.getCreatedAt())
                .build();
    }

    private String toJson(MatchingScoreBreakdown breakdown) {
        try {
            return objectMapper.writeValueAsString(breakdown);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize score breakdown", e);
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    private MatchingScoreBreakdown parseBreakdown(String json) {
        if (json == null) return MatchingScoreBreakdown.builder().totalScore(0).build();
        try {
            return objectMapper.readValue(json, MatchingScoreBreakdown.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse score breakdown JSON", e);
            return MatchingScoreBreakdown.builder().totalScore(0).build();
        }
    }
}
