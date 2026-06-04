package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.service.AccountTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link AccountTemplateService}.
 * Builds default funding accounts per country template (LLR-FIN-02.1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountTemplateServiceImpl implements AccountTemplateService {

    @Override
    public List<Account> buildFromCountry(CountryCode country, LegalEntity entity) {
        Objects.requireNonNull(country, "Country must not be null");
        Objects.requireNonNull(entity,  "LegalEntity must not be null");
        log.info("Building funding accounts from template for country={}, entity={}",
                country, entity.getEntityCode());

        String currency = entity.getBaseCurrency();

        return switch (country) {
            case US -> buildUsAccounts(entity, currency);
            case IN -> buildInAccounts(entity, currency);
            case NP -> buildNpAccounts(entity, currency);
        };
    }

    private List<Account> buildUsAccounts(LegalEntity entity, String currency) {
        return List.of(
                account(entity, "1000", "Bank - Operating Account (USD)",
                        AccountRole.BANK_OPERATING, AccountType.ASSET, currency),
                account(entity, "1100", "Petty Cash (USD)",
                        AccountRole.CASH, AccountType.ASSET, currency),
                account(entity, "3000", "Founders - Equity (USD)",
                        AccountRole.FOUNDER_EQUITY, AccountType.EQUITY, currency),
                account(entity, "2100", "Loan Payable (USD)",
                        AccountRole.LOAN_PAYABLE, AccountType.LIABILITY, currency),
                account(entity, "4000", "Grant Income (USD)",
                        AccountRole.GRANT_INCOME, AccountType.REVENUE, currency)
        );
    }

    private List<Account> buildInAccounts(LegalEntity entity, String currency) {
        return List.of(
                account(entity, "1000", "Bank - Operating Account (INR)",
                        AccountRole.BANK_OPERATING, AccountType.ASSET, currency),
                account(entity, "1100", "Cash in Hand (INR)",
                        AccountRole.CASH, AccountType.ASSET, currency),
                account(entity, "3000", "Founders - Equity (INR)",
                        AccountRole.FOUNDER_EQUITY, AccountType.EQUITY, currency),
                account(entity, "2100", "Loan Payable (INR)",
                        AccountRole.LOAN_PAYABLE, AccountType.LIABILITY, currency),
                account(entity, "4000", "Grant Income (INR)",
                        AccountRole.GRANT_INCOME, AccountType.REVENUE, currency)
        );
    }

    private List<Account> buildNpAccounts(LegalEntity entity, String currency) {
        return List.of(
                account(entity, "1000", "Bank - Operating Account (NPR)",
                        AccountRole.BANK_OPERATING, AccountType.ASSET, currency),
                account(entity, "1100", "Petty Cash (NPR)",
                        AccountRole.CASH, AccountType.ASSET, currency),
                account(entity, "3000", "Founders - Equity (NPR)",
                        AccountRole.FOUNDER_EQUITY, AccountType.EQUITY, currency),
                account(entity, "2100", "Loan Payable (NPR)",
                        AccountRole.LOAN_PAYABLE, AccountType.LIABILITY, currency),
                account(entity, "4000", "Grant Income (NPR)",
                        AccountRole.GRANT_INCOME, AccountType.REVENUE, currency)
        );
    }

    private static Account account(LegalEntity entity, String code, String name,
                                   AccountRole role, AccountType type, String currency) {
        return Account.builder()
                .legalEntity(entity)
                .accountCode(code)
                .accountName(name)
                .accountRole(role)
                .accountType(type)
                .currencyCode(currency)
                .build();
    }
}
