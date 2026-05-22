package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.LegalEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds the default funding {@link Account} records (fa_accounts) from
 * country-specific templates (LLR-FIN-02.1).
 *
 * <p>These accounts are seeded when a legal entity is approved, providing
 * the source and destination accounts required by the capital-injection
 * workflow.  Each supported country gets a standard set of accounts that
 * cover all {@link com.af.novadesk.api.finance.constants.FundingSource}
 * options.</p>
 *
 * <p>Standard accounts seeded per entity:</p>
 * <ul>
 *   <li>{@code BANK_OPERATING} — default destination for capital injections</li>
 *   <li>{@code FOUNDER_EQUITY} — source for FOUNDER_EQUITY funding</li>
 *   <li>{@code LOAN_PAYABLE} — source for LOAN funding</li>
 *   <li>{@code GRANT_INCOME} — source for GRANT funding</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountTemplateService {

    /**
     * Builds the default funding accounts for the given country.
     *
     * @param country the ISO alpha-2 country code
     * @param entity  the legal entity that will own these accounts
     * @return a list of Account entries to seed
     */
    public List<Account> buildFromCountry(CountryCode country, LegalEntity entity) {
        log.info("Building funding accounts from template for country={}, entity={}",
                country, entity.getEntityCode());

        String currency = entity.getBaseCurrency();

        return switch (country) {
            case US -> buildUsAccounts(entity, currency);
            case IN -> buildInAccounts(entity, currency);
            case NP -> buildNpAccounts(entity, currency);
        };
    }

    // ── US (USD) template ────────────────────────────────────────────────

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

    // ── India (INR) template ─────────────────────────────────────────────

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

    // ── Nepal (NPR) template ─────────────────────────────────────────────

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

    // ── Helper ───────────────────────────────────────────────────────────

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
