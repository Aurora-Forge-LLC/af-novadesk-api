package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.MatchingScoreBreakdown;
import com.af.novadesk.api.finance.entity.BankTransaction;
import com.af.novadesk.api.finance.entity.ExpenseTransaction;
import com.af.novadesk.api.finance.entity.Vendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link MatchingScoreCalculator} — covers all scoring
 * combinations, edge cases, and threshold behaviors (LLR-BNK-02.2).
 */
@DisplayName("MatchingScoreCalculator")
class MatchingScoreCalculatorTest {

    private MatchingScoreCalculator calculator;

    private static final LocalDate JAN_5 = LocalDate.of(2026, 1, 5);
    private static final BigDecimal AMOUNT_1500_50 = new BigDecimal("1500.50");

    @BeforeEach
    void setUp() {
        calculator = new MatchingScoreCalculator();
    }

    // ── Helper factories ───────────────────────────────────────

    private BankTransaction bankTxn(LocalDate date, BigDecimal amount, String description) {
        return BankTransaction.builder()
                .transactionDate(date)
                .amount(amount)
                .description(description)
                .build();
    }

    private ExpenseTransaction expenseTxn(LocalDate date, BigDecimal amount, String vendorName) {
        Vendor vendor = Vendor.builder().vendorName(vendorName).build();
        return ExpenseTransaction.builder()
                .expenseDate(date)
                .amount(amount)
                .vendor(vendor)
                .build();
    }

    // =================================================================
    // Perfect Match Scenarios
    // =================================================================

    @Nested
    @DisplayName("Perfect match — score 100")
    class PerfectMatch {

        @Test
        @DisplayName("Same amount, same date, vendor name in description")
        void shouldScore100() {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(),
                    "AMAZON WEB SERVICES PAYMENT REF123");
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "Amazon Web Services");

            MatchingScoreBreakdown result = calculator.calculate(b, e);

            // 50 (amount) + 30 (date) + 15 (vendor name match) = 95
            assertThat(result.getTotalScore()).isEqualTo(95);
            assertThat(result.getAmountScore()).isEqualTo(50);
            assertThat(result.getDateScore()).isEqualTo(30);
            assertThat(result.getDescriptionScore()).isEqualTo(15);
            assertThat(result.getDescriptionMatchMethod())
                    .isEqualTo(MatchingScoreBreakdown.METHOD_VENDOR_NAME);
            assertThat(result.getTier()).isEqualTo(MatchingScoreBreakdown.TIER_AUTO_MATCH);
        }
    }

    // =================================================================
    // Amount Mismatch — Early Exit
    // =================================================================

    @Nested
    @DisplayName("Amount mismatch — returns 0 immediately")
    class AmountMismatch {

        @Test
        @DisplayName("Different amounts, same date and vendor")
        void shouldScore0() {
            BankTransaction b = bankTxn(JAN_5, new BigDecimal("999.99").negate(), "Amazon Payment");
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "Amazon");

            MatchingScoreBreakdown result = calculator.calculate(b, e);

            assertThat(result.getTotalScore()).isZero();
            assertThat(result.getAmountScore()).isZero();
            assertThat(result.getTier()).isEqualTo(MatchingScoreBreakdown.TIER_MANUAL_REVIEW);
        }

        @Test
        @DisplayName("Credit transaction (positive amount) matches ABS same — service layer filters credits")
        void shouldScore80ForCredit() {
            // Calculator only compares ABS amounts; service layer skips credits
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50, "Deposit");
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "Anything");

            MatchingScoreBreakdown result = calculator.calculate(b, e);

            assertThat(result.getTotalScore()).isEqualTo(80); // 50 amount + 30 date
        }

        @Test
        @DisplayName("Null amounts should return 0 — cannot match unknown amounts")
        void shouldHandleNullAmounts() {
            BankTransaction b = bankTxn(JAN_5, null, "desc");
            ExpenseTransaction e = expenseTxn(JAN_5, null, "vendor");

            MatchingScoreBreakdown result = calculator.calculate(b, e);

            assertThat(result.getTotalScore()).isZero();
            assertThat(result.getAmountScore()).isZero();
        }
    }

    // =================================================================
    // Date Scoring
    // =================================================================

    @Nested
    @DisplayName("Date proximity scoring")
    class DateScoring {

        @Test
        @DisplayName("Exact same date: +30")
        void exactDate() {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "desc");
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "vendor");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getDateScore()).isEqualTo(30);
            assertThat(result.getTotalScore()).isEqualTo(50 + 30);
        }

        @ParameterizedTest
        @CsvSource({"-1,20", "1,20"})
        @DisplayName("Within ±1 day: +20")
        void nearDate(int offset, int expectedScore) {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "desc");
            ExpenseTransaction e = expenseTxn(JAN_5.plusDays(offset), AMOUNT_1500_50, "vendor");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getDateScore()).isEqualTo(expectedScore);
        }

        @ParameterizedTest
        @CsvSource({"-3,10", "-2,10", "2,10", "3,10"})
        @DisplayName("Within ±3 days: +10")
        void closeDate(int offset, int expectedScore) {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "desc");
            ExpenseTransaction e = expenseTxn(JAN_5.plusDays(offset), AMOUNT_1500_50, "vendor");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getDateScore()).isEqualTo(expectedScore);
        }

        @Test
        @DisplayName("More than 3 days apart: +0")
        void farDate() {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "desc");
            ExpenseTransaction e = expenseTxn(LocalDate.of(2026, 2, 1), AMOUNT_1500_50, "vendor");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getDateScore()).isZero();
            assertThat(result.getTotalScore()).isEqualTo(50); // only amount
        }
    }

    // =================================================================
    // Description Scoring
    // =================================================================

    @Nested
    @DisplayName("Description similarity scoring")
    class DescriptionScoring {

        @Test
        @DisplayName("Vendor name in description: +15")
        void vendorNameMatch() {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(),
                    "PAYMENT TO MICROSOFT CORPORATION 4029357");
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "Microsoft Corporation");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getDescriptionScore()).isEqualTo(15);
            assertThat(result.getDescriptionMatchMethod())
                    .isEqualTo(MatchingScoreBreakdown.METHOD_VENDOR_NAME);
            assertThat(result.getTotalScore()).isEqualTo(50 + 30 + 15); // 95
        }

        @Test
        @DisplayName("Partial token match: should match if all meaningful tokens present")
        void partialVendorNameMatch() {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(),
                    "OFFICE SUPPLIES DEPOT PAYMENT");
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "Supplies Depot");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getDescriptionScore()).isEqualTo(15);
            assertThat(result.getDescriptionMatchMethod())
                    .isEqualTo(MatchingScoreBreakdown.METHOD_VENDOR_NAME);
        }

        @Test
        @DisplayName("Empty vendor name: +0 description")
        void emptyVendorName() {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "Some random text");
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getDescriptionScore()).isZero();
            assertThat(result.getTotalScore()).isEqualTo(50 + 30); // 80 — qualifies for auto-match
        }

        @Test
        @DisplayName("Null vendor: +0 description")
        void nullVendor() {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "desc");
            ExpenseTransaction e = ExpenseTransaction.builder()
                    .expenseDate(JAN_5)
                    .amount(AMOUNT_1500_50)
                    .vendor(null)
                    .build();

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getDescriptionScore()).isZero();
        }

        @Test
        @DisplayName("Empty bank description: +0 description")
        void emptyBankDescription() {
            BankTransaction b = BankTransaction.builder()
                    .transactionDate(JAN_5)
                    .amount(AMOUNT_1500_50.negate())
                    .description("")
                    .build();
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "Vendor");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getDescriptionScore()).isZero();
        }
    }

    // =================================================================
    // Threshold Testing
    // =================================================================

    @Nested
    @DisplayName("Decision thresholds")
    class Thresholds {

        @Test
        @DisplayName("Score 80 → AUTO_MATCH")
        void autoMatchThreshold() {
            // Amount (50) + Date exact (30) = 80, no description match needed
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "xyz");
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "unrelated vendor");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getTotalScore()).isEqualTo(80);
            assertThat(result.getTier()).isEqualTo(MatchingScoreBreakdown.TIER_AUTO_MATCH);
        }

        @Test
        @DisplayName("Score 60 → SUGGEST")
        void suggestThreshold() {
            // Amount (50) + Date near (10) = 60, no description
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "xyz");
            ExpenseTransaction e = expenseTxn(JAN_5.plusDays(3), AMOUNT_1500_50, "unrelated");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getTotalScore()).isEqualTo(60);
            assertThat(result.getTier()).isEqualTo(MatchingScoreBreakdown.TIER_SUGGEST);
        }

        @Test
        @DisplayName("Score 75 → SUGGEST (amount 50 + date 20 + fuzzy desc 5)")
        void suggestWithFuzzyDescription() {
            // "mcrosoft" vs "microsft" — Levenshtein distance 2 → SCORE_DESC_CLOSE=10 → total=80
            // Use strings with distance 4 to get SCORE_DESC_FUZZY=5 → 50+20+5=75
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "mcrsoft");
            ExpenseTransaction e = expenseTxn(JAN_5.plusDays(1), AMOUNT_1500_50, "micro");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getTotalScore()).isEqualTo(75);
            assertThat(result.getTier()).isEqualTo(MatchingScoreBreakdown.TIER_SUGGEST);
        }

        @Test
        @DisplayName("Score 50 → MANUAL_REVIEW")
        void manualReview() {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "xyz");
            ExpenseTransaction e = expenseTxn(LocalDate.of(2026, 3, 1), AMOUNT_1500_50, "unrelated");

            MatchingScoreBreakdown result = calculator.calculate(b, e);
            assertThat(result.getTotalScore()).isEqualTo(50);
            assertThat(result.getTier()).isEqualTo(MatchingScoreBreakdown.TIER_MANUAL_REVIEW);
        }
    }

    // =================================================================
    // Null Safety
    // =================================================================

    @Nested
    @DisplayName("Null argument validation")
    class NullSafety {

        @Test
        @DisplayName("Null bank transaction throws")
        void nullBankTransaction() {
            ExpenseTransaction e = expenseTxn(JAN_5, AMOUNT_1500_50, "vendor");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.calculate(null, e))
                    .withMessageContaining("bankTxn");
        }

        @Test
        @DisplayName("Null expense transaction throws")
        void nullExpenseTransaction() {
            BankTransaction b = bankTxn(JAN_5, AMOUNT_1500_50.negate(), "desc");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.calculate(b, null))
                    .withMessageContaining("expenseTxn");
        }
    }
}
