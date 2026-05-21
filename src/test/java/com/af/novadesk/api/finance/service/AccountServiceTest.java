package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.dto.AccountSummaryResponse;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.AccountNotFoundException;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.service.impl.AccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccountService} / {@link AccountServiceImpl}.
 *
 * Covers:
 * - list()     : returns all accounts mapped to DTOs; empty list when no accounts
 * - getById()  : finds existing account; throws NotFoundException for unknown ID
 * - Mapping    : all DTO fields are populated correctly from the entity
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(accountRepository);
    }

    // =========================================================================
    // list()
    // =========================================================================

    @Nested
    @DisplayName("list()")
    class ListAccounts {

        @Test
        @DisplayName("Returns all accounts as mapped summary DTOs")
        void list_returnsAllAccountsMapped() {
            UUID entityId  = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();
            LegalEntity entity  = buildEntity(entityId, "US", "USD");
            Account     account = buildAccount(accountId, entity, AccountRole.FOUNDER_EQUITY, AccountType.EQUITY, "USD");

            when(accountRepository.findAllWithLegalEntity()).thenReturn(List.of(account));

            List<AccountSummaryResponse> results = service.list();

            assertThat(results).hasSize(1);
            AccountSummaryResponse dto = results.get(0);
            assertThat(dto.id()).isEqualTo(accountId);
            assertThat(dto.legalEntityId()).isEqualTo(entityId);
            assertThat(dto.accountCode()).isEqualTo("FOUNDER_EQUITY");
            assertThat(dto.accountName()).isEqualTo("Founder Equity Account");
            assertThat(dto.accountRole()).isEqualTo(AccountRole.FOUNDER_EQUITY);
            assertThat(dto.accountType()).isEqualTo(AccountType.EQUITY);
            assertThat(dto.currencyCode()).isEqualTo("USD");
            assertThat(dto.status()).isEqualTo(Status.ACTIVE);
        }

        @Test
        @DisplayName("Returns multiple accounts when repository has many")
        void list_multipleAccounts_returnsAll() {
            LegalEntity entity = buildEntity(UUID.randomUUID(), "US", "USD");
            Account a1 = buildAccount(UUID.randomUUID(), entity, AccountRole.FOUNDER_EQUITY,  AccountType.EQUITY,    "USD");
            Account a2 = buildAccount(UUID.randomUUID(), entity, AccountRole.BANK_OPERATING,  AccountType.ASSET,     "USD");
            Account a3 = buildAccount(UUID.randomUUID(), entity, AccountRole.INTER_ENTITY_RECEIVABLE, AccountType.ASSET, "USD");

            when(accountRepository.findAllWithLegalEntity()).thenReturn(List.of(a1, a2, a3));

            assertThat(service.list()).hasSize(3);
        }

        @Test
        @DisplayName("Returns empty list when no accounts exist")
        void list_noAccounts_returnsEmptyList() {
            when(accountRepository.findAllWithLegalEntity()).thenReturn(List.of());

            assertThat(service.list()).isEmpty();
        }
    }

    // =========================================================================
    // getById()
    // =========================================================================

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("Existing account is returned as summary DTO")
        void getById_existing_returnsSummaryDto() {
            UUID accountId = UUID.randomUUID();
            LegalEntity entity  = buildEntity(UUID.randomUUID(), "US", "USD");
            Account     account = buildAccount(accountId, entity, AccountRole.BANK_OPERATING, AccountType.ASSET, "USD");

            when(accountRepository.findWithLegalEntityById(accountId)).thenReturn(Optional.of(account));

            AccountSummaryResponse dto = service.getById(accountId);

            assertThat(dto.id()).isEqualTo(accountId);
            assertThat(dto.accountRole()).isEqualTo(AccountRole.BANK_OPERATING);
            assertThat(dto.accountType()).isEqualTo(AccountType.ASSET);
        }

        @Test
        @DisplayName("Non-existent ID throws NotFoundException containing the ID")
        void getById_nonExistent_throwsNotFoundWithId() {
            UUID nonExistentId = UUID.randomUUID();
            when(accountRepository.findWithLegalEntityById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(nonExistentId))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining(nonExistentId.toString());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LegalEntity buildEntity(UUID id, String code, String currency) {
        return LegalEntity.builder()
                .id(id)
                .entityCode(code)
                .entityName(code + " Entity")
                .baseCurrency(currency)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
    }

    private Account buildAccount(UUID id, LegalEntity entity, AccountRole role, AccountType type, String currency) {
        return Account.builder()
                .id(id)
                .legalEntity(entity)
                .accountCode(role.name())
                .accountName(roleName(role))
                .accountRole(role)
                .accountType(type)
                .currencyCode(currency)
                .status(Status.ACTIVE)
                .build();
    }

    private String roleName(AccountRole role) {
        return switch (role) {
            case FOUNDER_EQUITY          -> "Founder Equity Account";
            case BANK_OPERATING          -> "Bank Operating Account";
            case CASH                    -> "Cash Account";
            case INTER_ENTITY_RECEIVABLE -> "Inter-Entity Receivable";
            case INTER_ENTITY_PAYABLE    -> "Inter-Entity Payable";
            default                      -> role.name();
        };
    }
}

