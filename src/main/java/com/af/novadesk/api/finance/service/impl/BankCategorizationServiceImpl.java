package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.constants.*;
import com.af.novadesk.api.finance.dto.BulkCategorizeRequest;
import com.af.novadesk.api.finance.dto.CategorizeTransactionRequest;
import com.af.novadesk.api.finance.dto.ExpenseTransactionDto;
import com.af.novadesk.api.finance.dto.SplitLineItem;
import com.af.novadesk.api.finance.dto.SplitTransactionRequest;
import com.af.novadesk.api.finance.entity.*;
import com.af.novadesk.api.finance.repository.*;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.BankCategorizationService;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Full implementation of {@link BankCategorizationService} — creates expense transactions
 * with double-entry ledger entries from unmatched bank transactions (LLR-BNK-03.3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankCategorizationServiceImpl implements BankCategorizationService {

    private final BankTransactionRepository bankTransactionRepository;
    private final ExpenseTransactionRepository expenseTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final VendorRepository vendorRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final ShadowUserRepository shadowUserRepository;
    private final FinanceSecurityContext securityContext;
    private final VendorMappingRepository vendorMappingRepository;

    @Override
    @Transactional
    public ExpenseTransactionDto categorizeTransaction(UUID bankTransactionId, CategorizeTransactionRequest request) {
        // 1. Load and validate bank transaction
        BankTransaction bankTxn = bankTransactionRepository.findById(bankTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Bank transaction not found: " + bankTransactionId));

        if (bankTxn.getReconciliationStatus() != ReconciliationStatus.UNMATCHED) {
            throw new IllegalStateException("Bank transaction is not UNMATCHED. Current: " + bankTxn.getReconciliationStatus());
        }

        // 2. Resolve references
        UUID authUserId = securityContext.getAuthUserId();
        LegalEntity legalEntity = bankTxn.getLegalEntity();

        Vendor vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + request.getVendorId()));

        ChartOfAccount chartOfAccount = chartOfAccountRepository.findById(request.getChartOfAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Chart of account not found: " + request.getChartOfAccountId()));

        ShadowUser createdBy = shadowUserRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new IllegalArgumentException("Shadow user not found"));

        // 3. Resolve source account (fa_account) from bank account type
        EntityBankAccount bankAccount = bankTxn.getBankAccount();
        Account sourceAccount = resolveSourceAccount(bankAccount, legalEntity);

        // 4. Determine amounts and currency
        BigDecimal amountLocal = bankTxn.getAmount().abs().setScale(4, RoundingMode.HALF_UP);
        String currencyCode = legalEntity.getBaseCurrency().trim();
        // For simplicity, use 1:1 exchange rate (same currency assumption)
        BigDecimal exchangeRate = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        BigDecimal amountUsd = amountLocal.multiply(exchangeRate).setScale(4, RoundingMode.HALF_UP);

        // 5. Validate COA belongs to same legal entity
        if (!chartOfAccount.getLegalEntity().getId().equals(legalEntity.getId())) {
            throw new IllegalArgumentException(
                    "Chart of account '" + chartOfAccount.getAccountName()
                    + "' does not belong to legal entity " + legalEntity.getId());
        }

        // 6. Build description with optional department/project
        String description = buildCategorizationDescription(request, bankTxn);

        // 7. Create ExpenseTransaction
        ExpenseTransaction transaction = ExpenseTransaction.builder()
                .legalEntity(legalEntity)
                .vendor(vendor)
                .createdBy(createdBy)
                .expenseDate(bankTxn.getTransactionDate())
                .amount(amountLocal)
                .currencyCode(currencyCode)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .sourceAccount(sourceAccount)
                .chartOfAccount(chartOfAccount)
                .description(description)
                .transactionStatus(ExpenseTransactionStatus.POSTED)
                .reconciliationStatus("RECONCILED")
                .matchedBankTransactionId(bankTxn.getId())
                .build();

        ExpenseTransaction saved = expenseTransactionRepository.save(transaction);

        // 7. Double-entry ledger entries: CREDIT source (bank), DEBIT expense category
        UUID journalId = UUID.randomUUID();
        List<LedgerEntry> entries = List.of(
                buildEntry(journalId, saved, legalEntity, sourceAccount,
                        LedgerEntrySide.CREDIT, amountLocal, currencyCode, amountUsd, exchangeRate),
                buildCoaEntry(journalId, saved, legalEntity, chartOfAccount,
                        LedgerEntrySide.DEBIT, amountLocal, currencyCode, amountUsd, exchangeRate)
        );
        ledgerEntryRepository.saveAll(entries);

        // 8. Update BankTransaction to MATCHED
        bankTxn.setReconciliationStatus(ReconciliationStatus.MATCHED);
        bankTxn.setMatchingMethod(MatchingMethod.MANUAL);
        bankTxn.setMatchingScore(100);
        bankTxn.setMatchedExpenseId(saved.getId());
        bankTransactionRepository.save(bankTxn);

        // 9. Learn vendor mapping
        learnVendorMapping(bankTxn, vendor);

        log.info("Categorized bankTxn={} → expenseTxn={} vendor={} coa={} amount={}",
                bankTxn.getId(), saved.getId(), vendor.getVendorName(),
                chartOfAccount.getAccountName(), amountLocal);

        // 10. Return DTO
        return mapToDto(saved);
    }

    // ── Private helpers ────────────────────────────────────────

    private Account resolveSourceAccount(EntityBankAccount bankAccount, LegalEntity entity) {
        AccountRole role = mapBankAccountTypeToAccountRole(bankAccount.getAccountType());
        return accountRepository.findFirstByLegalEntityAndAccountRoleAndStatus(
                        entity, role, com.af.novadesk.api.common.constants.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "No active fa_account found for entity " + entity.getId()
                        + " with role " + role + ". Please ensure the entity has a properly configured account."));
    }

    private AccountRole mapBankAccountTypeToAccountRole(BankAccountType type) {
        return switch (type) {
            case CASH -> AccountRole.CASH;
            case OPERATING -> AccountRole.BANK_OPERATING;
            case PAYROLL -> AccountRole.BANK_OPERATING;
            default -> AccountRole.BANK_OPERATING;
        };
    }

    private LedgerEntry buildEntry(UUID journalId, ExpenseTransaction expense, LegalEntity entity,
                                    Account account, LedgerEntrySide side,
                                    BigDecimal amountLocal, String currency, BigDecimal amountUsd,
                                    BigDecimal exchangeRate) {
        return LedgerEntry.builder()
                .journalId(journalId)
                .legalEntity(entity)
                .account(account)
                .entrySide(side)
                .amountLocal(amountLocal)
                .amountUsd(amountUsd)
                .currencyLocal(currency)
                .exchangeRateUsed(exchangeRate)
                .rateDateUsed(expense.getExpenseDate())
                .referenceType("BANK_RECONCILIATION")
                .referenceId(expense.getId())
                .description(expense.getDescription())
                .build();
    }

    private LedgerEntry buildCoaEntry(UUID journalId, ExpenseTransaction expense, LegalEntity entity,
                                       ChartOfAccount coa, LedgerEntrySide side,
                                       BigDecimal amountLocal, String currency, BigDecimal amountUsd,
                                       BigDecimal exchangeRate) {
        return LedgerEntry.builder()
                .journalId(journalId)
                .legalEntity(entity)
                .chartOfAccount(coa)
                .entrySide(side)
                .amountLocal(amountLocal)
                .amountUsd(amountUsd)
                .currencyLocal(currency)
                .exchangeRateUsed(exchangeRate)
                .rateDateUsed(expense.getExpenseDate())
                .referenceType("BANK_RECONCILIATION")
                .referenceId(expense.getId())
                .description(expense.getDescription())
                .build();
    }

    private void learnVendorMapping(BankTransaction bankTxn, Vendor vendor) {
        if (bankTxn.getDescription() == null) return;
        String pattern = derivePattern(bankTxn.getDescription());
        vendorMappingRepository.findByLegalEntityIdAndBankDescriptionPattern(
                        bankTxn.getLegalEntity().getId(), pattern)
                .ifPresentOrElse(vm -> {
                    vm.setMatchCount(vm.getMatchCount() + 1);
                    if (vm.getMatchCount() >= 3) vm.setConfidenceLevel(VendorMappingConfidence.AUTO_LEARNED);
                    vendorMappingRepository.save(vm);
                }, () -> {
                    VendorMapping vm = VendorMapping.builder()
                            .legalEntity(bankTxn.getLegalEntity())
                            .bankDescriptionPattern(pattern)
                            .vendor(vendor)
                            .confidenceLevel(VendorMappingConfidence.USER_CONFIRMED)
                            .matchCount(1)
                            .build();
                    vendorMappingRepository.save(vm);
                });
    }

    private String derivePattern(String description) {
        for (String token : description.trim().split("[\\s\\*]+")) {
            String cleaned = token.replaceAll("[^A-Za-z]", "");
            if (cleaned.length() >= 3) return cleaned.toUpperCase() + "%";
        }
        return description.trim().substring(0, Math.min(5, description.trim().length())).toUpperCase() + "%";
    }

    /**
     * Builds the expense description from categorization request, appending
     * optional department and project metadata so they are not silently dropped.
     */
    private String buildCategorizationDescription(CategorizeTransactionRequest request, BankTransaction bankTxn) {
        StringBuilder sb = new StringBuilder();
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            sb.append(request.getNotes());
        } else {
            sb.append(bankTxn.getDescription());
        }
        if (request.getDepartment() != null && !request.getDepartment().isBlank()) {
            sb.append(" [Dept: ").append(request.getDepartment()).append("]");
        }
        if (request.getProject() != null && !request.getProject().isBlank()) {
            sb.append(" [Project: ").append(request.getProject()).append("]");
        }
        return sb.toString();
    }

    private ExpenseTransactionDto mapToDto(ExpenseTransaction entity) {
        ExpenseTransactionDto dto = new ExpenseTransactionDto();
        dto.setId(entity.getId());
        dto.setLegalEntityId(entity.getLegalEntity().getId());
        dto.setVendorId(entity.getVendor().getId());
        dto.setExpenseDate(entity.getExpenseDate());
        dto.setAmount(entity.getAmount());
        dto.setCurrencyCode(entity.getCurrencyCode());
        dto.setPaymentMethod(entity.getPaymentMethod());
        dto.setDescription(entity.getDescription());
        dto.setTransactionStatus(entity.getTransactionStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setSourceAccountId(entity.getSourceAccount() != null ? entity.getSourceAccount().getId() : null);
        dto.setChartOfAccountId(entity.getChartOfAccount() != null ? entity.getChartOfAccount().getId() : null);
        return dto;
    }

    @Override
    @Transactional
    public List<ExpenseTransactionDto> bulkCategorize(BulkCategorizeRequest request) {
        List<ExpenseTransactionDto> results = new ArrayList<>();

        for (UUID txnId : request.getTransactionIds()) {
            CategorizeTransactionRequest singleRequest = CategorizeTransactionRequest.builder()
                    .vendorId(request.getVendorId())
                    .chartOfAccountId(request.getChartOfAccountId())
                    .notes(request.getNotes())
                    .build();

            try {
                ExpenseTransactionDto dto = categorizeTransaction(txnId, singleRequest);
                results.add(dto);
                log.info("Bulk-categorized bankTxn={}", txnId);
            } catch (Exception e) {
                log.error("Failed to bulk-categorize bankTxn={}: {}", txnId, e.getMessage());
                // Continue with remaining transactions
            }
        }

        log.info("Bulk categorization complete: {} of {} transactions categorized successfully",
                results.size(), request.getTransactionIds().size());
        return results;
    }

    // ── LLR-BNK-03.5: Split Transactions ────────────────────────

    @Override
    @Transactional
    public List<ExpenseTransactionDto> splitTransaction(UUID bankTransactionId, SplitTransactionRequest request) {
        // 1. Load and validate bank transaction
        BankTransaction bankTxn = bankTransactionRepository.findById(bankTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Bank transaction not found: " + bankTransactionId));

        if (bankTxn.getReconciliationStatus() != ReconciliationStatus.UNMATCHED) {
            throw new IllegalStateException(
                    "Bank transaction is not UNMATCHED. Current: " + bankTxn.getReconciliationStatus());
        }

        // 2. Validate SUM(lines.amount) == ABS(bankTxn.amount) within tolerance 0.001
        BigDecimal bankAmount = bankTxn.getAmount().abs();
        BigDecimal splitSum = request.getLines().stream()
                .map(SplitLineItem::getAmount)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        BigDecimal difference = bankAmount.subtract(splitSum).abs();
        if (difference.compareTo(new BigDecimal("0.001")) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Split amounts (%s) do not match bank transaction amount (%s). Difference: %s",
                    splitSum.setScale(4, RoundingMode.HALF_UP),
                    bankAmount.setScale(4, RoundingMode.HALF_UP),
                    difference.setScale(4, RoundingMode.HALF_UP)));
        }

        // 3. Resolve common references
        LegalEntity legalEntity = bankTxn.getLegalEntity();
        EntityBankAccount bankAccount = bankTxn.getBankAccount();
        Account sourceAccount = resolveSourceAccount(bankAccount, legalEntity);
        UUID authUserId = securityContext.getAuthUserId();
        ShadowUser createdBy = shadowUserRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new IllegalArgumentException("Shadow user not found"));

        String currencyCode = legalEntity.getBaseCurrency().trim();
        BigDecimal exchangeRate = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

        List<ExpenseTransactionDto> results = new ArrayList<>();

        // 4. For each split line, create expense transaction + double-entry ledger entries
        for (SplitLineItem line : request.getLines()) {
            Vendor vendor = vendorRepository.findById(line.getVendorId())
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + line.getVendorId()));
            ChartOfAccount chartOfAccount = chartOfAccountRepository.findById(line.getChartOfAccountId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Chart of account not found: " + line.getChartOfAccountId()));

            // Validate COA belongs to same legal entity
            if (!chartOfAccount.getLegalEntity().getId().equals(legalEntity.getId())) {
                throw new IllegalArgumentException(
                        "Chart of account '" + chartOfAccount.getAccountName()
                        + "' does not belong to legal entity " + legalEntity.getId());
            }

            BigDecimal lineAmount = line.getAmount().abs().setScale(4, RoundingMode.HALF_UP);
            BigDecimal lineAmountUsd = lineAmount.multiply(exchangeRate).setScale(4, RoundingMode.HALF_UP);

            String description = request.getNotes() != null && !request.getNotes().isBlank()
                    ? request.getNotes()
                    : bankTxn.getDescription();

            ExpenseTransaction transaction = ExpenseTransaction.builder()
                    .legalEntity(legalEntity)
                    .vendor(vendor)
                    .createdBy(createdBy)
                    .expenseDate(bankTxn.getTransactionDate())
                    .amount(lineAmount)
                    .currencyCode(currencyCode)
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .sourceAccount(sourceAccount)
                    .chartOfAccount(chartOfAccount)
                    .description(description)
                    .transactionStatus(ExpenseTransactionStatus.POSTED)
                    .reconciliationStatus("RECONCILED")
                    .matchedBankTransactionId(bankTxn.getId())
                    .build();

            ExpenseTransaction saved = expenseTransactionRepository.save(transaction);

            // Double-entry ledger: CREDIT source, DEBIT expense category
            UUID journalId = UUID.randomUUID();
            List<LedgerEntry> entries = List.of(
                    buildEntry(journalId, saved, legalEntity, sourceAccount,
                            LedgerEntrySide.CREDIT, lineAmount, currencyCode, lineAmountUsd, exchangeRate),
                    buildCoaEntry(journalId, saved, legalEntity, chartOfAccount,
                            LedgerEntrySide.DEBIT, lineAmount, currencyCode, lineAmountUsd, exchangeRate));
            ledgerEntryRepository.saveAll(entries);

            // Learn vendor mapping for this vendor
            learnVendorMapping(bankTxn, vendor);

            results.add(mapToDto(saved));

            log.info("Split line: vendor={} coa={} amount={} → expenseTxn={}",
                    vendor.getVendorName(), chartOfAccount.getAccountName(), lineAmount, saved.getId());
        }

        // 5. Mark BankTransaction as MATCHED
        bankTxn.setReconciliationStatus(ReconciliationStatus.MATCHED);
        bankTxn.setMatchingMethod(MatchingMethod.MANUAL);
        bankTxn.setMatchingScore(100);
        bankTransactionRepository.save(bankTxn);

        log.info("Split bankTxn={} into {} expense transactions, total={}",
                bankTxn.getId(), results.size(),
                splitSum.setScale(4, RoundingMode.HALF_UP));

        return results;
    }
}
