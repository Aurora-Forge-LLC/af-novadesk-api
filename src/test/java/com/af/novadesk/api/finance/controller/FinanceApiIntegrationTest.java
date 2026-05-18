package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.constants.LedgerEntrySide;
import com.af.novadesk.api.finance.constants.Status;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.LedgerEntry;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.repository.LedgerEntryRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;INIT=CREATE SCHEMA IF NOT EXISTS af_novadesk\\;CREATE SCHEMA IF NOT EXISTS af_novadesk_outbox",
        "spring.jpa.properties.hibernate.default_schema=af_novadesk",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class FinanceApiIntegrationTest {

    private static final String URL = "/api/v1/finance/funding/capital-injections";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;

    @Test
    @WithMockUser(username = "finance.user", roles = "FINANCE_CAPITAL_INJECTION_WRITE")
    @DisplayName("Founder equity capital injection creates a balanced two-leg ledger journal")
    void createCapitalInjection_founderEquity_createsBalancedLedger() throws Exception {
        LegalEntity target = saveEntity("INDIA", "INR", CountryCode.IN);
        Account source = saveAccount(target, AccountRole.FOUNDER_EQUITY, "FOUNDER_EQUITY_SRC", "Founder Equity");
        Account destination = saveAccount(target, AccountRole.BANK_OPERATING, "BANK_OP", "Bank Operating");

        String body = """
                {
                  "target_entity_code": "INDIA",
                  "funding_source": "FOUNDER_EQUITY",
                  "amount": 100000.00,
                  "funding_date": "%s",
                  "source_account_id": "%s",
                  "destination_account_id": "%s",
                  "manual_exchange_rate": 0.012000,
                  "manual_rate_justification": "fallback rate for integration test",
                  "manual_rate_approved_by": "finance.manager"
                }
                """.formatted(LocalDate.now(), source.getId(), destination.getId());

        MvcResult result = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.capital_injection_id").exists())
                .andReturn();

        UUID injectionId = extractInjectionId(result);
        List<LedgerEntry> entries = ledgerEntryRepository.findAll().stream()
                .filter(e -> injectionId.equals(e.getReferenceId()))
                .toList();

        assertThat(entries).hasSize(2);
        assertThat(entries.stream().filter(e -> e.getEntrySide() == LedgerEntrySide.DEBIT).count()).isEqualTo(1);
        assertThat(entries.stream().filter(e -> e.getEntrySide() == LedgerEntrySide.CREDIT).count()).isEqualTo(1);
        assertThat(sumBySide(entries, LedgerEntrySide.DEBIT, true))
                .isEqualByComparingTo(sumBySide(entries, LedgerEntrySide.CREDIT, true));
        assertThat(sumBySide(entries, LedgerEntrySide.DEBIT, false))
                .isEqualByComparingTo(sumBySide(entries, LedgerEntrySide.CREDIT, false));
    }

    @Test
    @WithMockUser(username = "finance.user", roles = "FINANCE_CAPITAL_INJECTION_WRITE")
    @DisplayName("Inter-entity transfer posts all four balanced ledger legs")
    void createCapitalInjection_interEntityTransfer_postsAllFourLegs() throws Exception {
        LegalEntity target = saveEntity("INDIA", "INR", CountryCode.IN);
        LegalEntity sourceEntity = saveEntity("US", "USD", CountryCode.US);

        Account sourceCash = saveAccount(sourceEntity, AccountRole.CASH, "US_CASH", "US Cash");
        saveAccount(sourceEntity, AccountRole.INTER_ENTITY_RECEIVABLE, "US_INTER_REC", "US Inter Receivable");
        Account targetBank = saveAccount(target, AccountRole.BANK_OPERATING, "INR_BANK", "INR Bank");
        saveAccount(target, AccountRole.INTER_ENTITY_PAYABLE, "IN_INTER_PAY", "IN Inter Payable");

        String body = """
                {
                  "target_entity_code": "INDIA",
                  "source_entity_code": "US",
                  "funding_source": "INTER_ENTITY_TRANSFER",
                  "amount": 100000.00,
                  "funding_date": "%s",
                  "source_account_id": "%s",
                  "destination_account_id": "%s",
                  "manual_exchange_rate": 0.012000,
                  "manual_rate_justification": "test inter-entity rate",
                  "manual_rate_approved_by": "finance.manager"
                }
                """.formatted(LocalDate.now(), sourceCash.getId(), targetBank.getId());

        MvcResult result = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transfer_id").exists())
                .andReturn();

        UUID injectionId = extractInjectionId(result);
        List<LedgerEntry> entries = ledgerEntryRepository.findAll().stream()
                .filter(e -> injectionId.equals(e.getReferenceId()))
                .toList();

        assertThat(entries).hasSize(4);
        assertThat(entries.stream().filter(e -> e.getEntrySide() == LedgerEntrySide.DEBIT).count()).isEqualTo(2);
        assertThat(entries.stream().filter(e -> e.getEntrySide() == LedgerEntrySide.CREDIT).count()).isEqualTo(2);
        assertThat(sumBySide(entries, LedgerEntrySide.DEBIT, true))
                .isEqualByComparingTo(sumBySide(entries, LedgerEntrySide.CREDIT, true));
        assertThat(sumBySide(entries, LedgerEntrySide.DEBIT, false))
                .isEqualByComparingTo(sumBySide(entries, LedgerEntrySide.CREDIT, false));
    }

    @Test
    @WithMockUser(username = "finance.user", roles = "FINANCE_CAPITAL_INJECTION_WRITE")
    @DisplayName("Future funding date returns 400 validation problem")
    void createCapitalInjection_futureDate_returns400() throws Exception {
        LegalEntity target = saveEntity("INDIA", "INR", CountryCode.IN);
        Account source = saveAccount(target, AccountRole.FOUNDER_EQUITY, "FOUNDER_EQUITY_SRC", "Founder Equity");
        Account destination = saveAccount(target, AccountRole.BANK_OPERATING, "BANK_OP", "Bank Operating");

        String body = """
                {
                  "target_entity_code": "INDIA",
                  "funding_source": "FOUNDER_EQUITY",
                  "amount": 1000.00,
                  "funding_date": "%s",
                  "source_account_id": "%s",
                  "destination_account_id": "%s"
                }
                """.formatted(LocalDate.now().plusDays(1), source.getId(), destination.getId());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void createCapitalInjection_unauthenticated_returns401() throws Exception {
        String body = """
                {
                  "target_entity_code": "INDIA",
                  "funding_source": "FOUNDER_EQUITY",
                  "amount": 1000.00,
                  "funding_date": "%s",
                  "source_account_id": "%s"
                }
                """.formatted(LocalDate.now(), UUID.randomUUID());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "finance.user", roles = "FINANCE_CAPITAL_INJECTION_WRITE")
    @DisplayName("Missing exchange rate and no manual rate returns 422")
    void createCapitalInjection_missingExchangeRate_returns422() throws Exception {
        LegalEntity target = saveEntity("INDIA", "INR", CountryCode.IN);
        Account source = saveAccount(target, AccountRole.FOUNDER_EQUITY, "FOUNDER_EQUITY_SRC", "Founder Equity");
        Account destination = saveAccount(target, AccountRole.BANK_OPERATING, "BANK_OP", "Bank Operating");

        String body = """
                {
                  "target_entity_code": "INDIA",
                  "funding_source": "FOUNDER_EQUITY",
                  "amount": 1000.00,
                  "funding_date": "%s",
                  "source_account_id": "%s",
                  "destination_account_id": "%s"
                }
                """.formatted(LocalDate.now(), source.getId(), destination.getId());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Missing Exchange Rate"));
    }

    private UUID extractInjectionId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(root.path("data").path("capital_injection_id").asText());
    }

    private BigDecimal sumBySide(List<LedgerEntry> entries, LedgerEntrySide side, boolean local) {
        return entries.stream()
                .filter(e -> e.getEntrySide() == side)
                .map(e -> local ? e.getAmountLocal() : e.getAmountUsd())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LegalEntity saveEntity(String code, String currency, CountryCode country) {
        return legalEntityRepository.save(LegalEntity.builder()
                .entityName(code + " Entity")
                .entityCode(code)
                .country(country)
                .baseCurrency(currency)
                .incorporationDate(LocalDate.of(2020, 1, 1))
                .approvalStatus(ApprovalStatus.APPROVED)
                .status(Status.ACTIVE)
                .build());
    }

    private Account saveAccount(LegalEntity entity, AccountRole role, String code, String name) {
        return accountRepository.save(Account.builder()
                .legalEntity(entity)
                .accountCode(code)
                .accountName(name)
                .accountRole(role)
                .accountType(AccountType.ASSET)
                .currencyCode(entity.getBaseCurrency())
                .status(Status.ACTIVE)
                .build());
    }

}

