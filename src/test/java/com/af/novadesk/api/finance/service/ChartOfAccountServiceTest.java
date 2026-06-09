package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.dto.ChartOfAccountDto;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.mapper.ChartOfAccountMapper;
import com.af.novadesk.api.finance.repository.ChartOfAccountRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.impl.ChartOfAccountServiceImpl;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChartOfAccountService} / {@link ChartOfAccountServiceImpl}.
 *
 * Covers:
 * - listByEntity()        : returns all CoA entries for an entity, ordered by account code
 * - listByEntityAndType() : filters by account type (e.g. EXPENSE for the expense dropdown)
 * - Empty results         : returns empty list when no entries match
 * - Org-scoping           : cross-tenant access rejected via EntityNotFoundException
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChartOfAccountService")
class ChartOfAccountServiceTest {

    @Mock
    private ChartOfAccountRepository chartOfAccountRepository;

    @Mock
    private ChartOfAccountMapper chartOfAccountMapper;

    @Mock
    private LegalEntityRepository legalEntityRepository;

    @Mock
    private FinanceSecurityContext securityContext;

    private ChartOfAccountService service;

    private static final UUID ORG_ID    = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChartOfAccountServiceImpl(
                chartOfAccountRepository, chartOfAccountMapper,
                legalEntityRepository, securityContext);
        when(securityContext.getOrganizationId()).thenReturn(ORG_ID);
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
            LegalEntity entity = buildEntity(ENTITY_ID);
            when(legalEntityRepository.findByIdAndOrganizationId(ENTITY_ID, ORG_ID))
                    .thenReturn(Optional.of(entity));

            ChartOfAccount asset   = buildCoa(UUID.randomUUID(), entity, "1000", "Cash",     AccountType.ASSET);
            ChartOfAccount expense = buildCoa(UUID.randomUUID(), entity, "5100", "Salaries", AccountType.EXPENSE);

            ChartOfAccountDto assetDto   = buildDto(asset.getId(),   "1000", "Cash",     AccountType.ASSET);
            ChartOfAccountDto expenseDto = buildDto(expense.getId(), "5100", "Salaries", AccountType.EXPENSE);

            when(chartOfAccountRepository.findAllByLegalEntityIdOrderByAccountCodeAsc(ENTITY_ID))
                    .thenReturn(List.of(asset, expense));
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of(asset, expense)))
                    .thenReturn(List.of(assetDto, expenseDto));

            List<ChartOfAccountDto> result = service.listByEntity(ENTITY_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getAccountCode()).isEqualTo("1000");
            assertThat(result.get(1).getAccountCode()).isEqualTo("5100");
            verify(chartOfAccountRepository).findAllByLegalEntityIdOrderByAccountCodeAsc(ENTITY_ID);
        }

        @Test
        @DisplayName("Returns empty list when entity has no CoA entries")
        void returnsEmptyListWhenNoneExist() {
            LegalEntity entity = buildEntity(ENTITY_ID);
            when(legalEntityRepository.findByIdAndOrganizationId(ENTITY_ID, ORG_ID))
                    .thenReturn(Optional.of(entity));
            when(chartOfAccountRepository.findAllByLegalEntityIdOrderByAccountCodeAsc(ENTITY_ID))
                    .thenReturn(List.of());
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of()))
                    .thenReturn(List.of());

            assertThat(service.listByEntity(ENTITY_ID)).isEmpty();
        }

        @Test
        @DisplayName("Throws EntityNotFoundException when entity belongs to a different org (cross-tenant)")
        void throwsWhenEntityBelongsToDifferentOrg() {
            when(legalEntityRepository.findByIdAndOrganizationId(ENTITY_ID, ORG_ID))
                    .thenReturn(Optional.empty());   // entity not found in caller's org

            assertThatThrownBy(() -> service.listByEntity(ENTITY_ID))
                    .isInstanceOf(EntityNotFoundException.class);
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
            LegalEntity entity = buildEntity(ENTITY_ID);
            when(legalEntityRepository.findByIdAndOrganizationId(ENTITY_ID, ORG_ID))
                    .thenReturn(Optional.of(entity));

            ChartOfAccount e1 = buildCoa(UUID.randomUUID(), entity, "5000", "Cost of Sales",   AccountType.EXPENSE);
            ChartOfAccount e2 = buildCoa(UUID.randomUUID(), entity, "5100", "Admin Expenses",  AccountType.EXPENSE);

            ChartOfAccountDto d1 = buildDto(e1.getId(), "5000", "Cost of Sales",  AccountType.EXPENSE);
            ChartOfAccountDto d2 = buildDto(e2.getId(), "5100", "Admin Expenses", AccountType.EXPENSE);

            when(chartOfAccountRepository
                    .findAllByLegalEntityIdAndAccountTypeOrderByAccountCodeAsc(ENTITY_ID, AccountType.EXPENSE))
                    .thenReturn(List.of(e1, e2));
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of(e1, e2)))
                    .thenReturn(List.of(d1, d2));

            List<ChartOfAccountDto> result = service.listByEntityAndType(ENTITY_ID, AccountType.EXPENSE);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(dto -> dto.getAccountType() == AccountType.EXPENSE);
            verify(chartOfAccountRepository)
                    .findAllByLegalEntityIdAndAccountTypeOrderByAccountCodeAsc(ENTITY_ID, AccountType.EXPENSE);
        }

        @Test
        @DisplayName("Returns ASSET entries when accountType=ASSET is requested")
        void returnsAssetEntriesForAssetFilter() {
            LegalEntity entity = buildEntity(ENTITY_ID);
            when(legalEntityRepository.findByIdAndOrganizationId(ENTITY_ID, ORG_ID))
                    .thenReturn(Optional.of(entity));

            ChartOfAccount a1 = buildCoa(UUID.randomUUID(), entity, "1000", "Cash",        AccountType.ASSET);
            ChartOfAccount a2 = buildCoa(UUID.randomUUID(), entity, "1100", "Receivables", AccountType.ASSET);

            ChartOfAccountDto d1 = buildDto(a1.getId(), "1000", "Cash",        AccountType.ASSET);
            ChartOfAccountDto d2 = buildDto(a2.getId(), "1100", "Receivables", AccountType.ASSET);

            when(chartOfAccountRepository
                    .findAllByLegalEntityIdAndAccountTypeOrderByAccountCodeAsc(ENTITY_ID, AccountType.ASSET))
                    .thenReturn(List.of(a1, a2));
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of(a1, a2)))
                    .thenReturn(List.of(d1, d2));

            List<ChartOfAccountDto> result = service.listByEntityAndType(ENTITY_ID, AccountType.ASSET);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(dto -> dto.getAccountType() == AccountType.ASSET);
        }

        @Test
        @DisplayName("Returns empty list when no entries match the given type")
        void returnsEmptyListWhenNoMatch() {
            LegalEntity entity = buildEntity(ENTITY_ID);
            when(legalEntityRepository.findByIdAndOrganizationId(ENTITY_ID, ORG_ID))
                    .thenReturn(Optional.of(entity));
            when(chartOfAccountRepository
                    .findAllByLegalEntityIdAndAccountTypeOrderByAccountCodeAsc(ENTITY_ID, AccountType.EXPENSE))
                    .thenReturn(List.of());
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of()))
                    .thenReturn(List.of());

            assertThat(service.listByEntityAndType(ENTITY_ID, AccountType.EXPENSE)).isEmpty();
        }

        @Test
        @DisplayName("Throws EntityNotFoundException when entity belongs to a different org (cross-tenant)")
        void throwsWhenEntityBelongsToDifferentOrg() {
            when(legalEntityRepository.findByIdAndOrganizationId(ENTITY_ID, ORG_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listByEntityAndType(ENTITY_ID, AccountType.EXPENSE))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("Passes correct entityId and accountType to the repository")
        void passesCorrectArgumentsToRepository() {
            LegalEntity entity = buildEntity(ENTITY_ID);
            when(legalEntityRepository.findByIdAndOrganizationId(ENTITY_ID, ORG_ID))
                    .thenReturn(Optional.of(entity));
            when(chartOfAccountRepository
                    .findAllByLegalEntityIdAndAccountTypeOrderByAccountCodeAsc(ENTITY_ID, AccountType.LIABILITY))
                    .thenReturn(List.of());
            when(chartOfAccountMapper.toChartOfAccountDtoList(List.of()))
                    .thenReturn(List.of());

            service.listByEntityAndType(ENTITY_ID, AccountType.LIABILITY);

            verify(chartOfAccountRepository)
                    .findAllByLegalEntityIdAndAccountTypeOrderByAccountCodeAsc(ENTITY_ID, AccountType.LIABILITY);
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
                .build();
    }

    private ChartOfAccountDto buildDto(UUID id, String code, String name, AccountType type) {
        return new ChartOfAccountDto(id, code, name, type, null, null, true, true, Status.ACTIVE);
    }
}
