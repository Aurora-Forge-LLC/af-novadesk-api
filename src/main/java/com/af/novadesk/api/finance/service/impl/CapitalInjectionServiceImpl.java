// ──────────────────────────────────────────────────────────────────────────────
// MIGRATED from financialaccounting → finance.funding  (LLR-FIN-02)
// ──────────────────────────────────────────────────────────────────────────────
package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CapitalInjectionEventType;
import com.af.novadesk.api.finance.constants.FundingSource;
import com.af.novadesk.api.finance.constants.LedgerEntrySide;
import com.af.novadesk.api.finance.constants.Status;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.config.FundingProperties;
import com.af.novadesk.api.finance.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.dto.CapitalInjectionResponse;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.CapitalInjection;
import com.af.novadesk.api.finance.entity.CapitalInjectionOutboxEvent;
import com.af.novadesk.api.finance.entity.LedgerEntry;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.repository.CapitalInjectionOutboxEventRepository;
import com.af.novadesk.api.finance.repository.CapitalInjectionRepository;
import com.af.novadesk.api.finance.repository.LedgerEntryRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.service.CapitalInjectionService;
import com.af.novadesk.api.finance.service.ExchangeRateResolution;
import com.af.novadesk.api.finance.service.ExchangeRateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Orchestrates capital-injection creation per LLR-FIN-02.
 *
 * <h3>Workflow</h3>
 * <ol>
 *   <li>Validate request (date, entity approval, account ownership).</li>
 *   <li>Resolve target and optional source {@link LegalEntity} objects.</li>
 *   <li>Derive local currency from {@code targetEntity.baseCurrency}.</li>
 *   <li>Resolve exchange rate to the reporting currency (USD).</li>
 *   <li>Build and persist {@link CapitalInjection} header record.</li>
 *   <li>Build balanced double-entry {@link LedgerEntry} lines.</li>
 *   <li>Validate debit == credit totals before committing (LLR-FIN-02.2).</li>
 *   <li>Save all entries atomically inside a single {@code @Transactional} boundary.</li>
 *   <li><strong>Write a {@link CapitalInjectionOutboxEvent} in the same transaction</strong>
 *       (Transactional Outbox Pattern) — guarantees at-least-once delivery of the
 *       {@code CAPITAL_INJECTION_CREATED} domain event to downstream consumers
 *       without a distributed transaction.</li>
 * </ol>
 *
 * <h3>Inter-entity transfer (LLR-FIN-02.4)</h3>
 * <p>When {@code fundingSource == INTER_ENTITY_TRANSFER} a shared {@code transferId}
 * is assigned and four ledger legs are created across both entity ledgers.</p>
 *
 * <h3>Transactional Outbox Pattern</h3>
 * <p>The outbox event is persisted within the same ACID transaction as the
 * {@link CapitalInjection} and {@link LedgerEntry} records.  Either all three
 * writes succeed or all three are rolled back — there is no window where the
 * business data is committed but the event is lost.  A separate polling publisher
 * (scheduled job) reads {@code PENDING} rows from
 * {@code af_novadesk_outbox.capital_injection_outbox_events} after commit and
 * delivers them to the message broker.</p>
 */
@Service
@Transactional
public class CapitalInjectionServiceImpl implements CapitalInjectionService {

    private static final String REFERENCE_TYPE = "CAPITAL_INJECTION";

    private final LegalEntityRepository legalEntityRepository;
    private final AccountRepository accountRepository;
    private final CapitalInjectionRepository capitalInjectionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ExchangeRateService exchangeRateService;
    private final FundingProperties fundingProperties;
    private final CapitalInjectionOutboxEventRepository outboxEventRepository;

    public CapitalInjectionServiceImpl(
            LegalEntityRepository legalEntityRepository,
            AccountRepository accountRepository,
            CapitalInjectionRepository capitalInjectionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ExchangeRateService exchangeRateService,
            FundingProperties fundingProperties,
            CapitalInjectionOutboxEventRepository outboxEventRepository
    ) {
        this.legalEntityRepository = legalEntityRepository;
        this.accountRepository = accountRepository;
        this.capitalInjectionRepository = capitalInjectionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.exchangeRateService = exchangeRateService;
        this.fundingProperties = fundingProperties;
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Records a capital injection and the corresponding double-entry ledger lines,
     * then writes a {@link CapitalInjectionOutboxEvent} — all within a single
     * database transaction (Transactional Outbox Pattern).
     */
    @Override
    public CapitalInjectionResponse createCapitalInjection(CapitalInjectionRequest request) {

        // ── 1. Entities ───────────────────────────────────────────────────────
        String targetCode = normalize(request.getTargetEntityCode());
        LegalEntity targetEntity = resolveActiveApprovedEntity(targetCode);
        LegalEntity sourceEntity = resolveSourceEntityIfRequired(request);

        validateFundingDate(request.getFundingDate());

        // ── 2. Accounts ───────────────────────────────────────────────────────
        Account destinationAccount = resolveDestinationAccount(targetEntity, request.getDestinationAccountId());
        Account sourceAccount      = resolveSourceAccount(request, targetEntity, sourceEntity);

        if (sourceAccount.getId().equals(destinationAccount.getId())) {
            throw new BadRequestException("Source and destination accounts must be different");
        }

        // ── 3. Currency & exchange rate ───────────────────────────────────────
        String localCurrency = targetEntity.getBaseCurrency();

        ExchangeRateResolution rateResolution = exchangeRateService.resolveRate(
                localCurrency,
                fundingProperties.getReportingCurrency(),
                request.getFundingDate(),
                request.getManualExchangeRate(),
                request.getManualRateJustification(),
                request.getManualRateApprovedBy()
        );

        BigDecimal amountLocal = scale(request.getAmount());
        BigDecimal amountUsd   = scale(amountLocal.multiply(rateResolution.rate()));

        // ── 4. Header record ──────────────────────────────────────────────────
        UUID transferId = request.getFundingSource() == FundingSource.INTER_ENTITY_TRANSFER
                ? UUID.randomUUID() : null;
        UUID journalId  = UUID.randomUUID();

        CapitalInjection saved = capitalInjectionRepository.save(buildInjection(
                request, targetEntity, sourceEntity,
                sourceAccount, destinationAccount,
                localCurrency, amountLocal, amountUsd, rateResolution, transferId
        ));

        // ── 5. Ledger entries ─────────────────────────────────────────────────
        List<LedgerEntry> entries = request.getFundingSource() == FundingSource.INTER_ENTITY_TRANSFER
                ? buildInterEntityEntries(saved, sourceEntity, sourceAccount, destinationAccount,
                                          localCurrency, amountLocal, amountUsd, rateResolution, journalId)
                : buildStandardEntries(saved, sourceAccount, destinationAccount,
                                       localCurrency, amountLocal, amountUsd, rateResolution, journalId);

        assertBalanced(entries);
        ledgerEntryRepository.saveAll(entries);

        // ── 6. Transactional Outbox event ─────────────────────────────────────
        // Written in the SAME transaction as the CapitalInjection and LedgerEntry
        // records.  The polling publisher will read PENDING rows after commit and
        // deliver them to the message broker, guaranteeing at-least-once delivery
        // without a distributed transaction.
        outboxEventRepository.save(buildOutboxEvent(saved, journalId, targetEntity, sourceEntity));

        // ── 7. Response ───────────────────────────────────────────────────────
        return CapitalInjectionResponse.builder()
                .capitalInjectionId(saved.getId())
                .journalId(journalId)
                .transferId(saved.getTransferId())
                .targetEntityCode(targetEntity.getEntityCode())
                .sourceEntityCode(sourceEntity != null ? sourceEntity.getEntityCode() : null)
                .amountLocal(saved.getAmountLocal())
                .currencyLocal(saved.getCurrencyLocal())
                .amountUsd(saved.getAmountUsd())
                .exchangeRateUsed(saved.getExchangeRateUsed())
                .rateDateUsed(saved.getRateDateUsed())
                .rateSource(saved.getRateSource())
                .message("Capital injection recorded successfully")
                .build();
    }

    // =========================================================================
    // Journal builders
    // =========================================================================

    /**
     * Standard two-leg journal (LLR-FIN-02.2):
     * CREDIT source account → DEBIT destination account.
     */
    private List<LedgerEntry> buildStandardEntries(
            CapitalInjection saved,
            Account sourceAccount,
            Account destinationAccount,
            String localCurrency,
            BigDecimal amountLocal,
            BigDecimal amountUsd,
            ExchangeRateResolution rate,
            UUID journalId
    ) {
        String desc = "Capital injection — " + saved.getFundingSource().name();
        return List.of(
                buildEntry(journalId, null, saved.getTargetEntity(), sourceAccount,
                           LedgerEntrySide.CREDIT, amountLocal, localCurrency, amountUsd, rate, desc, saved.getId()),
                buildEntry(journalId, null, saved.getTargetEntity(), destinationAccount,
                           LedgerEntrySide.DEBIT,  amountLocal, localCurrency, amountUsd, rate, desc, saved.getId())
        );
    }

    /**
     * Four-leg inter-entity journal (LLR-FIN-02.4):
     * <pre>
     * Source:      CREDIT cash  | DEBIT  inter-entity receivable
     * Destination: DEBIT  cash  | CREDIT inter-entity payable
     * </pre>
     */
    private List<LedgerEntry> buildInterEntityEntries(
            CapitalInjection saved,
            LegalEntity sourceEntity,
            Account sourceCashAccount,
            Account destinationCashAccount,
            String localCurrency,
            BigDecimal amountLocal,
            BigDecimal amountUsd,
            ExchangeRateResolution rate,
            UUID journalId
    ) {
        if (sourceEntity == null) {
            throw new BadRequestException("sourceEntityCode is required for inter-entity transfers");
        }

        Account srcReceivable = findAccountByRole(sourceEntity,            AccountRole.INTER_ENTITY_RECEIVABLE);
        Account dstPayable    = findAccountByRole(saved.getTargetEntity(), AccountRole.INTER_ENTITY_PAYABLE);

        List<LedgerEntry> entries = new ArrayList<>();
        UUID tid = saved.getTransferId();

        // Source entity
        entries.add(buildEntry(journalId, tid, sourceEntity, sourceCashAccount,
                               LedgerEntrySide.CREDIT, amountLocal, localCurrency, amountUsd, rate,
                               "Inter-entity transfer out", saved.getId()));
        entries.add(buildEntry(journalId, tid, sourceEntity, srcReceivable,
                               LedgerEntrySide.DEBIT,  amountLocal, localCurrency, amountUsd, rate,
                               "Inter-entity receivable",  saved.getId()));

        // Destination entity
        entries.add(buildEntry(journalId, tid, saved.getTargetEntity(), destinationCashAccount,
                               LedgerEntrySide.DEBIT,  amountLocal, localCurrency, amountUsd, rate,
                               "Inter-entity transfer in", saved.getId()));
        entries.add(buildEntry(journalId, tid, saved.getTargetEntity(), dstPayable,
                               LedgerEntrySide.CREDIT, amountLocal, localCurrency, amountUsd, rate,
                               "Inter-entity payable",    saved.getId()));

        return entries;
    }

    private LedgerEntry buildEntry(
            UUID journalId, UUID transferId,
            LegalEntity legalEntity, Account account,
            LedgerEntrySide side,
            BigDecimal amountLocal, String localCurrency, BigDecimal amountUsd,
            ExchangeRateResolution rate,
            String description, UUID referenceId
    ) {
        return LedgerEntry.builder()
                .journalId(journalId)
                .transferId(transferId)
                .legalEntity(legalEntity)
                .account(account)
                .entrySide(side)
                .amountLocal(amountLocal)
                .currencyLocal(localCurrency)
                .amountUsd(amountUsd)
                .exchangeRateUsed(rate.rate())
                .rateDateUsed(rate.rateDate())
                .description(description)
                .referenceType(REFERENCE_TYPE)
                .referenceId(referenceId)
                .build();
    }

    // =========================================================================
    // Outbox builder
    // =========================================================================

    /**
     * Builds the {@link CapitalInjectionOutboxEvent} that is persisted in the
     * same database transaction as the {@link CapitalInjection} and its
     * {@link LedgerEntry} lines (Transactional Outbox Pattern).
     *
     * <p>Idempotency key convention:
     * {@code "CAPITAL_INJECTION_CREATED:<capitalInjectionId>:<journalId>"}</p>
     */
    private CapitalInjectionOutboxEvent buildOutboxEvent(
            CapitalInjection saved,
            UUID journalId,
            LegalEntity targetEntity,
            LegalEntity sourceEntity
    ) {
        String idempotencyKey = CapitalInjectionEventType.CAPITAL_INJECTION_CREATED
                + ":" + saved.getId()
                + ":" + journalId;

        String payload = buildPayload(saved, journalId, targetEntity, sourceEntity);

        return CapitalInjectionOutboxEvent.builder()
                .capitalInjection(saved)
                .eventType(CapitalInjectionEventType.CAPITAL_INJECTION_CREATED)
                .payload(payload)
                // The target legal entity's ID serves as the organization scope.
                // Replace with a proper organizationId from security context
                // if multi-organisation support is introduced in the future.
                .organizationId(targetEntity.getId())
                .idempotencyKey(idempotencyKey)
                .build();
    }

    /**
     * Builds a minimal JSON payload for the {@code CAPITAL_INJECTION_CREATED} event.
     *
     * <p>Uses manual JSON construction to avoid pulling ObjectMapper into a domain
     * service.  If payload complexity grows, introduce a dedicated
     * {@code CapitalInjectionEventPayload} record and serialize via Jackson.</p>
     */
    private String buildPayload(
            CapitalInjection saved,
            UUID journalId,
            LegalEntity targetEntity,
            LegalEntity sourceEntity
    ) {
        return "{"
                + "\"capitalInjectionId\":\"" + saved.getId() + "\","
                + "\"journalId\":\"" + journalId + "\","
                + "\"transferId\":" + (saved.getTransferId() != null
                        ? "\"" + saved.getTransferId() + "\"" : "null") + ","
                + "\"targetEntityCode\":\"" + targetEntity.getEntityCode() + "\","
                + "\"sourceEntityCode\":" + (sourceEntity != null
                        ? "\"" + sourceEntity.getEntityCode() + "\"" : "null") + ","
                + "\"fundingSource\":\"" + saved.getFundingSource().name() + "\","
                + "\"amountLocal\":" + saved.getAmountLocal() + ","
                + "\"currencyLocal\":\"" + saved.getCurrencyLocal().trim() + "\","
                + "\"amountUsd\":" + saved.getAmountUsd() + ","
                + "\"exchangeRateUsed\":" + saved.getExchangeRateUsed() + ","
                + "\"rateDateUsed\":\"" + saved.getRateDateUsed() + "\","
                + "\"rateSource\":\"" + saved.getRateSource().name() + "\","
                + "\"fundingDate\":\"" + saved.getFundingDate() + "\","
                + "\"createdBy\":" + (saved.getCreatedBy() != null
                        ? "\"" + saved.getCreatedBy() + "\"" : "null")
                + "}";
    }

    // =========================================================================
    // Builders
    // =========================================================================

    private CapitalInjection buildInjection(
            CapitalInjectionRequest request,
            LegalEntity targetEntity, LegalEntity sourceEntity,
            Account sourceAccount, Account destinationAccount,
            String localCurrency, BigDecimal amountLocal, BigDecimal amountUsd,
            ExchangeRateResolution rate, UUID transferId
    ) {
        return CapitalInjection.builder()
                .targetEntity(targetEntity)
                .sourceEntity(sourceEntity)
                .transferId(transferId)
                .fundingSource(request.getFundingSource())
                .fundingDate(request.getFundingDate())
                .amountLocal(amountLocal)
                .currencyLocal(localCurrency)
                .amountUsd(amountUsd)
                .exchangeRateUsed(rate.rate())
                .rateDateUsed(rate.rateDate())
                .rateSource(rate.rateSource())
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                .createdBy(request.getRequestedBy())
                .build();
    }

    // =========================================================================
    // Validation
    // =========================================================================

    private void validateFundingDate(LocalDate fundingDate) {
        if (fundingDate.isAfter(LocalDate.now())) {
            throw new BadRequestException("Funding date cannot be in the future");
        }
    }

    /** Asserts SUM(debits) == SUM(credits) in local currency (LLR-FIN-02.2). */
    private void assertBalanced(List<LedgerEntry> entries) {
        BigDecimal debits  = entries.stream()
                .filter(e -> e.getEntrySide() == LedgerEntrySide.DEBIT)
                .map(LedgerEntry::getAmountLocal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal credits = entries.stream()
                .filter(e -> e.getEntrySide() == LedgerEntrySide.CREDIT)
                .map(LedgerEntry::getAmountLocal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debits.compareTo(credits) != 0) {
            throw new BadRequestException(
                    "Double-entry validation failed: debits (" + debits + ") ≠ credits (" + credits + ")");
        }
    }

    // =========================================================================
    // Resolution helpers
    // =========================================================================

    private LegalEntity resolveActiveApprovedEntity(String entityCode) {
        LegalEntity entity = legalEntityRepository.findByEntityCode(entityCode)
                .orElseThrow(() -> new NotFoundException("Legal entity not found: " + entityCode));

        if (entity.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BadRequestException(
                    "Entity '" + entityCode + "' is not yet approved for financial operations");
        }
        if (entity.getStatus() != Status.ACTIVE) {
            throw new BadRequestException("Entity '" + entityCode + "' is not active");
        }
        return entity;
    }

    private LegalEntity resolveSourceEntityIfRequired(CapitalInjectionRequest request) {
        if (request.getFundingSource() != FundingSource.INTER_ENTITY_TRANSFER) {
            return null;
        }
        if (request.getSourceEntityCode() == null || request.getSourceEntityCode().isBlank()) {
            throw new BadRequestException("sourceEntityCode is required for inter-entity transfers");
        }
        if (request.getSourceAccountId() == null) {
            throw new BadRequestException("sourceAccountId is required for inter-entity transfers");
        }
        return resolveActiveApprovedEntity(normalize(request.getSourceEntityCode()));
    }

    private Account resolveDestinationAccount(LegalEntity targetEntity, UUID destinationAccountId) {
        if (destinationAccountId != null) {
            Account account = accountRepository.findById(destinationAccountId)
                    .orElseThrow(() -> new NotFoundException(
                            "Destination account not found: " + destinationAccountId));
            assertAccountBelongsTo(account, targetEntity, "Destination");
            return account;
        }
        return accountRepository
                .findFirstByLegalEntityAndAccountRoleAndStatus(
                        targetEntity, AccountRole.BANK_OPERATING, Status.ACTIVE)
                .or(() -> accountRepository.findFirstByLegalEntityAndAccountRoleAndStatus(
                        targetEntity, AccountRole.CASH, Status.ACTIVE))
                .orElseThrow(() -> new NotFoundException(
                        "No active BANK_OPERATING or CASH account for entity: "
                                + targetEntity.getEntityCode()));
    }

    private Account resolveSourceAccount(
            CapitalInjectionRequest request,
            LegalEntity targetEntity,
            LegalEntity sourceEntity
    ) {
        if (request.getSourceAccountId() == null) {
            throw new BadRequestException("sourceAccountId is required");
        }
        Account account = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new NotFoundException(
                        "Source account not found: " + request.getSourceAccountId()));

        LegalEntity expectedOwner = (request.getFundingSource() == FundingSource.INTER_ENTITY_TRANSFER)
                ? sourceEntity : targetEntity;
        assertAccountBelongsTo(account, expectedOwner, "Source");
        return account;
    }

    private Account findAccountByRole(LegalEntity entity, AccountRole role) {
        return accountRepository
                .findFirstByLegalEntityAndAccountRoleAndStatus(entity, role, Status.ACTIVE)
                .orElseThrow(() -> new NotFoundException(
                        "Required account role [" + role + "] not found for entity: "
                                + entity.getEntityCode()));
    }

    private void assertAccountBelongsTo(Account account, LegalEntity entity, String label) {
        if (!account.getLegalEntity().getId().equals(entity.getId())) {
            throw new BadRequestException(
                    label + " account does not belong to entity: " + entity.getEntityCode());
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}


