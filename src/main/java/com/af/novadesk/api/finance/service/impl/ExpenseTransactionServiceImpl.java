package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.service.FileStorageService;
import com.af.novadesk.api.finance.config.FundingProperties;
import com.af.novadesk.api.finance.constants.ExpenseTransactionStatus;
import com.af.novadesk.api.finance.constants.LedgerEntrySide;
import com.af.novadesk.api.finance.dto.ExpenseAttachmentDto;
import com.af.novadesk.api.finance.dto.ExpenseLedgerJournalDto;
import com.af.novadesk.api.finance.dto.ExpenseLedgerResponse;
import com.af.novadesk.api.finance.dto.ExpenseTransactionDto;
import com.af.novadesk.api.finance.dto.ExpenseTransactionPageDto;
import com.af.novadesk.api.finance.dto.LedgerEntrySummaryDto;
import com.af.novadesk.api.finance.dto.VoidExpenseDto;
import com.af.novadesk.api.finance.mapper.ExpenseAttachmentMapper;
import com.af.novadesk.api.finance.mapper.ExpenseTransactionMapper;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import com.af.novadesk.api.finance.entity.ExpenseAttachment;
import com.af.novadesk.api.finance.entity.ExpenseTransaction;
import com.af.novadesk.api.finance.entity.LedgerEntry;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.entity.Vendor;
import com.af.novadesk.api.finance.exception.AccountNotFoundException;
import com.af.novadesk.api.finance.exception.AttachmentNotFoundException;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.exception.ExpenseTransactionNotFoundException;
import com.af.novadesk.api.finance.exception.InvalidExpenseStateException;
import com.af.novadesk.api.finance.exception.ShadowUserNotFoundException;
import com.af.novadesk.api.finance.exception.VendorNotFoundException;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.repository.ChartOfAccountRepository;
import com.af.novadesk.api.finance.repository.ExpenseAttachmentRepository;
import com.af.novadesk.api.finance.repository.ExpenseTransactionRepository;
import com.af.novadesk.api.finance.repository.LedgerEntryRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.repository.VendorRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.ExpenseTransactionOutboxService;
import com.af.novadesk.api.finance.service.ExpenseTransactionService;
import com.af.novadesk.api.finance.service.ExchangeRateResolution;
import com.af.novadesk.api.finance.service.ExchangeRateService;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements Manual Expense Recording (LLR-FIN-03).
 *
 * <h3>recordExpense workflow</h3>
 * <ol>
 *   <li>Extract {@code orgId} + {@code authUserId} from the verified JWT.</li>
 *   <li>Resolve {@link ShadowUser} — the author of the expense.</li>
 *   <li>Resolve {@link LegalEntity} by {@code legalEntityId + orgId}.</li>
 *   <li>Resolve {@link Vendor} by {@code vendorId + orgId}.</li>
 *   <li>Resolve source {@link Account} (fa_accounts); assert it belongs to the entity.</li>
 *   <li>Resolve {@link ChartOfAccount} (chart_of_accounts); assert it belongs to the entity.</li>
 *   <li>Derive {@code currencyCode} from {@code legalEntity.baseCurrency}.</li>
 *   <li>Resolve exchange rate for USD conversion.</li>
 *   <li>Persist {@link ExpenseTransaction}.</li>
 *   <li>Post two balanced {@link LedgerEntry} rows (CREDIT source account, DEBIT chart of account).</li>
 *   <li>Publish {@code EXPENSE_CREATED} outbox event — all in one transaction.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseTransactionServiceImpl implements ExpenseTransactionService {

    private static final String  REFERENCE_TYPE    = "EXPENSE";
    private static final long    MAX_FILE_SIZE      = 5_242_880L; // 5 MB
    private static final Set<String> ALLOWED_TYPES  = Set.of("PDF", "PNG", "JPG", "JPEG");

    private final ExpenseTransactionRepository  expenseTransactionRepository;
    private final ExpenseAttachmentRepository   attachmentRepository;
    private final LegalEntityRepository         legalEntityRepository;
    private final VendorRepository              vendorRepository;
    private final AccountRepository             accountRepository;
    private final ChartOfAccountRepository      chartOfAccountRepository;
    private final LedgerEntryRepository         ledgerEntryRepository;
    private final ShadowUserRepository          shadowUserRepository;
    private final ExchangeRateService           exchangeRateService;
    private final FundingProperties             fundingProperties;
    private final ExpenseTransactionOutboxService outboxService;
    private final FileStorageService            fileStorageService;
    private final FinanceSecurityContext        securityContext;
    private final ExpenseTransactionMapper      expenseTransactionMapper;
    private final ExpenseAttachmentMapper       expenseAttachmentMapper;

    // =========================================================================
    // LLR-FIN-03.1 / 03.2: Record expense
    // =========================================================================

    @Override
    @Transactional
    public ExpenseTransactionDto recordExpense(ExpenseTransactionDto request) {

        // ── 1. Caller identity ────────────────────────────────────────────────
        UUID orgId      = securityContext.getOrganizationId();
        UUID authUserId = securityContext.getAuthUserId();

        // ── 2. ShadowUser ─────────────────────────────────────────────────────
        ShadowUser createdBy = shadowUserRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ShadowUserNotFoundException(authUserId));

        // ── 3. Legal entity (org-scoped) ──────────────────────────────────────
        LegalEntity legalEntity = legalEntityRepository
                .findByIdAndOrganizationId(request.getLegalEntityId(), orgId)
                .orElseThrow(() -> new EntityNotFoundException(request.getLegalEntityId()));

        // ── 4. Vendor (org-scoped) ────────────────────────────────────────────
        Vendor vendor = vendorRepository
                .findByIdAndOrganizationId(request.getVendorId(), orgId)
                .orElseThrow(() -> new VendorNotFoundException(request.getVendorId()));

        // ── 5. Source account (fa_accounts) + chart of account (chart_of_accounts) ──
        Account sourceAccount = resolveAccount(request.getSourceAccountId(), "Source");
        assertAccountBelongsTo(sourceAccount, legalEntity, "Source");

        ChartOfAccount chartOfAccount = resolveChartOfAccount(
                request.getChartOfAccountId(), legalEntity);

        // ── 7. Currency — derived from entity, never from request ─────────────
        String currencyCode = legalEntity.getBaseCurrency().trim();

        // ── 8. Exchange rate for USD conversion ───────────────────────────────
        // Manual rate fields are optional. If provided, all three must be present.
        BigDecimal manualRate          = request.getManualExchangeRate();
        String     manualJustification = request.getManualRateJustification();
        String     manualApprovedBy    = request.getManualRateApprovedBy();

        if (manualJustification != null && !manualJustification.isBlank() && manualRate == null) {
            throw new BadRequestException(
                    "manualExchangeRate is required when manualRateJustification is provided");
        }

        if (manualRate != null) {
            if (manualJustification == null || manualJustification.isBlank()) {
                throw new BadRequestException(
                        "manualRateJustification is required when manualExchangeRate is provided");
            }
            // Always resolve the approver name from the authenticated user's profile.
            // Display name is preferred (e.g. "Jane Smith"); email is the fallback.
            manualApprovedBy = createdBy.getDisplayName() != null && !createdBy.getDisplayName().isBlank()
                    ? createdBy.getDisplayName()
                    : createdBy.getEmail();
        }

        ExchangeRateResolution rate = exchangeRateService.resolveRate(
                currencyCode,
                fundingProperties.reportingCurrency(),
                request.getExpenseDate(),
                manualRate,
                manualJustification,
                manualApprovedBy
        );

        BigDecimal amountLocal = request.getAmount().setScale(4, RoundingMode.HALF_UP);
        BigDecimal amountUsd   = amountLocal.multiply(rate.rate()).setScale(4, RoundingMode.HALF_UP);

        // ── 9. Save transaction ───────────────────────────────────────────────
        ExpenseTransaction transaction = ExpenseTransaction.builder()
                .legalEntity(legalEntity)
                .vendor(vendor)
                .createdBy(createdBy)
                .expenseDate(request.getExpenseDate())
                .amount(amountLocal)
                .currencyCode(currencyCode)
                .paymentMethod(request.getPaymentMethod())
                .sourceAccount(sourceAccount)
                .chartOfAccount(chartOfAccount)
                .invoiceReceiptNumber(request.getInvoiceReceiptNumber())
                .description(request.getDescription())
                .manualExchangeRate(manualRate)
                .manualRateJustification(manualJustification)
                .manualRateApprovedBy(manualApprovedBy)
                .transactionStatus(ExpenseTransactionStatus.POSTED)
                .build();

        ExpenseTransaction saved = expenseTransactionRepository.save(transaction);

        // ── 10. Double-entry ledger entries ───────────────────────────────────
        UUID journalId = UUID.randomUUID();

        List<LedgerEntry> entries = List.of(
                buildEntry(journalId, saved, legalEntity, sourceAccount,
                        LedgerEntrySide.CREDIT, amountLocal, currencyCode, amountUsd, rate),
                buildCoaEntry(journalId, saved, legalEntity, chartOfAccount,
                        LedgerEntrySide.DEBIT,  amountLocal, currencyCode, amountUsd, rate)
        );
        ledgerEntryRepository.saveAll(entries);

        // ── 11. Outbox event ──────────────────────────────────────────────────
        outboxService.publishExpenseCreated(saved, journalId, orgId, authUserId);

        log.info("Recorded expense id={} amount={} {} for entity={} vendor={}",
                saved.getId(), amountLocal, currencyCode,
                legalEntity.getEntityCode(), vendor.getVendorName());

        return expenseTransactionMapper.toDto(saved);
    }

    // =========================================================================
    // LLR-FIN-03: List and get
    // =========================================================================

    @Override
    public ExpenseTransactionPageDto listExpenses(int page, int size, String sortBy, String status, UUID legalEntityId) {
        UUID orgId = securityContext.getOrganizationId();
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, toEntityField(sortBy)));

        Page<ExpenseTransaction> txPage;

        boolean hasEntity = legalEntityId != null;
        boolean hasStatus = status != null && !status.isBlank();

        if (hasEntity && hasStatus) {
            ExpenseTransactionStatus txStatus = parseStatus(status);
            txPage = expenseTransactionRepository
                    .findAllByOrganizationIdAndLegalEntityIdAndTransactionStatus(orgId, legalEntityId, txStatus, pageRequest);
        } else if (hasEntity) {
            txPage = expenseTransactionRepository
                    .findAllByOrganizationIdAndLegalEntityId(orgId, legalEntityId, pageRequest);
        } else if (hasStatus) {
            ExpenseTransactionStatus txStatus = parseStatus(status);
            txPage = expenseTransactionRepository
                    .findAllByOrganizationIdAndTransactionStatus(orgId, txStatus, pageRequest);
        } else {
            txPage = expenseTransactionRepository.findAllByOrganizationId(orgId, pageRequest);
        }

        List<ExpenseTransactionDto> content = txPage.getContent().stream()
                .map(expenseTransactionMapper::toDto)
                .collect(Collectors.toList());

        return new ExpenseTransactionPageDto(
                content,
                txPage.getNumber(),
                txPage.getSize(),
                txPage.getTotalElements(),
                txPage.getTotalPages()
        );
    }

    @Override
    public ExpenseTransactionDto getExpense(UUID id) {
        UUID orgId = securityContext.getOrganizationId();
        ExpenseTransaction transaction = expenseTransactionRepository
                .findWithRelationsById(id)
                .filter(t -> t.getLegalEntity().getOrganizationId().equals(orgId))
                .orElseThrow(() -> new ExpenseTransactionNotFoundException(id));
        return expenseTransactionMapper.toDto(transaction);
    }

    // =========================================================================
    // LLR-FIN-03: Ledger view
    // =========================================================================

    @Override
    public ExpenseLedgerResponse getExpenseLedger(UUID id) {
        UUID orgId = securityContext.getOrganizationId();

        // Verify the transaction exists and belongs to this org.
        ExpenseTransaction transaction = requireTransactionInOrg(id, orgId);

        // Fetch all ledger entries for this expense in chronological order.
        List<LedgerEntry> allEntries = ledgerEntryRepository
                .findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(REFERENCE_TYPE, id);

        // Group by journalId (insertion-order preserved — first = ORIGINAL, second = VOID_REVERSAL).
        Map<UUID, List<LedgerEntry>> byJournal = new LinkedHashMap<>();
        for (LedgerEntry entry : allEntries) {
            byJournal.computeIfAbsent(entry.getJournalId(), k -> new ArrayList<>()).add(entry);
        }

        // Map each journal to a DTO.  First journal is always ORIGINAL; any subsequent ones are VOID_REVERSAL.
        boolean first = true;
        List<ExpenseLedgerJournalDto> journals = new ArrayList<>();
        for (Map.Entry<UUID, List<LedgerEntry>> journalGroup : byJournal.entrySet()) {
            String journalType = first ? "ORIGINAL" : "VOID_REVERSAL";
            first = false;

            List<LedgerEntrySummaryDto> entrySummaries = journalGroup.getValue().stream()
                    .map(e -> {
                        // CREDIT entries use fa_account; DEBIT entries use chart_of_account
                        UUID accountId     = e.getAccount() != null
                                ? e.getAccount().getId()
                                : e.getChartOfAccount().getId();
                        String accountName = e.getAccount() != null
                                ? e.getAccount().getAccountName()
                                : e.getChartOfAccount().getAccountName();
                        String accountCode = e.getAccount() != null
                                ? e.getAccount().getAccountCode()
                                : e.getChartOfAccount().getAccountCode();
                        return new LedgerEntrySummaryDto(
                            e.getId(),
                            accountId,
                            accountName,
                            accountCode,
                            e.getEntrySide(),
                            e.getAmountLocal(),
                            e.getCurrencyLocal(),
                            e.getAmountUsd(),
                            e.getExchangeRateUsed(),
                            e.getRateDateUsed(),
                            e.getRateWarning() != null && e.getRateWarning(),
                            e.getDescription()
                        );
                    })
                    .collect(Collectors.toList());

            journals.add(new ExpenseLedgerJournalDto(journalGroup.getKey(), journalType, entrySummaries));
        }

        return new ExpenseLedgerResponse(id, transaction.getTransactionStatus(), journals);
    }

    // =========================================================================
    // LLR-FIN-03: Void
    // =========================================================================

    @Override
    @Transactional
    public ExpenseTransactionDto voidExpense(UUID id, VoidExpenseDto request) {
        UUID orgId      = securityContext.getOrganizationId();
        UUID authUserId = securityContext.getAuthUserId();

        ExpenseTransaction transaction = requireTransactionInOrg(id, orgId);

        if (transaction.getTransactionStatus() == ExpenseTransactionStatus.VOID) {
            throw new InvalidExpenseStateException(id, "VOID", "void");
        }

        // ── Reversal entries (swap sides) ─────────────────────────────────────
        String currencyCode = transaction.getCurrencyCode().trim();
        ExchangeRateResolution rate = exchangeRateService.resolveRate(
                currencyCode,
                fundingProperties.reportingCurrency(),
                LocalDate.now(),
                null, null, null
        );

        BigDecimal amountLocal = transaction.getAmount();
        BigDecimal amountUsd   = amountLocal.multiply(rate.rate()).setScale(4, RoundingMode.HALF_UP);

        UUID journalId = UUID.randomUUID();
        List<LedgerEntry> reversals = List.of(
                buildEntry(journalId, transaction, transaction.getLegalEntity(),
                        transaction.getSourceAccount(),
                        LedgerEntrySide.DEBIT,  amountLocal, currencyCode, amountUsd, rate),
                buildCoaEntry(journalId, transaction, transaction.getLegalEntity(),
                        transaction.getChartOfAccount(),
                        LedgerEntrySide.CREDIT, amountLocal, currencyCode, amountUsd, rate)
        );
        ledgerEntryRepository.saveAll(reversals);

        transaction.setTransactionStatus(ExpenseTransactionStatus.VOID);
        // saveAndFlush forces @PreUpdate / @LastModifiedDate to fire before toDto()
        // reads updatedAt — prevents returning a stale timestamp in the response.
        ExpenseTransaction saved = expenseTransactionRepository.saveAndFlush(transaction);

        outboxService.publishExpenseVoided(saved, request.getVoidReason(), orgId, authUserId);

        log.info("Voided expense id={}, reason='{}'", saved.getId(), request.getVoidReason());

        return expenseTransactionMapper.toDto(saved);
    }

    // =========================================================================
    // LLR-FIN-03.4: Attachments
    // =========================================================================

    @Override
    @Transactional
    public ExpenseAttachmentDto uploadAttachment(UUID transactionId, MultipartFile file) {
        UUID orgId      = securityContext.getOrganizationId();
        UUID authUserId = securityContext.getAuthUserId();

        ExpenseTransaction transaction = requireTransactionInOrg(transactionId, orgId);

        if (transaction.getTransactionStatus() == ExpenseTransactionStatus.VOID) {
            throw new BadRequestException("Cannot attach files to a VOID expense transaction");
        }

        // ── Validate file ─────────────────────────────────────────────────────
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown";
        String extension    = extractExtension(originalName).toUpperCase();

        if (!ALLOWED_TYPES.contains(extension)) {
            throw new BadRequestException(
                    "Unsupported file type '" + extension + "'. Allowed: PDF, PNG, JPG, JPEG");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException(
                    "File size " + file.getSize() + " bytes exceeds the 5 MB limit");
        }

        // ── Build storage key ─────────────────────────────────────────────────
        // Convention: <orgId>/<entityId>/<transactionId>/<uuid>.<ext>
        String storageKey = orgId + "/"
                + transaction.getLegalEntity().getId() + "/"
                + transactionId + "/"
                + UUID.randomUUID() + "." + extension.toLowerCase();

        // ── Upload to MinIO / S3 ──────────────────────────────────────────────
        try {
            fileStorageService.upload(storageKey, file.getInputStream(),
                    file.getSize(), file.getContentType());
        } catch (Exception ex) {
            throw new BadRequestException("File upload failed: " + ex.getMessage());
        }

        // ── Resolve ShadowUser ────────────────────────────────────────────────
        ShadowUser uploadedBy = shadowUserRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ShadowUserNotFoundException(authUserId));

        // ── Persist metadata ──────────────────────────────────────────────────
        ExpenseAttachment attachment = ExpenseAttachment.builder()
                .expenseTransaction(transaction)
                .originalFileName(originalName)
                .fileType(extension)
                .fileSizeBytes((int) file.getSize())
                .storageKey(storageKey)
                .isEncrypted(true)
                .uploadedBy(uploadedBy)
                .build();

        ExpenseAttachment saved = attachmentRepository.save(attachment);
        log.info("Uploaded attachment id={} for transaction={}", saved.getId(), transactionId);

        return expenseAttachmentMapper.toDto(saved);
    }

    @Override
    public List<ExpenseAttachmentDto> listAttachments(UUID transactionId) {
        UUID orgId = securityContext.getOrganizationId();
        requireTransactionInOrg(transactionId, orgId); // existence + org check

        return attachmentRepository.findAllByExpenseTransactionId(transactionId)
                .stream()
                .map(expenseAttachmentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAttachment(UUID transactionId, UUID attachmentId) {
        UUID orgId = securityContext.getOrganizationId();
        ExpenseTransaction transaction = requireTransactionInOrg(transactionId, orgId);

        if (transaction.getTransactionStatus() == ExpenseTransactionStatus.VOID) {
            throw new BadRequestException("Cannot delete attachments from a VOID expense transaction");
        }

        ExpenseAttachment attachment = attachmentRepository
                .findByIdAndExpenseTransactionId(attachmentId, transactionId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId, transactionId));

        fileStorageService.delete(attachment.getStorageKey());
        attachmentRepository.delete(attachment);
        log.info("Deleted attachment id={} from transaction={}", attachmentId, transactionId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ExpenseTransaction requireTransactionInOrg(UUID id, UUID orgId) {
        return expenseTransactionRepository
                .findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ExpenseTransactionNotFoundException(id));
    }

    private Account resolveAccount(UUID accountId, String label) {
        return accountRepository.findWithLegalEntityById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        label + " account not found: " + accountId));
    }

    private void assertAccountBelongsTo(Account account, LegalEntity entity, String label) {
        if (!account.getLegalEntity().getId().equals(entity.getId())) {
            throw new BadRequestException(
                    label + " account does not belong to legal entity: " + entity.getEntityCode());
        }
    }

    private LedgerEntry buildEntry(
            UUID journalId,
            ExpenseTransaction transaction,
            LegalEntity legalEntity,
            Account account,
            LedgerEntrySide side,
            BigDecimal amountLocal,
            String currencyCode,
            BigDecimal amountUsd,
            ExchangeRateResolution rate
    ) {
        return LedgerEntry.builder()
                .journalId(journalId)
                .legalEntity(legalEntity)
                .account(account)
                .entrySide(side)
                .amountLocal(amountLocal)
                .currencyLocal(currencyCode)
                .amountUsd(amountUsd)
                .exchangeRateUsed(rate.rate())
                .rateDateUsed(rate.rateDate())
                .description(transaction.getDescription())
                .referenceType(REFERENCE_TYPE)
                .referenceId(transaction.getId())
                .build();
    }

    /** Builds a ledger entry backed by a {@link ChartOfAccount} (expense DEBIT / void CREDIT). */
    private LedgerEntry buildCoaEntry(
            UUID journalId,
            ExpenseTransaction transaction,
            LegalEntity legalEntity,
            ChartOfAccount chartOfAccount,
            LedgerEntrySide side,
            BigDecimal amountLocal,
            String currencyCode,
            BigDecimal amountUsd,
            ExchangeRateResolution rate
    ) {
        return LedgerEntry.builder()
                .journalId(journalId)
                .legalEntity(legalEntity)
                .chartOfAccount(chartOfAccount)
                .entrySide(side)
                .amountLocal(amountLocal)
                .currencyLocal(currencyCode)
                .amountUsd(amountUsd)
                .exchangeRateUsed(rate.rate())
                .rateDateUsed(rate.rateDate())
                .description(transaction.getDescription())
                .referenceType(REFERENCE_TYPE)
                .referenceId(transaction.getId())
                .build();
    }

    /** Resolves a {@link ChartOfAccount} by ID, validates it belongs to the entity. */
    private ChartOfAccount resolveChartOfAccount(UUID chartOfAccountId, LegalEntity entity) {
        ChartOfAccount coa = chartOfAccountRepository.findById(chartOfAccountId)
                .orElseThrow(() -> new BadRequestException(
                        "Chart of account not found: " + chartOfAccountId));
        if (!coa.getLegalEntity().getId().equals(entity.getId())) {
            throw new BadRequestException(
                    "Chart of account does not belong to legal entity: " + entity.getEntityCode());
        }
        return coa;
    }

    private ExpenseTransactionStatus parseStatus(String status) {
        try {
            return ExpenseTransactionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "Invalid transaction status '" + status + "'. Allowed values: POSTED, VOID");
        }
    }

    private static String toEntityField(String sortBy) {
        return switch (sortBy == null ? "expenseDate" : sortBy) {
            case "amount"      -> "amount";
            case "createdAt"   -> "createdAt";
            case "vendorId"    -> "vendor.id";
            default            -> "expenseDate";
        };
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1)
                : "";
    }

}
