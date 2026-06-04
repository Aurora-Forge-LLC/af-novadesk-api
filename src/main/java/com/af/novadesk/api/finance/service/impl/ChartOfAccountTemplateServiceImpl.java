package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import com.af.novadesk.api.finance.service.ChartOfAccountTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default implementation of {@link ChartOfAccountTemplateService}.
 * Builds country-specific Chart of Accounts templates (LLR-FIN-01.2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartOfAccountTemplateServiceImpl implements ChartOfAccountTemplateService {

    @Override
    public List<ChartOfAccount> buildFromCountry(CountryCode country) {
        java.util.Objects.requireNonNull(country, "Country must not be null");
        log.info("Building Chart of Accounts from template for country={}", country);

        return switch (country) {
            case US -> buildUsTemplate();
            case IN -> buildInTemplate();
            case NP -> buildNpTemplate();
        };
    }

    // ── US GAAP-aligned template ────────────────────────────────────────

    private List<ChartOfAccount> buildUsTemplate() {
        return List.of(
                coa("1000", "Cash and Cash Equivalents",   AccountType.ASSET,     true),
                coa("1100", "Accounts Receivable",         AccountType.ASSET,     true),
                coa("1200", "Inventory",                   AccountType.ASSET,     true),
                coa("1300", "Prepaid Expenses",            AccountType.ASSET,     true),
                coa("1400", "Fixed Assets",                AccountType.ASSET,     false),
                coa("1410", "Property, Plant & Equipment", AccountType.ASSET,     true),
                coa("1500", "Accumulated Depreciation",    AccountType.ASSET,     true),
                coa("2000", "Accounts Payable",            AccountType.LIABILITY, true),
                coa("2100", "Accrued Liabilities",         AccountType.LIABILITY, true),
                coa("2200", "Short-term Borrowings",       AccountType.LIABILITY, true),
                coa("2300", "Long-term Debt",              AccountType.LIABILITY, true),
                coa("3000", "Common Stock",                AccountType.EQUITY,    true),
                coa("3100", "Retained Earnings",           AccountType.EQUITY,    true),
                coa("4000", "Revenue",                     AccountType.REVENUE,   true),
                coa("4100", "Service Revenue",             AccountType.REVENUE,   true),
                coa("5000", "Cost of Goods Sold",          AccountType.EXPENSE,   true),
                coa("5100", "Salaries & Wages",            AccountType.EXPENSE,   true),
                coa("5200", "Rent Expense",                AccountType.EXPENSE,   true),
                coa("5300", "Utilities Expense",           AccountType.EXPENSE,   true),
                coa("5400", "Depreciation Expense",        AccountType.EXPENSE,   true),
                coa("5500", "Tax Expense",                 AccountType.EXPENSE,   true)
        );
    }

    // ── Indian (Ind AS / Schedule III) template ──────────────────────────

    private List<ChartOfAccount> buildInTemplate() {
        return List.of(
                coa("1000", "Cash and Bank Balances",       AccountType.ASSET,     true),
                coa("1100", "Trade Receivables",            AccountType.ASSET,     true),
                coa("1200", "Inventories",                  AccountType.ASSET,     true),
                coa("1300", "Loans & Advances",             AccountType.ASSET,     true),
                coa("1400", "Fixed Assets (Tangible)",      AccountType.ASSET,     false),
                coa("1410", "Property, Plant & Equipment",  AccountType.ASSET,     true),
                coa("1500", "Intangible Assets",            AccountType.ASSET,     true),
                coa("2000", "Trade Payables",               AccountType.LIABILITY, true),
                coa("2100", "Other Current Liabilities",    AccountType.LIABILITY, true),
                coa("2200", "Short-term Borrowings",        AccountType.LIABILITY, true),
                coa("2300", "Long-term Borrowings",         AccountType.LIABILITY, true),
                coa("3000", "Share Capital",                AccountType.EQUITY,    true),
                coa("3100", "Reserves & Surplus",           AccountType.EQUITY,    true),
                coa("4000", "Revenue from Operations",      AccountType.REVENUE,   true),
                coa("4100", "Other Income",                 AccountType.REVENUE,   true),
                coa("5000", "Cost of Materials Consumed",   AccountType.EXPENSE,   true),
                coa("5100", "Employee Benefits Expense",    AccountType.EXPENSE,   true),
                coa("5200", "Finance Costs",                AccountType.EXPENSE,   true),
                coa("5300", "Depreciation & Amortisation",  AccountType.EXPENSE,   true),
                coa("5400", "Tax Expense",                  AccountType.EXPENSE,   true)
        );
    }

    // ── Nepal (NFRS) template ─────────────────────────────────────────────

    private List<ChartOfAccount> buildNpTemplate() {
        return List.of(
                coa("1000", "Cash and Cash Equivalents",          AccountType.ASSET,     true),
                coa("1100", "Accounts Receivable",                AccountType.ASSET,     true),
                coa("1200", "Inventory",                          AccountType.ASSET,     true),
                coa("1300", "Prepayments",                        AccountType.ASSET,     true),
                coa("1400", "Property, Plant & Equipment",        AccountType.ASSET,     false),
                coa("1410", "Land & Building",                    AccountType.ASSET,     true),
                coa("1420", "Furniture & Fixtures",               AccountType.ASSET,     true),
                coa("1500", "Intangible Assets",                  AccountType.ASSET,     true),
                coa("2000", "Accounts Payable",                   AccountType.LIABILITY, true),
                coa("2100", "Accrued Expenses",                   AccountType.LIABILITY, true),
                coa("2200", "Short-term Loans",                   AccountType.LIABILITY, true),
                coa("2300", "Long-term Loans",                    AccountType.LIABILITY, true),
                coa("2400", "Shareholder Loans",                  AccountType.LIABILITY, true),
                coa("3000", "Share Capital",                      AccountType.EQUITY,    true),
                coa("3100", "Retained Earnings",                  AccountType.EQUITY,    true),
                coa("4000", "Revenue",                            AccountType.REVENUE,   true),
                coa("5000", "Cost of Sales",                      AccountType.EXPENSE,   true),
                coa("5100", "Administrative Expenses",            AccountType.EXPENSE,   true),
                coa("5200", "Selling & Distribution Expenses",    AccountType.EXPENSE,   true),
                coa("5300", "Finance Costs",                      AccountType.EXPENSE,   true),
                coa("5400", "Depreciation",                       AccountType.EXPENSE,   true),
                coa("5500", "Tax Expense",                        AccountType.EXPENSE,   true)
        );
    }

    private static ChartOfAccount coa(String code, String name, AccountType type, boolean postable) {
        return ChartOfAccount.builder()
                .accountCode(code)
                .accountName(name)
                .accountType(type)
                .postable(postable)
                .systemGenerated(true)
                .build();
    }
}
