// ──────────────────────────────────────────────────────────────────────────────
// MIGRATED from financialaccounting → finance.funding  (LLR-FIN-02)
// ──────────────────────────────────────────────────────────────────────────────
package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.config.FundingProperties;
import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CapitalInjectionStatus;
import com.af.novadesk.api.finance.constants.FundingSource;
import com.af.novadesk.api.finance.constants.LedgerEntrySide;
import com.af.novadesk.api.finance.dto.CapitalInjectionDetailDto;
import com.af.novadesk.api.finance.dto.CapitalInjectionPageDto;
import com.af.novadesk.api.finance.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.dto.CapitalInjectionResponse;
import com.af.novadesk.api.finance.dto.CapitalInjectionStatusRequest;
import com.af.novadesk.api.finance.dto.CapitalInjectionSummaryDto;
import com.af.novadesk.api.finance.dto.InterEntityTransferDto;
import com.af.novadesk.api.finance.dto.LedgerEntrySummaryDto;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.CapitalInjection;
import com.af.novadesk.api.finance.entity.LedgerEntry;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.AuthenticationRequiredException;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.CapitalInjectionNotFoundException;
import com.af.novadesk.api.finance.exception.EntityNotApprovedException;
import com.af.novadesk.api.finance.exception.InvalidAccountStateException;
import com.af.novadesk.api.finance.exception.UnbalancedLedgerException;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.repository.CapitalInjectionRepository;
import com.af.novadesk.api.finance.repository.LedgerEntryRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.CapitalInjectionOutboxService;
import com.af.novadesk.api.finance.service.CapitalInjectionService;
import com.af.novadesk.api.finance.service.ExchangeRateResolution;
import com.af.novadesk.api.finance.service.ExchangeRateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * Orchestrates capital-injection creation per LLR-FIN-02.
 *
 * <h3>Workflow</h3>
 * <ol>
 *   <li>Validate request date — O(1) guard before any I/O (M3).</li>
 *   <li>Resolve target and optional source {@link LegalEntity} objects.</li>
 *   <li>Resolve accounts and validate source-account role (Fix #9).</li>
 *   <li>Resolve exchange rate(s) — one per entity when currencies differ (Fix #4).</li>
 *   <li>Build and persist {@link CapitalInjection} header record.</li>
 *   <li>Build balanced double-entry {@link LedgerEntry} lines.</li>
 *   <li>Assert debit == credit in both local currency and USD (Fix #5).</li>
 *   <li>Save all entries atomically inside a single {@code @Transactional} boundary.</li>
 *   <li>Write a {@link com.af.novadesk.api.finance.entity.CapitalInjectionOutboxEvent}
 *       via {@link CapitalInjectionOutboxService} in the same transaction
 *       (Transactional Outbox Pattern).</li>
 * </ol>
 *
 * <h3>Security</h3>
 * <p>The {@code createdBy} audit field is resolved exclusively from the
 * {@link SecurityContextHolder} — never from the request body (Fix #1).</p>
 *
 * <h3>Inter-entity transfer (LLR-FIN-02.4)</h3>
 * <p>When {@code fundingSource == INTER_ENTITY_TRANSFER} a shared {@code transferId}
 * is assigned and four ledger legs are created across both entity ledgers.  When
 * source and target entities have different base currencies, each pair of legs is
 * denominated in its own entity's currency with an independently-resolved FX
 * rate (Fix #4).</p>
 */
@Service
@Transactional
public class CapitalInjectionServiceImpl implements CapitalInjectionService {

    private static final String REFERENCE_TYPE = "CAPITAL_INJECTION";

    /**
     * FundingSource → expected source AccountRole mapping (Fix #9).
     * INTER_ENTITY_TRANSFER uses the source entity's CASH account and is
     * handled separately by {@link #buildInterEntityEntries}.
     */
    private static final Map<FundingSource, AccountRole> EXPECTED_SOURCE_ROLE = Map.of(
            FundingSource.FOUNDER_EQUITY, AccountRole.FOUNDER_EQUITY,
            FundingSource.LOAN,           AccountRole.LOAN_PAYABLE,
            FundingSource.GRANT,          AccountRole.GRANT_INCOME
    );

    private final LegalEntityRepository    legalEntityRepository;
    private final AccountRepository        accountRepository;
    private final CapitalInjectionRepository  capitalInjectionRepository;
    private final LedgerEntryRepository    ledgerEntryRepository;
    private final ExchangeRateService      exchangeRateService;
    private final FundingProperties        fundingProperties;
    private final CapitalInjectionOutboxService outboxService;
    private final Clock                    clock;
    private final FinanceSecurityContext   securityContext;
    private final EntityAccessGuard        entityAccessGuard;

    public CapitalInjectionServiceImpl(
            LegalEntityRepository legalEntityRepository,
            AccountRepository accountRepository,
            CapitalInjectionRepository capitalInjectionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ExchangeRateService exchangeRateService,
            FundingProperties fundingProperties,
            CapitalInjectionOutboxService outboxService,
            Clock clock,
            FinanceSecurityContext securityContext,
            EntityAccessGuard entityAccessGuard
    ) {
        this.legalEntityRepository      = legalEntityRepository;
        this.accountRepository          = accountRepository;
        this.capitalInjectionRepository = capitalInjectionRepository;
        this.ledgerEntryRepository      = ledgerEntryRepository;
        this.exchangeRateService        = exchangeRateService;
        this.fundingProperties          = fundingProperties;
        this.outboxService              = outboxService;
        this.clock                      = clock;
        this.securityContext            = securityContext;
        this.entityAccessGuard          = entityAccessGuard;
    }

    /**
     * Records a capital injection and the corresponding double-entry ledger lines,
     * then writes a {@link com.af.novadesk.api.finance.entity.CapitalInjectionOutboxEvent}
     * via {@link CapitalInjectionOutboxService} — all within a single
     * database transaction (Transactional Outbox Pattern).
     */
    @Override
    public CapitalInjectionResponse createCapitalInjection(CapitalInjectionRequest request) {

        // ── 1. Date guard — O(1), must precede any DB I/O (M3) ───────────────
        validateFundingDate(request.getFundingDate());

        // ── 2. Resolve caller identity from security context (Fix #1) ─────────
        String callerIdentity = resolveCallerIdentity();

        // ── 2a. Early inter-entity input guard — fast-fail before any DB I/O ──
        if (request.getFundingSource() == FundingSource.INTER_ENTITY_TRANSFER) {
            if (request.getSourceEntityCode() == null || request.getSourceEntityCode().isBlank()) {
                throw new BadRequestException("sourceEntityCode is required for inter-entity transfers");
            }
        }

        // ── 3. Entities ───────────────────────────────────────────────────────
        String targetCode   = normalize(request.getTargetEntityCode());
        LegalEntity targetEntity = resolveActiveApprovedEntity(targetCode);
        LegalEntity sourceEntity = resolveSourceEntityIfRequired(request);

        if (sourceEntity != null && sourceEntity.getId().equals(targetEntity.getId())) {
            throw new BadRequestException(ApiMessages.SAME_ENTITY_TRANSFER);
        }

        // ── 3a. Entity-scope guard ────────────────────────────────────────────
        // Funding is recorded against the target entity, so the caller must be
        // able to act on it. For an inter-entity transfer, funds also leave the
        // source entity, so authority over the source is required too. Org-wide
        // roles bypass both checks (handled inside the guard).
        entityAccessGuard.assertCanAccessEntity(targetEntity.getId());
        if (sourceEntity != null) {
            entityAccessGuard.assertCanAccessEntity(sourceEntity.getId());
        }

        // ── 4. Accounts ───────────────────────────────────────────────────────
        Account destinationAccount = resolveDestinationAccount(targetEntity, request.getDestinationAccountId());
        Account sourceAccount      = resolveSourceAccount(request, targetEntity, sourceEntity);

        if (sourceAccount.getId().equals(destinationAccount.getId())) {
            throw new InvalidAccountStateException("Source and destination accounts must be different");
        }

        // ── 5. Currency & exchange rate (target entity) ───────────────────────
        String targetLocalCurrency = targetEntity.getBaseCurrency();

        ExchangeRateResolution targetRateResolution = exchangeRateService.resolveRate(
                targetLocalCurrency,
                fundingProperties.reportingCurrency(),
                request.getFundingDate(),
                request.getManualExchangeRate(),
                request.getManualRateJustification(),
                request.getManualRateApprovedBy()
        );

        BigDecimal targetAmountLocal = scale(request.getAmount());
        BigDecimal amountUsd         = scale(targetAmountLocal.multiply(targetRateResolution.rate()));

        // ── 6. Source-entity FX — resolved independently when currencies differ
        //      (Fix #4: inter-entity multi-currency defect) ────────────────────
        String sourceLocalCurrency        = targetLocalCurrency;
        BigDecimal sourceAmountLocal      = targetAmountLocal;
        ExchangeRateResolution sourceRate = targetRateResolution;

        if (request.getFundingSource() == FundingSource.INTER_ENTITY_TRANSFER && sourceEntity != null) {
            sourceLocalCurrency = sourceEntity.getBaseCurrency();
            if (!sourceLocalCurrency.equalsIgnoreCase(targetLocalCurrency)) {
                sourceRate = exchangeRateService.resolveRate(
                        sourceLocalCurrency,
                        fundingProperties.reportingCurrency(),
                        request.getFundingDate(),
                        null, null, null
                );
                // Convert the shared USD value back to the source entity's currency
                sourceAmountLocal = scale(
                        amountUsd.divide(sourceRate.rate(), 4, RoundingMode.HALF_UP));
            }
        }

        // ── 7. Header record ──────────────────────────────────────────────────
        UUID transferId = request.getFundingSource() == FundingSource.INTER_ENTITY_TRANSFER
                ? UUID.randomUUID() : null;
        UUID journalId  = UUID.randomUUID();

        CapitalInjection saved = capitalInjectionRepository.save(buildInjection(
                request, targetEntity, sourceEntity,
                sourceAccount, destinationAccount,
                targetLocalCurrency, targetAmountLocal, amountUsd,
                targetRateResolution, transferId, callerIdentity
        ));

        // ── 8. Ledger entries ─────────────────────────────────────────────────
        List<LedgerEntry> entries = request.getFundingSource() == FundingSource.INTER_ENTITY_TRANSFER
                ? buildInterEntityEntries(saved, sourceEntity, sourceAccount, destinationAccount,
                        targetLocalCurrency, targetAmountLocal, amountUsd, targetRateResolution,
                        sourceLocalCurrency, sourceAmountLocal, sourceRate, journalId)
                : buildStandardEntries(saved, sourceAccount, destinationAccount,
                        targetLocalCurrency, targetAmountLocal, amountUsd, targetRateResolution, journalId);

        assertBalanced(entries);
        ledgerEntryRepository.saveAll(entries);

        // ── 9. Transactional Outbox event (LLR-FIN-02) ────────────────────────
        outboxService.publishCapitalInjectionCreated(
                saved, journalId, targetEntity, sourceEntity, callerIdentity);

        // ── 10. Response ──────────────────────────────────────────────────────
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
                .rateWarning(saved.getRateWarning())
                .message(ApiMessages.CAPITAL_INJECTION_SUCCESS)
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
     * Four-leg inter-entity journal (LLR-FIN-02.4).
     *
     * <p>Fix #4: When source and target entities operate in different base
     * currencies, the two legs posted against the source entity are denominated
     * in the source entity's currency ({@code sourceLocalCurrency /
     * sourceAmountLocal}) rather than the target-entity amounts, preventing
     * a mismatched ledger on the source side.</p>
     *
     * <pre>
     * Source entity:      CREDIT cash          | DEBIT  inter-entity receivable
     * Destination entity: DEBIT  cash          | CREDIT inter-entity payable
     * </pre>
     */
    private List<LedgerEntry> buildInterEntityEntries(
            CapitalInjection saved,
            LegalEntity sourceEntity,
            Account sourceCashAccount,
            Account destinationCashAccount,
            // Target entity amounts
            String targetLocalCurrency,
            BigDecimal targetAmountLocal,
            BigDecimal amountUsd,
            ExchangeRateResolution targetRate,
            // Source entity amounts (independent when currencies differ — Fix #4)
            String sourceLocalCurrency,
            BigDecimal sourceAmountLocal,
            ExchangeRateResolution sourceRate,
            UUID journalId
    ) {
        if (sourceEntity == null) {
            throw new BadRequestException("sourceEntityCode is required for inter-entity transfers");
        }

        Account srcReceivable = findAccountByRole(sourceEntity,            AccountRole.INTER_ENTITY_RECEIVABLE);
        Account dstPayable    = findAccountByRole(saved.getTargetEntity(), AccountRole.INTER_ENTITY_PAYABLE);

        List<LedgerEntry> entries = new ArrayList<>();
        UUID tid = saved.getTransferId();

        // Source entity legs — denominated in source entity's base currency
        entries.add(buildEntry(journalId, tid, sourceEntity, sourceCashAccount,
                               LedgerEntrySide.CREDIT, sourceAmountLocal, sourceLocalCurrency, amountUsd,
                               sourceRate, "Inter-entity transfer out", saved.getId()));
        entries.add(buildEntry(journalId, tid, sourceEntity, srcReceivable,
                               LedgerEntrySide.DEBIT,  sourceAmountLocal, sourceLocalCurrency, amountUsd,
                               sourceRate, "Inter-entity receivable",   saved.getId()));

        // Destination entity legs — denominated in target entity's base currency
        entries.add(buildEntry(journalId, tid, saved.getTargetEntity(), destinationCashAccount,
                               LedgerEntrySide.DEBIT,  targetAmountLocal, targetLocalCurrency, amountUsd,
                               targetRate, "Inter-entity transfer in",  saved.getId()));
        entries.add(buildEntry(journalId, tid, saved.getTargetEntity(), dstPayable,
                               LedgerEntrySide.CREDIT, targetAmountLocal, targetLocalCurrency, amountUsd,
                               targetRate, "Inter-entity payable",      saved.getId()));

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
                .rateWarning(rate.warning())
                .description(description)
                .referenceType(REFERENCE_TYPE)
                .referenceId(referenceId)
                .build();
    }

    // =========================================================================
    // Builders
    // =========================================================================

    private CapitalInjection buildInjection(
            CapitalInjectionRequest request,
            LegalEntity targetEntity, LegalEntity sourceEntity,
            Account sourceAccount, Account destinationAccount,
            String localCurrency, BigDecimal amountLocal, BigDecimal amountUsd,
            ExchangeRateResolution rate, UUID transferId,
            String callerIdentity
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
                .rateWarning(rate.warning())
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                // Fix #1: pull identity from security context — never from request body
                .createdBy(callerIdentity)
                .build();
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Rejects future funding dates.
     *
     * <p>Uses {@link ZoneOffset#UTC} for deterministic date evaluation (M2)
     * and a {@link Clock} that can be overridden in tests.</p>
     */
    private void validateFundingDate(LocalDate fundingDate) {
        if (fundingDate.isAfter(LocalDate.now(clock.withZone(ZoneOffset.UTC)))) {
            throw new BadRequestException("Funding date cannot be in the future");
        }
    }

    /**
     * Asserts SUM(debits) == SUM(credits) in both local currency and USD.
     *
     * <p>Fix #5: adding the parallel USD balance check catches cross-currency
     * discrepancies that would silently corrupt consolidated financial reporting
     * if only the local-amount check were present.</p>
     */
    private void assertBalanced(List<LedgerEntry> entries) {
        // Assert all USD amounts are non-negative
        entries.stream()
                .filter(e -> e.getAmountUsd().signum() < 0)
                .findFirst()
                .ifPresent(ignored -> { throw new BadRequestException(
                        "Ledger entry amountUsd must be non-negative"); });

        BigDecimal localDebits  = sum(entries, LedgerEntrySide.DEBIT,  LedgerEntry::getAmountLocal);
        BigDecimal localCredits = sum(entries, LedgerEntrySide.CREDIT, LedgerEntry::getAmountLocal);
        if (localDebits.compareTo(localCredits) != 0) {
            throw new UnbalancedLedgerException(
                    "Double-entry validation failed (local): debits ("
                    + localDebits + ") ≠ credits (" + localCredits + ")");
        }

        // Fix #5: parallel USD balance assertion
        BigDecimal usdDebits  = sum(entries, LedgerEntrySide.DEBIT,  LedgerEntry::getAmountUsd);
        BigDecimal usdCredits = sum(entries, LedgerEntrySide.CREDIT, LedgerEntry::getAmountUsd);
        if (usdDebits.compareTo(usdCredits) != 0) {
            throw new UnbalancedLedgerException(
                    "Double-entry validation failed (USD): debits ("
                    + usdDebits + ") ≠ credits (" + usdCredits + ")");
        }
    }

    private BigDecimal sum(List<LedgerEntry> entries, LedgerEntrySide side,
                           java.util.function.Function<LedgerEntry, BigDecimal> extractor) {
        return entries.stream()
                .filter(e -> e.getEntrySide() == side)
                .map(extractor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // =========================================================================
    // Resolution helpers
    // =========================================================================

    /**
     * Reads the authenticated principal name from the Spring Security context.
     *
     * <p>Fix #1: the caller's identity is NEVER taken from the request body.</p>
     *
     * @throws IllegalStateException if the security context has no authenticated principal
     */
    private String resolveCallerIdentity() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationRequiredException();
        }
        return auth.getName();
    }

    private LegalEntity resolveActiveApprovedEntity(String entityCode) {
        UUID orgId = securityContext.getOrganizationId();
        LegalEntity entity = legalEntityRepository
                .findByEntityCodeAndOrganizationId(entityCode, orgId)
                .orElseThrow(() -> new com.af.novadesk.api.finance.exception.EntityNotFoundException(
                        java.util.UUID.nameUUIDFromBytes(entityCode.getBytes())));

        if (entity.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new EntityNotApprovedException(entityCode, "not yet approved for financial operations");
        }
        if (entity.getStatus() != Status.ACTIVE) {
            throw new EntityNotApprovedException(entityCode, "not active");
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
                    .orElseThrow(() -> new com.af.novadesk.api.finance.exception.AccountNotFoundException(
                            "Destination account not found: " + destinationAccountId));
            assertAccountBelongsTo(account, targetEntity, "Destination");
            return account;
        }
        return accountRepository
                .findFirstByLegalEntityAndAccountRoleAndStatus(
                        targetEntity, AccountRole.BANK_OPERATING, Status.ACTIVE)
                .or(() -> accountRepository.findFirstByLegalEntityAndAccountRoleAndStatus(
                        targetEntity, AccountRole.CASH, Status.ACTIVE))
                .orElseThrow(() -> new com.af.novadesk.api.finance.exception.AccountNotFoundException(
                        "No active BANK_OPERATING or CASH account for entity: "
                                + targetEntity.getEntityCode()));
    }

    /**
     * Resolves the source account and validates its role against the funding
     * source (Fix #9): a BANK_OPERATING account must not be used as the source
     * of a FOUNDER_EQUITY injection.
     */
    private Account resolveSourceAccount(
            CapitalInjectionRequest request,
            LegalEntity targetEntity,
            LegalEntity sourceEntity
    ) {
        if (request.getSourceAccountId() == null) {
            throw new BadRequestException("sourceAccountId is required");
        }
        Account account = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new com.af.novadesk.api.finance.exception.AccountNotFoundException(
                        "Source account not found: " + request.getSourceAccountId()));

        LegalEntity expectedOwner = (request.getFundingSource() == FundingSource.INTER_ENTITY_TRANSFER)
                ? sourceEntity : targetEntity;
        assertAccountBelongsTo(account, expectedOwner, "Source");

        // Fix #9: assert the account's role matches the declared FundingSource
        AccountRole expectedRole = EXPECTED_SOURCE_ROLE.get(request.getFundingSource());
        if (expectedRole != null && account.getAccountRole() != expectedRole) {
            throw new InvalidAccountStateException(
                    "Source account role " + account.getAccountRole()
                    + " is not valid for " + request.getFundingSource()
                    + " funding — expected " + expectedRole);
        }

        return account;
    }

    private Account findAccountByRole(LegalEntity entity, AccountRole role) {
        return accountRepository
                .findFirstByLegalEntityAndAccountRoleAndStatus(entity, role, Status.ACTIVE)
                .orElseThrow(() -> new com.af.novadesk.api.finance.exception.AccountNotFoundException(
                        "Required account role [" + role + "] not found for entity: "
                                + entity.getEntityCode()));
    }

    private void assertAccountBelongsTo(Account account, LegalEntity entity, String label) {
        if (!account.getLegalEntity().getId().equals(entity.getId())) {
            throw new InvalidAccountStateException(
                    label + " account does not belong to entity: " + entity.getEntityCode());
        }
    }

    // =========================================================================
    // Query methods (read-only)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public CapitalInjectionPageDto listCapitalInjections(
            String entityCode,
            CapitalInjectionStatus injectionStatus,
            String q,
            FundingSource fundingSource,
            LocalDate fromDate, LocalDate toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String currencyLocal,
            int page, int size, String sortBy, String sortDir) {
        UUID orgId = securityContext.getOrganizationId();
        Sort.Direction dir = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(dir, sortBy != null ? sortBy : "fundingDate"));
        Page<CapitalInjection> injectionPage = capitalInjectionRepository.findAll(
                CapitalInjectionRepository.filterSpec(
                        orgId, entityCode, injectionStatus, q, fundingSource,
                        fromDate, toDate, minAmount, maxAmount, currencyLocal),
                pageRequest);

        List<CapitalInjectionSummaryDto> content = injectionPage.getContent().stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());

        return new CapitalInjectionPageDto(
                content,
                injectionPage.getNumber(),
                injectionPage.getSize(),
                injectionPage.getTotalElements(),
                injectionPage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CapitalInjectionDetailDto getCapitalInjectionDetail(UUID id) {
        CapitalInjection injection = capitalInjectionRepository.findWithRelationsById(id)
                .orElseThrow(() -> new com.af.novadesk.api.finance.exception.CapitalInjectionNotFoundException(id));

        // Org-scoped access check: verify the injection's target entity belongs to caller's org
        UUID orgId = securityContext.getOrganizationId();
        if (!injection.getTargetEntity().getOrganizationId().equals(orgId)) {
            throw new com.af.novadesk.api.finance.exception.CapitalInjectionNotFoundException(id);
        }

        List<LedgerEntry> entries = ledgerEntryRepository
                .findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(REFERENCE_TYPE, id);

        List<LedgerEntrySummaryDto> ledgerEntryDtos = entries.stream()
                .map(e -> new LedgerEntrySummaryDto(
                        e.getId(),
                        e.getAccount().getId(),
                        e.getAccount().getAccountName(),
                        e.getAccount().getAccountCode(),
                        e.getEntrySide(),
                        e.getAmountLocal(),
                        e.getCurrencyLocal(),
                        e.getAmountUsd(),
                        e.getExchangeRateUsed(),
                        e.getRateDateUsed(),
                        e.getRateWarning() != null && e.getRateWarning(),
                        e.getDescription()
                ))
                .collect(Collectors.toList());

        LegalEntity target = injection.getTargetEntity();
        LegalEntity source = injection.getSourceEntity();
        Account srcAcct = injection.getSourceAccount();
        Account dstAcct = injection.getDestinationAccount();

        return new CapitalInjectionDetailDto(
                injection.getId(),
                null, // journalId is not stored on the header; fetched via ledger entries
                injection.getTransferId(),
                target.getEntityCode(),
                target.getEntityName(),
                source != null ? source.getEntityCode() : null,
                injection.getFundingSource(),
                injection.getFundingDate(),
                injection.getAmountLocal(),
                injection.getCurrencyLocal(),
                injection.getAmountUsd(),
                injection.getExchangeRateUsed(),
                injection.getRateDateUsed(),
                injection.getRateSource(),
                srcAcct.getId(),
                srcAcct.getAccountName(),
                dstAcct.getId(),
                dstAcct.getAccountName(),
                injection.getReferenceNumber(),
                injection.getNotes(),
                injection.getInjectionStatus(),
                injection.getCreatedBy(),
                injection.getCreatedAt(),
                injection.getUpdatedAt(),
                ledgerEntryDtos
        );
    }

    @Override
    @Transactional
    public void updateCapitalInjectionStatus(UUID id, CapitalInjectionStatusRequest request) {
        CapitalInjection injection = capitalInjectionRepository.findWithRelationsById(id)
                .orElseThrow(() -> new com.af.novadesk.api.finance.exception.CapitalInjectionNotFoundException(id));

        // Org-scoped access check: verify the injection's target entity belongs to caller's org
        UUID orgId = securityContext.getOrganizationId();
        if (!injection.getTargetEntity().getOrganizationId().equals(orgId)) {
            throw new com.af.novadesk.api.finance.exception.CapitalInjectionNotFoundException(id);
        }
        // Entity-scope guard: caller must be able to act on the target entity
        // (org-wide roles bypass; entity-tier roles need an ACTIVE grant).
        entityAccessGuard.assertCanAccessEntity(injection.getTargetEntity().getId());

        if (request.getInjectionStatus() == CapitalInjectionStatus.VOID
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BadRequestException("Reason is required when voiding a capital injection");
        }

        if (request.getInjectionStatus() == CapitalInjectionStatus.VOID) {
            postReversalEntries(injection, request.getReason());
        }

        injection.setInjectionStatus(request.getInjectionStatus());
        capitalInjectionRepository.save(injection);
    }

    /**
     * Builds and persists compensating ledger entries for a voided injection,
     * then publishes the {@code CAPITAL_INJECTION_REVERSED} outbox event.
     * All work happens inside the caller's existing {@code @Transactional} boundary.
     */
    private void postReversalEntries(CapitalInjection injection, String reason) {
        List<LedgerEntry> originalEntries = ledgerEntryRepository
                .findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(REFERENCE_TYPE, injection.getId());

        if (originalEntries.isEmpty()) {
            throw new BadRequestException(
                    "Cannot void capital injection " + injection.getId()
                    + ": no ledger entries found to reverse");
        }

        UUID reversalJournalId = UUID.randomUUID();
        List<LedgerEntry> reversalEntries = buildReversalEntries(originalEntries, reversalJournalId);

        assertBalanced(reversalEntries);
        ledgerEntryRepository.saveAll(reversalEntries);

        String callerIdentity = resolveCallerIdentity();
        outboxService.publishCapitalInjectionReversed(injection, reversalJournalId, callerIdentity, reason);
    }

    /**
     * Produces a compensating set of ledger entries by swapping DEBIT↔CREDIT
     * on every entry from the original journal, sharing a new {@code journalId}.
     * Amounts, accounts, currencies, and FX metadata are copied verbatim so the
     * reversal nets to zero against the original entries.
     */
    private List<LedgerEntry> buildReversalEntries(List<LedgerEntry> originalEntries, UUID reversalJournalId) {
        List<LedgerEntry> reversals = new ArrayList<>(originalEntries.size());
        for (LedgerEntry original : originalEntries) {
            LedgerEntrySide reversedSide = original.getEntrySide() == LedgerEntrySide.DEBIT
                    ? LedgerEntrySide.CREDIT
                    : LedgerEntrySide.DEBIT;

            reversals.add(LedgerEntry.builder()
                    .journalId(reversalJournalId)
                    .transferId(original.getTransferId())
                    .legalEntity(original.getLegalEntity())
                    .account(original.getAccount())
                    .entrySide(reversedSide)
                    .amountLocal(original.getAmountLocal())
                    .currencyLocal(original.getCurrencyLocal())
                    .amountUsd(original.getAmountUsd())
                    .exchangeRateUsed(original.getExchangeRateUsed())
                    .rateDateUsed(original.getRateDateUsed())
                    .rateWarning(original.getRateWarning())
                    .description("Reversal — " + original.getDescription())
                    .referenceType(REFERENCE_TYPE)
                    .referenceId(original.getReferenceId())
                    .build());
        }
        return reversals;
    }

    @Override
    @Transactional(readOnly = true)
    public InterEntityTransferDto getInterEntityTransfer(UUID transferId) {
        List<CapitalInjection> injections = capitalInjectionRepository.findByTransferId(transferId);
        if (injections.isEmpty()) {
            throw new com.af.novadesk.api.finance.exception.CapitalInjectionNotFoundException(
                    "No inter-entity transfer found with id: " + transferId);
        }

        // Single-record model: the one CapitalInjection record has both sourceEntity
        // and targetEntity populated for inter-entity transfers
        CapitalInjection ci = injections.get(0);

        // Org-scoped access check
        UUID orgId = securityContext.getOrganizationId();
        if (!ci.getTargetEntity().getOrganizationId().equals(orgId)) {
            throw new com.af.novadesk.api.finance.exception.CapitalInjectionNotFoundException(
                    "No inter-entity transfer found with id: " + transferId);
        }
        // Entity-scope guard: caller must be able to view the target entity's data.
        entityAccessGuard.assertCanAccessEntity(ci.getTargetEntity().getId());

        String sourceCode = ci.getSourceEntity() != null
                ? ci.getSourceEntity().getEntityCode()
                : null;
        String targetCode = ci.getTargetEntity().getEntityCode();

        // Fetch journalId from ledger entries (not stored on CapitalInjection header)
        UUID journalId = ledgerEntryRepository
                .findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(REFERENCE_TYPE, ci.getId())
                .stream()
                .findFirst()
                .map(LedgerEntry::getJournalId)
                .orElse(null);

        // Single-record model: source & target CI id and journal id are the same
        return new InterEntityTransferDto(
                transferId,
                sourceCode,
                targetCode,
                ci.getId(),   // sourceCapitalInjectionId
                ci.getId(),   // targetCapitalInjectionId  (same record)
                journalId,    // sourceJournalId
                journalId     // targetJournalId            (same journal)
        );
    }

    // =========================================================================
    // DTO mapping helpers
    // =========================================================================

    private CapitalInjectionSummaryDto toSummaryDto(CapitalInjection ci) {
        LegalEntity target = ci.getTargetEntity();
        LegalEntity source = ci.getSourceEntity();

        return new CapitalInjectionSummaryDto(
                ci.getId(),
                target.getEntityCode(),
                target.getEntityName(),
                source != null ? source.getEntityCode() : null,
                ci.getFundingSource(),
                ci.getAmountLocal(),
                ci.getCurrencyLocal(),
                ci.getAmountUsd(),
                ci.getFundingDate(),
                ci.getExchangeRateUsed(),
                ci.getRateSource(),
                ci.getInjectionStatus(),
                ci.getReferenceNumber(),
                ci.getCreatedBy(),
                ci.getRateWarning() != null && ci.getRateWarning(),
                ci.getCreatedAt()
        );
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
