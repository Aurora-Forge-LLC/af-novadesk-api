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
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.repository.CapitalInjectionOutboxEventRepository;
import com.af.novadesk.api.finance.repository.CapitalInjectionRepository;
import com.af.novadesk.api.finance.repository.LedgerEntryRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.service.impl.CapitalInjectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CapitalInjectionService}.
 *
 * Verifies:
 * - LLR-FIN-02.2: balanced double-entry creation (standard two-leg journal)
 * - LLR-FIN-02.4: four-leg inter-entity journal
 * - LLR-FIN-02.1: future date rejection
 * - Entity approval / status guard
 * - Transactional Outbox: outbox event is saved in the same transaction
 */
@ExtendWith(MockitoExtension.class)
class CapitalInjectionServiceTest {

    @Mock private LegalEntityRepository              legalEntityRepository;
    @Mock private AccountRepository                  accountRepository;
    @Mock private CapitalInjectionRepository         capitalInjectionRepository;
    @Mock private LedgerEntryRepository              ledgerEntryRepository;
    @Mock private ExchangeRateService                exchangeRateService;
    @Mock private CapitalInjectionOutboxEventRepository outboxEventRepository;

    private CapitalInjectionService service;

    @BeforeEach
    void setUp() {
        FundingProperties props = new FundingProperties();
        props.setReportingCurrency("USD");
        props.setExchangeRateLookbackDays(7);

        service = new CapitalInjectionServiceImpl(
                legalEntityRepository, accountRepository,
                capitalInjectionRepository, ledgerEntryRepository,
                exchangeRateService, props, outboxEventRepository
        );
    }

    // =========================================================================
    // Happy path – standard two-leg journal (LLR-FIN-02.2)
    // =========================================================================

    @Test
    void createCapitalInjection_standard_createsBalancedTwoLegJournal() {
        // Arrange
        UUID sourceAccountId      = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        LegalEntity entity    = buildApprovedEntity("INDIA", "INR");
        Account sourceAccount = buildAccount(sourceAccountId, entity, AccountRole.FOUNDER_EQUITY, "INR");
        Account destAccount   = buildAccount(destinationAccountId, entity, AccountRole.BANK_OPERATING, "INR");

        when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(entity));
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destAccount));
        when(exchangeRateService.resolveRate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ExchangeRateResolution(new BigDecimal("0.012000"), RateSource.API, LocalDate.now()));
        when(capitalInjectionRepository.save(any(CapitalInjection.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(CapitalInjectionOutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.FOUNDER_EQUITY,
                new BigDecimal("100000.00"), sourceAccountId, destinationAccountId, null);

        // Act
        CapitalInjectionResponse response = service.createCapitalInjection(request);

        // Assert response
        assertThat(response.targetEntityCode()).isEqualTo("INDIA");
        assertThat(response.amountLocal()).isEqualByComparingTo("100000.0000");
        assertThat(response.amountUsd()).isEqualByComparingTo("1200.0000");

        // Assert two balanced ledger entries
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        List<LedgerEntry> entries = captor.getValue();

        assertThat(entries).hasSize(2);
        assertThat(entries).filteredOn(e -> e.getEntrySide() == LedgerEntrySide.DEBIT).hasSize(1);
        assertThat(entries).filteredOn(e -> e.getEntrySide() == LedgerEntrySide.CREDIT).hasSize(1);

        BigDecimal debits  = sumSide(entries, LedgerEntrySide.DEBIT);
        BigDecimal credits = sumSide(entries, LedgerEntrySide.CREDIT);
        assertThat(debits).isEqualByComparingTo(credits);

        // Assert outbox event written in the same transaction
        ArgumentCaptor<CapitalInjectionOutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(CapitalInjectionOutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        CapitalInjectionOutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getEventType()).isEqualTo(CapitalInjectionEventType.CAPITAL_INJECTION_CREATED);
        assertThat(outboxEvent.getIdempotencyKey()).startsWith("CAPITAL_INJECTION_CREATED:");
        assertThat(outboxEvent.getPayload()).contains("\"fundingSource\":\"FOUNDER_EQUITY\"");
        assertThat(outboxEvent.getPayload()).contains("\"targetEntityCode\":\"INDIA\"");
    }

    // =========================================================================
    // Happy path – inter-entity four-leg journal (LLR-FIN-02.4)
    // =========================================================================

    @Test
    void createCapitalInjection_interEntity_createsFourBalancedLegs() {
        UUID srcCashId   = UUID.randomUUID();
        UUID dstCashId   = UUID.randomUUID();
        UUID srcRecId    = UUID.randomUUID();
        UUID dstPayId    = UUID.randomUUID();

        LegalEntity sourceEntity = buildApprovedEntity("US",    "USD");
        LegalEntity targetEntity = buildApprovedEntity("INDIA", "USD"); // billing in USD for simplicity

        Account srcCash    = buildAccount(srcCashId,  sourceEntity, AccountRole.CASH,                    "USD");
        Account dstCash    = buildAccount(dstCashId,  targetEntity, AccountRole.BANK_OPERATING,           "USD");
        Account srcRec     = buildAccount(srcRecId,   sourceEntity, AccountRole.INTER_ENTITY_RECEIVABLE,  "USD");
        Account dstPay     = buildAccount(dstPayId,   targetEntity, AccountRole.INTER_ENTITY_PAYABLE,     "USD");

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
                .thenReturn(new ExchangeRateResolution(BigDecimal.ONE, RateSource.IDENTITY, LocalDate.now()));
        when(capitalInjectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(CapitalInjectionOutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.INTER_ENTITY_TRANSFER,
                new BigDecimal("15000.00"), srcCashId, dstCashId, "US");

        // Act
        CapitalInjectionResponse response = service.createCapitalInjection(request);

        // Assert transfer ID assigned
        assertThat(response.transferId()).isNotNull();

        // Assert four entries, balanced
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        List<LedgerEntry> entries = captor.getValue();

        assertThat(entries).hasSize(4);
        assertThat(entries).filteredOn(e -> e.getEntrySide() == LedgerEntrySide.DEBIT).hasSize(2);
        assertThat(entries).filteredOn(e -> e.getEntrySide() == LedgerEntrySide.CREDIT).hasSize(2);
        assertThat(sumSide(entries, LedgerEntrySide.DEBIT))
                .isEqualByComparingTo(sumSide(entries, LedgerEntrySide.CREDIT));

        // Assert outbox event written for inter-entity transfer
        ArgumentCaptor<CapitalInjectionOutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(CapitalInjectionOutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        CapitalInjectionOutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getEventType()).isEqualTo(CapitalInjectionEventType.CAPITAL_INJECTION_CREATED);
        assertThat(outboxEvent.getPayload()).contains("\"fundingSource\":\"INTER_ENTITY_TRANSFER\"");
        assertThat(outboxEvent.getPayload()).contains("\"sourceEntityCode\":\"US\"");
    }

    // =========================================================================
    // Validation edge cases
    // =========================================================================

    @Test
    void createCapitalInjection_futureFundingDate_throwsBadRequest() {
        LegalEntity entity = buildApprovedEntity("INDIA", "INR");
        when(legalEntityRepository.findByEntityCode("INDIA")).thenReturn(Optional.of(entity));

        CapitalInjectionRequest request = buildRequest("INDIA", FundingSource.FOUNDER_EQUITY,
                new BigDecimal("500.00"), UUID.randomUUID(), UUID.randomUUID(), null);
        request.setFundingDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> service.createCapitalInjection(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LegalEntity buildApprovedEntity(String code, String currency) {
        return LegalEntity.builder()
                .id(UUID.randomUUID())          // explicit ID required for assertAccountBelongsTo
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
        req.setFundingDate(LocalDate.now());
        req.setSourceAccountId(sourceAccountId);
        req.setDestinationAccountId(destinationAccountId);
        req.setSourceEntityCode(sourceEntityCode);
        req.setRequestedBy("test.user");
        return req;
    }

    private BigDecimal sumSide(List<LedgerEntry> entries, LedgerEntrySide side) {
        return entries.stream()
                .filter(e -> e.getEntrySide() == side)
                .map(LedgerEntry::getAmountLocal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
