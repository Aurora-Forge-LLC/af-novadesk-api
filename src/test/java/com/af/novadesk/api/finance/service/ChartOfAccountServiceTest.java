package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.dto.ChartOfAccountDto;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.mapper.ChartOfAccountMapper;
import com.af.novadesk.api.finance.repository.ChartOfAccountRepository;
import com.af.novadesk.api.finance.service.impl.ChartOfAccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChartOfAccountService} / {@link ChartOfAccountServiceImpl}.
 *
 * Covers:
 * - listByEntity()        : returns all CoA entries for an entity, ordered by account code
 * - listByEntityAndType() : filters by account type (e.g. EXPENSE for the expense dropdown)
 * - Empty results         : returns empty list when no entries match
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChartOfAccountService")
class ChartOfAccountServiceTest {

    @Mock
    private ChartOfAccountRepository chartOfAccountRepository;

    @Mock
    private ChartOfAccountMapper chartOfAccountMapper;

    private ChartOfAccountService service;

    @BeforeEach
    void setUp() {
        service = new ChartOfAccountServiceImpl(chartOfAccountRepository, chartOfAccountMapper);
    }

    // =========================================================================
    // listByEntity()
    // =========================================================================

    @Nested
    @DisplayName("listByEntity()")
    class ListByEntity {

        @Test
        @DisplayName("Returns all CoA entries for the entity ordered by account code")
        void returnsAllEntriesForEntity() {
            UUID entityId = UUID.randomUUID();
            LegalEntity entity = buildEntity(entityId);

            ChartOfAccount asset   = buildCoa(UUID.randomUUID(), entity, "1000", "Cash",     AccountType.ASSET);
            ChartOfAccount expense = buildCoa(UUID.randomUUID(), entity, "5100", "Salaries", AccountType.EXPENSE);

            ChartOfAccountDto assetDto   = buildDto(asset.getId(),   "1000", "Cash",     AccountType.ASSET);
            ChartOfAccountDto expenseDto = buildDto(expense.getId(), "5100", "Salaries", AccountType.EXPENSE);

            when(chartOfAccountRepository.findAllByLegalEntityIdOrderByAccountCodeAsc(entityId))
                    .thenReturn(List.of(asset, expense));
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of(asset, expense)))
                    .thenReturn(List.of(assetDto, expenseDto));

            List<ChartOfAccountDto> result = service.listByEntity(entityId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getAccountCode()).isEqualTo("1000");
            assertThat(result.get(1).getAccountCode()).isEqualTo("5100");
            verify(chartOfAccountRepository).findAllByLegalEntityIdOrderByAccountCodeAsc(entityId);
        }

        @Test
        @DisplayName("Returns empty list when entity has no CoA entries")
        void returnsEmptyListWhenNoneExist() {
            UUID entityId = UUID.randomUUID();

            when(chartOfAccountRepository.findAllByLegalEntityIdOrderByAccountCodeAsc(entityId))
                    .thenReturn(List.of());
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of()))
                    .thenReturn(List.of());

            assertThat(service.listByEntity(entityId)).isEmpty();
        }

        @Test
        @DisplayName("Delegates to correct repository method")
        void delegatesToCorrectRepositoryMethod() {
            UUID entityId = UUID.randomUUID();

            when(chartOfAccountRepository.findAllByLegalEntityIdOrderByAccountCodeAsc(entityId))
                    .thenReturn(List.of());
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of()))
                    .thenReturn(List.of());

            service.listByEntity(entityId);

            verify(chartOfAccountRepository).findAllByLegalEntityIdOrderByAccountCodeAsc(entityId);
        }
    }

    // =========================================================================
    // listByEntityAndType()
    // =========================================================================

    @Nested
    @DisplayName("listByEntityAndType()")
    class ListByEntityAndType {

        @Test
        @DisplayName("Returns only EXPENSE entries — the expense category dropdown use-case")
        void returnsOnlyExpenseEntries() {
            UUID entityId = UUID.randomUUID();
            LegalEntity entity = buildEntity(entityId);

            ChartOfAccount e1 = buildCoa(UUID.randomUUID(), entity, "5000", "Cost of Sales",       AccountType.EXPENSE);
            ChartOfAccount e2 = buildCoa(UUID.randomUUID(), entity, "5100", "Admin Expenses",       AccountType.EXPENSE);
            ChartOfAccount e3 = buildCoa(UUID.randomUUID(), entity, "5200", "Selling Expenses",     AccountType.EXPENSE);

            ChartOfAccountDto d1 = buildDto(e1.getId(), "5000", "Cost of Sales",   AccountType.EXPENSE);
            ChartOfAccountDto d2 = buildDto(e2.getId(), "5100", "Admin Expenses",  AccountType.EXPENSE);
            ChartOfAccountDto d3 = buildDto(e3.getId(), "5200", "Selling Expenses",AccountType.EXPENSE);

            when(chartOfAccountRepository.findAllByLegalEntityIdAndAccountType(entityId, AccountType.EXPENSE))
                    .thenReturn(List.of(e1, e2, e3));
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of(e1, e2, e3)))
                    .thenReturn(List.of(d1, d2, d3));

            List<ChartOfAccountDto> result = service.listByEntityAndType(entityId, AccountType.EXPENSE);

            assertThat(result).hasSize(3);
            assertThat(result).allMatch(dto -> dto.getAccountType() == AccountType.EXPENSE);
            verify(chartOfAccountRepository)
                    .findAllByLegalEntityIdAndAccountType(entityId, AccountType.EXPENSE);
        }

        @Test
        @DisplayName("Returns ASSET entries when accountType=ASSET is requested")
        void returnsAssetEntriesForAssetFilter() {
            UUID entityId = UUID.randomUUID();
            LegalEntity entity = buildEntity(entityId);

            ChartOfAccount a1 = buildCoa(UUID.randomUUID(), entity, "1000", "Cash",      AccountType.ASSET);
            ChartOfAccount a2 = buildCoa(UUID.randomUUID(), entity, "1100", "Receivables", AccountType.ASSET);

            ChartOfAccountDto d1 = buildDto(a1.getId(), "1000", "Cash",       AccountType.ASSET);
            ChartOfAccountDto d2 = buildDto(a2.getId(), "1100", "Receivables",AccountType.ASSET);

            when(chartOfAccountRepository.findAllByLegalEntityIdAndAccountType(entityId, AccountType.ASSET))
                    .thenReturn(List.of(a1, a2));
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of(a1, a2)))
                    .thenReturn(List.of(d1, d2));

            List<ChartOfAccountDto> result = service.listByEntityAndType(entityId, AccountType.ASSET);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(dto -> dto.getAccountType() == AccountType.ASSET);
        }

        @Test
        @DisplayName("Returns empty list when no entries match the given type")
        void returnsEmptyListWhenNoMatch() {
            UUID entityId = UUID.randomUUID();

            when(chartOfAccountRepository.findAllByLegalEntityIdAndAccountType(entityId, AccountType.EXPENSE))
                    .thenReturn(List.of());
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of()))
                    .thenReturn(List.of());

            assertThat(service.listByEntityAndType(entityId, AccountType.EXPENSE)).isEmpty();
        }

        @Test
        @DisplayName("Passes correct entityId and accountType to the repository")
        void passesCorrectArgumentsToRepository() {
            UUID entityId = UUID.randomUUID();

            when(chartOfAccountRepository.findAllByLegalEntityIdAndAccountType(entityId, AccountType.LIABILITY))
                    .thenReturn(List.of());
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of()))
                    .thenReturn(List.of());

            service.listByEntityAndType(entityId, AccountType.LIABILITY);

            verify(chartOfAccountRepository)
                    .findAllByLegalEntityIdAndAccountType(entityId, AccountType.LIABILITY);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LegalEntity buildEntity(UUID id) {
        return LegalEntity.builder()
                .id(id)
                .entityCode("TEST01")
                .entityName("Test Entity")
                .baseCurrency("NPR")
                .approvalStatus(ApprovalStatus.APPROVED)
                .status(Status.ACTIVE)
                .build();
    }

    private ChartOfAccount buildCoa(UUID id, LegalEntity entity,
                                    String code, String name, AccountType type) {
        return ChartOfAccount.builder()
                .id(id)
                .legalEntity(entity)
                .accountCode(code)
                .accountName(name)
                .accountType(type)
                .postable(true)
                .systemGenerated(true)
                .status(Status.ACTIVE)
                .build();
    }

    private ChartOfAccountDto buildDto(UUID id, String code, String name, AccountType type) {
        return new ChartOfAccountDto(id, code, name, type, null, null, true, true, Status.ACTIVE);
    }
}
