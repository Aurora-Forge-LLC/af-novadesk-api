package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.MatchingScoreBreakdown;
import com.af.novadesk.api.finance.entity.BankTransaction;
import com.af.novadesk.api.finance.entity.ExpenseTransaction;
import com.af.novadesk.api.finance.entity.VendorMapping;
import com.af.novadesk.api.finance.repository.VendorMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Calculates structured matching scores between bank transactions and
 * expense transactions with vendor pattern recognition (LLR-BNK-02.2 + BNK-02.5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingScoreCalculator {

    static final int SCORE_AMOUNT_EXACT = 50;
    static final int SCORE_DATE_EXACT = 30;
    static final int SCORE_DATE_NEAR = 20;
    static final int SCORE_DATE_CLOSE = 10;
    static final int SCORE_DESC_KNOWN_PATTERN = 20;  // VendorMapping match
    static final int SCORE_DESC_VENDOR = 15;
    static final int SCORE_DESC_CLOSE = 10;
    static final int SCORE_DESC_FUZZY = 5;

    public static final int AUTO_MATCH_THRESHOLD = 80;
    public static final int SUGGEST_THRESHOLD = 60;

    private static final int LEVENSHTEIN_CLOSE = 3;
    private static final int LEVENSHTEIN_FUZZY = 5;

    private final LevenshteinDistance levenshtein = new LevenshteinDistance();
    private final VendorMappingRepository vendorMappingRepository;

    /**
     * Calculate score with vendor pattern preload (for batch processing).
     */
    public MatchingScoreBreakdown calculate(BankTransaction bankTxn, ExpenseTransaction expenseTxn) {
        return calculate(bankTxn, expenseTxn, null);
    }

    /**
     * Calculate score with optional pre-loaded vendor patterns for performance.
     *
     * @param bankTxn         the bank transaction
     * @param expenseTxn      the expense transaction
     * @param vendorPatterns  pre-loaded patterns for the entity (null = query per call)
     */
    public MatchingScoreBreakdown calculate(BankTransaction bankTxn, ExpenseTransaction expenseTxn,
                                             List<VendorMapping> vendorPatterns) {
        if (bankTxn == null || expenseTxn == null) {
            throw new IllegalArgumentException("bankTxn and expenseTxn must not be null");
        }

        int amountScore = 0, dateScore = 0, descScore = 0;
        String descMethod = MatchingScoreBreakdown.METHOD_NONE;

        // 1. Amount (50 pts, all-or-nothing)
        BigDecimal bankAmt = bankTxn.getAmount();
        BigDecimal expenseAmt = expenseTxn.getAmount();
        if (bankAmt == null || expenseAmt == null) {
            return buildBreakdown(0, 0, 0, 0, descMethod);
        }
        if (bankAmt.abs().compareTo(expenseAmt) != 0) {
            return buildBreakdown(0, 0, 0, 0, descMethod);
        }
        amountScore = SCORE_AMOUNT_EXACT;

        // 2. Date (up to 30 pts)
        if (bankTxn.getTransactionDate() != null && expenseTxn.getExpenseDate() != null) {
            long daysDiff = Math.abs(ChronoUnit.DAYS.between(
                    bankTxn.getTransactionDate(), expenseTxn.getExpenseDate()));
            if (daysDiff == 0) dateScore = SCORE_DATE_EXACT;
            else if (daysDiff <= 1) dateScore = SCORE_DATE_NEAR;
            else if (daysDiff <= 3) dateScore = SCORE_DATE_CLOSE;
        }

        // 3. Description (up to 20 pts)
        String bankDesc = bankTxn.getDescription() != null
                ? bankTxn.getDescription().toLowerCase().trim() : "";

        if (!bankDesc.isEmpty()) {
            // 3a. Check vendor patterns first (entity-specific, 20 pts)
            UUID entityId = bankTxn.getLegalEntity() != null
                    ? bankTxn.getLegalEntity().getId() : null;
            if (entityId != null && vendorPatterns != null) {
                for (VendorMapping vm : vendorPatterns) {
                    if (bankDesc.matches(likeToRegex(vm.getBankDescriptionPattern()))) {
                        descScore = SCORE_DESC_KNOWN_PATTERN;
                        descMethod = "KNOWN_PATTERN";
                        break;
                    }
                }
            } else if (entityId != null) {
                // Fallback: query per call
                Optional<VendorMapping> match = vendorMappingRepository
                        .findMatchByDescription(entityId, bankTxn.getDescription());
                if (match.isPresent()) {
                    descScore = SCORE_DESC_KNOWN_PATTERN;
                    descMethod = "KNOWN_PATTERN";
                }
            }

            // 3b. If no pattern match, fall back to vendor name / Levenshtein
            if (descScore == 0) {
                String vendorName = "";
                if (expenseTxn.getVendor() != null && expenseTxn.getVendor().getVendorName() != null) {
                    vendorName = expenseTxn.getVendor().getVendorName().toLowerCase().trim();
                }
                if (!vendorName.isEmpty()) {
                    if (containsVendorName(bankDesc, vendorName)) {
                        descScore = SCORE_DESC_VENDOR;
                        descMethod = MatchingScoreBreakdown.METHOD_VENDOR_NAME;
                    } else {
                        String cleanBank = cleanDescription(bankDesc);
                        String cleanVendor = cleanDescription(vendorName);
                        if (!cleanBank.isEmpty() && !cleanVendor.isEmpty()) {
                            int distance = levenshtein.apply(cleanBank, cleanVendor);
                            if (distance < LEVENSHTEIN_CLOSE) {
                                descScore = SCORE_DESC_CLOSE;
                                descMethod = MatchingScoreBreakdown.METHOD_LEVENSHTEIN_CLOSE;
                            } else if (distance < LEVENSHTEIN_FUZZY) {
                                descScore = SCORE_DESC_FUZZY;
                                descMethod = MatchingScoreBreakdown.METHOD_LEVENSHTEIN_FUZZY;
                            }
                        }
                    }
                }
            }
        }

        int total = amountScore + dateScore + descScore;
        return buildBreakdown(total, amountScore, dateScore, descScore, descMethod);
    }

    /**
     * Convert SQL LIKE pattern to regex: "AMZN%" → "(?i)amzn.*"
     */
    static String likeToRegex(String pattern) {
        String regex = pattern.toLowerCase()
                .replace("%", ".*")
                .replace("_", ".");
        return "(?i)" + regex;
    }

    // ── Private helpers ────────────────────────────────────────

    private MatchingScoreBreakdown buildBreakdown(int total, int amount, int date, int desc, String descMethod) {
        String tier = total >= AUTO_MATCH_THRESHOLD ? MatchingScoreBreakdown.TIER_AUTO_MATCH
                : total >= SUGGEST_THRESHOLD ? MatchingScoreBreakdown.TIER_SUGGEST
                : MatchingScoreBreakdown.TIER_MANUAL_REVIEW;
        return MatchingScoreBreakdown.builder()
                .totalScore(total).amountScore(amount).dateScore(date)
                .descriptionScore(desc).descriptionMatchMethod(descMethod).tier(tier).build();
    }

    private boolean containsVendorName(String bankDesc, String vendorName) {
        String[] tokens = vendorName.split("[\\s,]+");
        int meaningful = 0, matched = 0;
        for (String t : tokens) {
            if (t.length() > 2) { meaningful++; if (bankDesc.contains(t)) matched++; }
        }
        return meaningful > 0 && matched == meaningful;
    }

    private String cleanDescription(String desc) {
        return desc.replaceAll("\\d{2,4}[-/.]\\d{2}[-/.]\\d{2,4}", "")
                .replaceAll("REF#?\\s*[A-Z0-9]+", "")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ").trim();
    }
}
