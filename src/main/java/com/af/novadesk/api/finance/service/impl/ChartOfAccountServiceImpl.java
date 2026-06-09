package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.dto.ChartOfAccountDto;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.mapper.ChartOfAccountMapper;
import com.af.novadesk.api.finance.repository.ChartOfAccountRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.ChartOfAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link ChartOfAccountService}.
 *
 * <p>Every query is org-scoped: the caller may only access CoA entries
 * for legal entities that belong to their own organisation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChartOfAccountServiceImpl implements ChartOfAccountService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final ChartOfAccountMapper     chartOfAccountMapper;
    private final LegalEntityRepository    legalEntityRepository;
    private final FinanceSecurityContext   securityContext;

    @Override
    public List<ChartOfAccountDto> listByEntity(UUID legalEntityId) {
        requireEntityInOrg(legalEntityId);
        log.debug("Listing chart of accounts for entity={}", legalEntityId);
        return chartOfAccountMapper.toChartOfAccountDtoList(
                chartOfAccountRepository.findAllByLegalEntityIdOrderByAccountCodeAsc(legalEntityId)
        );
    }

    @Override
    public List<ChartOfAccountDto> listByEntityAndType(UUID legalEntityId, AccountType accountType) {
        requireEntityInOrg(legalEntityId);
        log.debug("Listing chart of accounts for entity={} type={}", legalEntityId, accountType);
        return chartOfAccountMapper.toChartOfAccountDtoList(
                chartOfAccountRepository
                        .findAllByLegalEntityIdAndAccountTypeOrderByAccountCodeAsc(legalEntityId, accountType)
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Verifies the requested entity exists within the caller's organisation.
     * Throws {@link EntityNotFoundException} if not found or if the entity
     * belongs to a different organisation — preventing cross-tenant data access.
     */
    private void requireEntityInOrg(UUID legalEntityId) {
        UUID orgId = securityContext.getOrganizationId();
        legalEntityRepository
                .findByIdAndOrganizationId(legalEntityId, orgId)
                .orElseThrow(() -> new EntityNotFoundException(legalEntityId));
    }
}
