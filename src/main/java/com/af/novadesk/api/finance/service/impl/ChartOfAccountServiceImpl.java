package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.dto.ChartOfAccountDto;
import com.af.novadesk.api.finance.mapper.ChartOfAccountMapper;
import com.af.novadesk.api.finance.repository.ChartOfAccountRepository;
import com.af.novadesk.api.finance.service.ChartOfAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link ChartOfAccountService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChartOfAccountServiceImpl implements ChartOfAccountService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final ChartOfAccountMapper     chartOfAccountMapper;

    @Override
    public List<ChartOfAccountDto> listByEntity(UUID legalEntityId) {
        log.debug("Listing chart of accounts for entity={}", legalEntityId);
        return chartOfAccountMapper.toChartOfAccountDtoList(
                chartOfAccountRepository.findAllByLegalEntityIdOrderByAccountCodeAsc(legalEntityId)
        );
    }

    @Override
    public List<ChartOfAccountDto> listByEntityAndType(UUID legalEntityId, AccountType accountType) {
        log.debug("Listing chart of accounts for entity={} type={}", legalEntityId, accountType);
        return chartOfAccountMapper.toChartOfAccountDtoList(
                chartOfAccountRepository.findAllByLegalEntityIdAndAccountType(legalEntityId, accountType)
        );
    }
}
