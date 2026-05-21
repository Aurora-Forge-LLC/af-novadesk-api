package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.BankAccountType;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds default bank account records from country-specific templates (LLR-FIN-01.2).
 *
 * <p>Each supported country gets a default set of bank/cash accounts
 * (Cash Account, Operating Account) when a legal entity is approved.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountTemplateService {

    /**
     * Builds the default bank accounts for the given country.
     *
     * @param country the ISO alpha-2 country code
     * @return a list of EntityBankAccount entries to seed
     */
    public List<EntityBankAccount> buildFromCountry(CountryCode country) {
        log.info("Building default bank accounts from template for country={}", country);

        return switch (country) {
            case US -> buildUsDefaults();
            case IN -> buildInDefaults();
            case NP -> buildNpDefaults();
        };
    }

    private List<EntityBankAccount> buildUsDefaults() {
        return List.of(
                bankAccount(BankAccountType.CASH, "Petty Cash – US"),
                bankAccount(BankAccountType.OPERATING, "Main Operating Account – US"),
                bankAccount(BankAccountType.SAVINGS, "Savings Account – US")
        );
    }

    private List<EntityBankAccount> buildInDefaults() {
        return List.of(
                bankAccount(BankAccountType.CASH, "Cash in Hand – IN"),
                bankAccount(BankAccountType.OPERATING, "Current Account – IN"),
                bankAccount(BankAccountType.SAVINGS, "Savings Account – IN")
        );
    }

    private List<EntityBankAccount> buildNpDefaults() {
        return List.of(
                bankAccount(BankAccountType.CASH, "Petty Cash – NP"),
                bankAccount(BankAccountType.OPERATING, "Operating Account – NP"),
                bankAccount(BankAccountType.SAVINGS, "Savings Account – NP")
        );
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private static EntityBankAccount bankAccount(BankAccountType type, String label) {
        return EntityBankAccount.builder()
                .accountType(type)
                .accountLabel(label)
                .systemGenerated(true)
                .build();
    }
}
