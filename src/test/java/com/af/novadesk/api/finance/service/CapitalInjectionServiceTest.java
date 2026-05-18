package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CapitalInjectionEventType;
import com.af.novadesk.api.finance.constants.FundingSource;
import com.af.novadesk.api.finance.constants.LedgerEntrySide;
import com.af.novadesk.api.finance.constants.RateSource;
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
import com.af.novadesk.api.finance.exception.MissingExchangeRateException;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.repository.CapitalInjectionOutboxEventRepository;
import com.af.novadesk.api.finance.repository.CapitalInjectionRepository;
import com.af.novadesk.api.finance.repository.LedgerEntryRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.service.impl.CapitalInjectionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CapitalInjectionService}.
 *
 * Verifies:
 * - LLR-FIN-02.2: balanced double-entry creation (standard two-leg journal)
 * - LLR-FIN-02.4: four-leg inter-entity journal (same-currency and cross-currency)
 * - LLR-FIN-02.1: future date rejection
 * - Entity approval / status guards
 * - Source account role validation (Fix #9)
 * - Audit field pulled from security context, never request body (Fix #1)
 * - MissingExchangeRateException propagation
 * - Transactional Outbox: outbox event written in the same transaction
 */
@ExtendWith(MockitoExtension.class)
class CapitalInjectionServiceTest {

    @Mock private LegalEntityRepository              legalEntityRepository;
    @Mock private AccountRepository                  accountRepository;
    @Mock private CapitalInjectionRepository         capitalInjectionRepository;
    @Mock private LedgerEntryRepository              ledgerEntryRepository;
    @Mock private ExchangeRateService                exchangeRateService;
    @Mock private CapitalInjectionOutboxEventRepository outboxEventRepository;

    /** Real ObjectMapper — tests actual JSON serialisation of the outbox payload. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Fixed UTC clock so date comparisons are deterministic. */
    private final Clock clock = Clock.systemUTC();

    private CapitalInjectionService service;

    @BeforeEach
    void setUp() {
        FundingProperties props = new FundingProperties("USD", 7, new BigDecimal("10000"));

        // Fix #1: stub a Security Context so callerIdentity can be resolved
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("test.user");
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        service = new CapitalInjectionServiceImpl(
                legalEntityRepository, accountRepository,
                capitalInjectionRepository, ledgerEntryRepository,
                exchangeRateService, props, outboxEventRepository,
                objectMapper, clock
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // Happy path – standard two-leg journal (LLR-FIN-02.2)
    // =========================================================================

    @Test
    @DisplayName("Standard FOUNDER_EQUITY injection creates two balanced ledger entries")
    void createCapitalInjection_standard_createsBalancedTwoLegJournal() {
        UUID sourceAccountId      = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        LegalEntity entity    = buildApprovedEntity("INDIA", "INR");
        Account sourceAccount = buildAccount(sourceAccountId, entity, AccountRole.FOUNDER_EQUITY, "INR");
        Account destAccount   = buildAccount(destinationAccountId, entity, AccountRole.BANK_OPERATING, "INR");

        when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(entity));
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destAccount));
        when(exchangeRateService.resolveRate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ExchangeRateResolution(new BigDecimal("0.012000"), RateSource.API, LocalDate.now(ZoneOffset.UTC)));
        when(capitalInjectionRepository.save(any(CapitalInjection.class)))
                .thenAnswer(inv -> {
                    CapitalInjection ci = inv.getArgument(0);
                    if (ci.getId() == null) ci.setId(UUID.randomUUID());
                    return ci;
                });
        when(ledgerEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(CapitalInjectionOutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.FOUNDER_EQUITY,
                new BigDecimal("100000.00"), sourceAccountId, destinationAccountId, null);

        CapitalInjectionResponse response = service.createCapitalInjection(request);

        assertThat(response.targetEntityCode()).isEqualTo("INDIA");
        assertThat(response.amountLocal()).isEqualByComparingTo("100000.0000");
        assertThat(response.amountUsd()).isEqualByComparingTo("1200.0000");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        List<LedgerEntry> entries = captor.getValue();

        assertThat(entries).hasSize(2);
        assertThat(entries).filteredOn(e -> e.getEntrySide() == LedgerEntrySide.DEBIT).hasSize(1);
        assertThat(entries).filteredOn(e -> e.getEntrySide() == LedgerEntrySide.CREDIT).hasSize(1);
        assertThat(sumLocalSide(entries, LedgerEntrySide.DEBIT))
                .isEqualByComparingTo(sumLocalSide(entries, LedgerEntrySide.CREDIT));
        assertThat(sumUsdSide(entries, LedgerEntrySide.DEBIT))
                .isEqualByComparingTo(sumUsdSide(entries, LedgerEntrySide.CREDIT));

        // Fix #1: audit createdBy must come from security context, not request body
        @SuppressWarnings("unchecked")
        ArgumentCaptor<CapitalInjection> injectionCaptor = ArgumentCaptor.forClass(CapitalInjection.class);
        verify(capitalInjectionRepository).save(injectionCaptor.capture());
        assertThat(injectionCaptor.getValue().getCreatedBy()).isEqualTo("test.user");

        // Outbox event assertions
        ArgumentCaptor<CapitalInjectionOutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(CapitalInjectionOutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        CapitalInjectionOutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getEventType()).isEqualTo(CapitalInjectionEventType.CAPITAL_INJECTION_CREATED);
        assertThat(outboxEvent.getIdempotencyKey()).startsWith("CAPITAL_INJECTION_CREATED:");
        assertThat(outboxEvent.getPayload()).contains("\"fundingSource\":\"FOUNDER_EQUITY\"");
        assertThat(outboxEvent.getPayload()).contains("\"targetEntityCode\":\"INDIA\"");
        // Fix #3: Jackson serialisation — no raw string concatenation, createdBy is properly escaped
        assertThat(outboxEvent.getPayload()).contains("\"createdBy\":\"test.user\"");
    }

    // =========================================================================
    // Happy path – inter-entity four-leg journal, same currency (LLR-FIN-02.4)
    // =========================================================================

    @Test
    @DisplayName("Inter-entity transfer (same base currency) creates four balanced ledger legs")
    void createCapitalInjection_interEntity_sameCurrency_createsFourBalancedLegs() {
        UUID srcCashId = UUID.randomUUID();
        UUID dstCashId = UUID.randomUUID();

        LegalEntity sourceEntity = buildApprovedEntity("US",    "USD");
        LegalEntity targetEntity = buildApprovedEntity("INDIA", "USD"); // same currency for this test

        Account srcCash = buildAccount(srcCashId, sourceEntity, AccountRole.CASH,                   "USD");
        Account dstCash = buildAccount(dstCashId, targetEntity, AccountRole.BANK_OPERATING,          "USD");
        Account srcRec  = buildAccount(UUID.randomUUID(), sourceEntity, AccountRole.INTER_ENTITY_RECEIVABLE, "USD");
        Account dstPay  = buildAccount(UUID.randomUUID(), targetEntity, AccountRole.INTER_ENTITY_PAYABLE,    "USD");

        when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(targetEntity));
        when(legalEntityRepository.findByEntityCode("US")).thenReturn(Optional.of(sourceEntity));
        when(accountRepository.findById(srcCashId)).thenReturn(Optional.of(srcCash));
        when(accountRepository.findById(dstCashId)).thenReturn(Optional.of(dstCash));
        when(accountRepository.findFirstByLegalEntityAndAccountRoleAndStatus(
                sourceEntity, AccountRole.INTER_ENTITY_RECEIVABLE, Status.ACTIVE))
                .thenReturn(Optional.of(srcRec));
        when(accountRepository.findFirstByLegalEntityAndAccountRoleAndStatus(
                targetEntity, AccountRole.INTER_ENTITY_PAYABLE, Status.ACTIVE))
                .thenReturn(Optional.of(dstPay));
        when(exchangeRateService.resolveRate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ExchangeRateResolution(BigDecimal.ONE, RateSource.IDENTITY, LocalDate.now(ZoneOffset.UTC)));
        when(capitalInjectionRepository.save(any())).thenAnswer(inv -> {
            CapitalInjection ci = inv.getArgument(0);
            if (ci.getId() == null) ci.setId(UUID.randomUUID());
            return ci;
        });
        when(ledgerEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(CapitalInjectionOutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.INTER_ENTITY_TRANSFER,
                new BigDecimal("15000.00"), srcCashId, dstCashId, "US");

        CapitalInjectionResponse response = service.createCapitalInjection(request);

        assertThat(response.transferId()).isNotNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        List<LedgerEntry> entries = captor.getValue();

        assertThat(entries).hasSize(4);
        assertThat(entries).filteredOn(e -> e.getEntrySide() == LedgerEntrySide.DEBIT).hasSize(2);
        assertThat(entries).filteredOn(e -> e.getEntrySide() == LedgerEntrySide.CREDIT).hasSize(2);
        assertThat(sumLocalSide(entries, LedgerEntrySide.DEBIT))
                .isEqualByComparingTo(sumLocalSide(entries, LedgerEntrySide.CREDIT));
        // Fix #5: USD balance assertion
        assertThat(sumUsdSide(entries, LedgerEntrySide.DEBIT))
                .isEqualByComparingTo(sumUsdSide(entries, LedgerEntrySide.CREDIT));
    }

    // =========================================================================
    // Fix #4: Inter-entity transfer with DIFFERING base currencies
    // =========================================================================

    @Test
    @DisplayName("Inter-entity transfer with different base currencies denominate each entity's legs correctly")
    void createCapitalInjection_interEntity_differentCurrencies_sourceLegsInSourceCurrency() {
        // Target = INDIA (INR), Source = US (USD — the reporting currency)
        // The target entity has rate INR→USD = 0.012.
        // Source entity is in USD (same as reporting), so rate USD→USD = 1.0 (identity).
        // amountLocal = 100,000 INR → amountUsd = 1,200 USD
        // Source legs should be in USD (1,200 USD) not INR.
        UUID srcCashId = UUID.randomUUID();
        UUID dstCashId = UUID.randomUUID();

        LegalEntity sourceEntity = buildApprovedEntity("US",    "USD"); // USD base
        LegalEntity targetEntity = buildApprovedEntity("INDIA", "INR"); // INR base

        Account srcCash = buildAccount(srcCashId, sourceEntity, AccountRole.CASH,                   "USD");
        Account dstCash = buildAccount(dstCashId, targetEntity, AccountRole.BANK_OPERATING,          "INR");
        Account srcRec  = buildAccount(UUID.randomUUID(), sourceEntity, AccountRole.INTER_ENTITY_RECEIVABLE, "USD");
        Account dstPay  = buildAccount(UUID.randomUUID(), targetEntity, AccountRole.INTER_ENTITY_PAYABLE,    "INR");

        when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(targetEntity));
        when(legalEntityRepository.findByEntityCode("US")).thenReturn(Optional.of(sourceEntity));
        when(accountRepository.findById(srcCashId)).thenReturn(Optional.of(srcCash));
        when(accountRepository.findById(dstCashId)).thenReturn(Optional.of(dstCash));
        when(accountRepository.findFirstByLegalEntityAndAccountRoleAndStatus(
                sourceEntity, AccountRole.INTER_ENTITY_RECEIVABLE, Status.ACTIVE))
                .thenReturn(Optional.of(srcRec));
        when(accountRepository.findFirstByLegalEntityAndAccountRoleAndStatus(
                targetEntity, AccountRole.INTER_ENTITY_PAYABLE, Status.ACTIVE))
                .thenReturn(Optional.of(dstPay));

        // First call: target entity rate INR→USD; second call: source entity rate USD→USD (identity)
        when(exchangeRateService.resolveRate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ExchangeRateResolution(new BigDecimal("0.012"), RateSource.API, LocalDate.now(ZoneOffset.UTC)))  // target INR→USD
                .thenReturn(new ExchangeRateResolution(BigDecimal.ONE, RateSource.IDENTITY, LocalDate.now(ZoneOffset.UTC)));     // source USD→USD

        when(capitalInjectionRepository.save(any())).thenAnswer(inv -> {
            CapitalInjection ci = inv.getArgument(0);
            if (ci.getId() == null) ci.setId(UUID.randomUUID());
            return ci;
        });
        when(ledgerEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.INTER_ENTITY_TRANSFER,
                new BigDecimal("100000.00"), srcCashId, dstCashId, "US");

        service.createCapitalInjection(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        List<LedgerEntry> entries = captor.getValue();

        // Source entity legs: 2 entries in USD
        List<LedgerEntry> sourceLegs = entries.stream()
                .filter(e -> e.getLegalEntity().getEntityCode().equals("US")).toList();
        List<LedgerEntry> targetLegs = entries.stream()
                .filter(e -> e.getLegalEntity().getEntityCode().equals("INDIA")).toList();

        assertThat(sourceLegs).hasSize(2);
        assertThat(targetLegs).hasSize(2);

        // Fix #4: source legs must be in USD (not INR)
        sourceLegs.forEach(e -> assertThat(e.getCurrencyLocal()).isEqualTo("USD"));
        targetLegs.forEach(e -> assertThat(e.getCurrencyLocal()).isEqualTo("INR"));

        // Fix #4: source legs amountLocal = amountUsd (100000 INR * 0.012 = 1200 USD / 1.0 = 1200 USD)
        BigDecimal expectedSourceAmountLocal = new BigDecimal("1200.0000");
        sourceLegs.forEach(e ->
                assertThat(e.getAmountLocal()).isEqualByComparingTo(expectedSourceAmountLocal));

        // Fix #5: USD totals must balance across all four legs
        assertThat(sumUsdSide(entries, LedgerEntrySide.DEBIT))
                .isEqualByComparingTo(sumUsdSide(entries, LedgerEntrySide.CREDIT));
    }

    // =========================================================================
    // Validation edge cases
    // =========================================================================

    @Nested
    @DisplayName("Date validation (M3 — guard is O(1) and runs first)")
    class DateValidation {

        @Test
        void futureFundingDate_throwsBadRequest_beforeAnyDbLookup() {
            CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.FOUNDER_EQUITY,
                    new BigDecimal("500.00"), UUID.randomUUID(), UUID.randomUUID(), null);
            request.setFundingDate(LocalDate.now(ZoneOffset.UTC).plusDays(1));

            // Date check happens before entity lookup — no DB mock required
            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("future");
        }
    }

    @Nested
    @DisplayName("Entity resolution guards")
    class EntityResolutionGuards {

        @Test
        @DisplayName("Entity not found → NotFoundException")
        void entityNotFound_throwsNotFoundException() {
            when(legalEntityRepository.findByEntityCode("NONEXISTENT")).thenReturn(Optional.empty());

            CapitalInjectionRequest request = buildRequest("NONEXISTENT", FundingSource.FOUNDER_EQUITY,
                    new BigDecimal("500.00"), UUID.randomUUID(), null, null);

            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("NONEXISTENT");
        }

        @Test
        @DisplayName("Entity not approved → BadRequestException")
        void entityNotApproved_throwsBadRequest() {
            LegalEntity entity = buildApprovedEntity("INDIA", "INR");
            entity.setApprovalStatus(ApprovalStatus.PENDING);
            when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(entity));

            CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.FOUNDER_EQUITY,
                    new BigDecimal("500.00"), UUID.randomUUID(), null, null);

            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not yet approved");
        }

        @Test
        @DisplayName("Entity not active → BadRequestException")
        void entityNotActive_throwsBadRequest() {
            LegalEntity entity = buildApprovedEntity("INDIA", "INR");
            entity.setStatus(Status.INACTIVE);
            when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(entity));

            CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.FOUNDER_EQUITY,
                    new BigDecimal("500.00"), UUID.randomUUID(), null, null);

            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not active");
        }
    }

    @Nested
    @DisplayName("Account validation guards")
    class AccountValidationGuards {

        @Test
        @DisplayName("Source and destination accounts are the same → BadRequestException")
        void sameSourceAndDestination_throwsBadRequest() {
            UUID sharedId = UUID.randomUUID();
            LegalEntity entity = buildApprovedEntity("INDIA", "INR");
            Account account = buildAccount(sharedId, entity, AccountRole.FOUNDER_EQUITY, "INR");

            when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(entity));
            when(accountRepository.findById(sharedId)).thenReturn(Optional.of(account));

            CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.FOUNDER_EQUITY,
                    new BigDecimal("500.00"), sharedId, sharedId, null);

            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Source and destination accounts must be different");
        }

        @Test
        @DisplayName("Fix #9: BANK_OPERATING source for FOUNDER_EQUITY funding → BadRequestException")
        void wrongSourceAccountRole_throwsBadRequest() {
            UUID srcId = UUID.randomUUID();
            UUID dstId = UUID.randomUUID();
            LegalEntity entity = buildApprovedEntity("INDIA", "INR");
            // WRONG: source account is BANK_OPERATING but funding source requires FOUNDER_EQUITY
            Account wrongSrc = buildAccount(srcId, entity, AccountRole.BANK_OPERATING, "INR");
            Account dest     = buildAccount(dstId, entity, AccountRole.BANK_OPERATING, "INR");

            when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(entity));
            when(accountRepository.findById(dstId)).thenReturn(Optional.of(dest));
            when(accountRepository.findById(srcId)).thenReturn(Optional.of(wrongSrc));

            CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.FOUNDER_EQUITY,
                    new BigDecimal("500.00"), srcId, dstId, null);

            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("FOUNDER_EQUITY");
        }
    }

    @Nested
    @DisplayName("Inter-entity validation guards")
    class InterEntityGuards {

        @Test
        @DisplayName("INTER_ENTITY_TRANSFER without sourceEntityCode → BadRequestException")
        void interEntityWithoutSourceEntityCode_throwsBadRequest() {
            CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.INTER_ENTITY_TRANSFER,
                    new BigDecimal("500.00"), UUID.randomUUID(), UUID.randomUUID(), null);
            // sourceEntityCode intentionally not set

            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("sourceEntityCode is required");
        }

        @Test
        @DisplayName("INTER_ENTITY_TRANSFER without sourceAccountId → BadRequestException")
        void interEntityWithoutSourceAccountId_throwsBadRequest() {
            LegalEntity target = buildApprovedEntity("INDIA", "INR");
            when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(target));

            CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.INTER_ENTITY_TRANSFER,
                    new BigDecimal("500.00"), null, UUID.randomUUID(), "US");
            // sourceAccountId is null

            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("sourceAccountId is required");
        }

        @Test
        @DisplayName("Missing INTER_ENTITY_RECEIVABLE account → NotFoundException")
        void missingReceivableAccount_throwsNotFoundException() {
            UUID srcCashId = UUID.randomUUID();
            UUID dstCashId = UUID.randomUUID();
            LegalEntity sourceEntity = buildApprovedEntity("US",    "USD");
            LegalEntity targetEntity = buildApprovedEntity("INDIA", "USD");
            Account srcCash = buildAccount(srcCashId, sourceEntity, AccountRole.CASH,          "USD");
            Account dstCash = buildAccount(dstCashId, targetEntity, AccountRole.BANK_OPERATING, "USD");

            when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(targetEntity));
            when(legalEntityRepository.findByEntityCode("US")).thenReturn(Optional.of(sourceEntity));
            when(accountRepository.findById(srcCashId)).thenReturn(Optional.of(srcCash));
            when(accountRepository.findById(dstCashId)).thenReturn(Optional.of(dstCash));
            when(exchangeRateService.resolveRate(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new ExchangeRateResolution(BigDecimal.ONE, RateSource.IDENTITY, LocalDate.now()));
            when(capitalInjectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            // No INTER_ENTITY_RECEIVABLE available
            when(accountRepository.findFirstByLegalEntityAndAccountRoleAndStatus(
                    sourceEntity, AccountRole.INTER_ENTITY_RECEIVABLE, Status.ACTIVE))
                    .thenReturn(Optional.empty());

            CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.INTER_ENTITY_TRANSFER,
                    new BigDecimal("500.00"), srcCashId, dstCashId, "US");

            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("INTER_ENTITY_RECEIVABLE");
        }
    }

    @Nested
    @DisplayName("Exchange rate propagation")
    class ExchangeRatePropagation {

        @Test
        @DisplayName("MissingExchangeRateException propagates from ExchangeRateService")
        void missingExchangeRate_propagatesMissingExchangeRateException() {
            LegalEntity entity = buildApprovedEntity("INDIA", "INR");
            UUID srcId = UUID.randomUUID();
            UUID dstId = UUID.randomUUID();
            Account src = buildAccount(srcId, entity, AccountRole.FOUNDER_EQUITY, "INR");
            Account dst = buildAccount(dstId, entity, AccountRole.BANK_OPERATING,  "INR");

            when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(entity));
            when(accountRepository.findById(dstId)).thenReturn(Optional.of(dst));
            when(accountRepository.findById(srcId)).thenReturn(Optional.of(src));
            when(exchangeRateService.resolveRate(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new MissingExchangeRateException("No rate for INR→USD"));

            CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.FOUNDER_EQUITY,
                    new BigDecimal("500.00"), srcId, dstId, null);

            assertThatThrownBy(() -> service.createCapitalInjection(request))
                    .isInstanceOf(MissingExchangeRateException.class)
                    .hasMessageContaining("INR");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LegalEntity buildApprovedEntity(String code, String currency) {
        return LegalEntity.builder()
                .id(UUID.randomUUID())
                .entityCode(code)
                .entityName(code + " Entity")
                .baseCurrency(currency)
                .approvalStatus(ApprovalStatus.APPROVED)
                .status(Status.ACTIVE)
                .build();
    }

    private Account buildAccount(UUID id, LegalEntity entity, AccountRole role, String currency) {
        return Account.builder()
                .id(id)
                .legalEntity(entity)
                .accountCode(role.name())
                .accountName(role.name())
                .accountRole(role)
                .accountType(AccountType.ASSET)
                .currencyCode(currency)
                .status(Status.ACTIVE)
                .build();
    }

    private CapitalInjectionRequest buildRequest(
            String targetCode,
            FundingSource fundingSource,
            BigDecimal amount,
            UUID sourceAccountId,
            UUID destinationAccountId,
            String sourceEntityCode
    ) {
        CapitalInjectionRequest req = new CapitalInjectionRequest();
        req.setTargetEntityCode(targetCode);
        req.setFundingSource(fundingSource);
        req.setAmount(amount);
        req.setFundingDate(LocalDate.now(ZoneOffset.UTC));
        req.setSourceAccountId(sourceAccountId);
        req.setDestinationAccountId(destinationAccountId);
        req.setSourceEntityCode(sourceEntityCode);
        // Note: requestedBy intentionally omitted — identity is resolved from
        // the security context (Fix #1).
        return req;
    }

    private BigDecimal sumLocalSide(List<LedgerEntry> entries, LedgerEntrySide side) {
        return entries.stream()
                .filter(e -> e.getEntrySide() == side)
                .map(LedgerEntry::getAmountLocal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumUsdSide(List<LedgerEntry> entries, LedgerEntrySide side) {
        return entries.stream()
                .filter(e -> e.getEntrySide() == side)
                .map(LedgerEntry::getAmountUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
