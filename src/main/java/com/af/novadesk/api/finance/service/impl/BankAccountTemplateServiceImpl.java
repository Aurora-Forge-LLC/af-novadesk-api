package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.constants.BankAccountType;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import com.af.novadesk.api.finance.service.BankAccountTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default implementation of {@link BankAccountTemplateService}.
 * Builds default bank accounts per country template (LLR-FIN-01.2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountTemplateServiceImpl implements BankAccountTemplateService {

    @Override
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
                bankAccount(BankAccountType.CASH,      "Petty Cash – US"),
                bankAccount(BankAccountType.OPERATING, "Main Operating Account – US"),
                bankAccount(BankAccountType.SAVINGS,   "Savings Account – US")
        );
    }

    private List<EntityBankAccount> buildInDefaults() {
        return List.of(
                bankAccount(BankAccountType.CASH,      "Cash in Hand – IN"),
                bankAccount(BankAccountType.OPERATING, "Current Account – IN"),
                bankAccount(BankAccountType.SAVINGS,   "Savings Account – IN")
        );
    }

    private List<EntityBankAccount> buildNpDefaults() {
        return List.of(
                bankAccount(BankAccountType.CASH,      "Petty Cash – NP"),
                bankAccount(BankAccountType.OPERATING, "Operating Account – NP"),
                bankAccount(BankAccountType.SAVINGS,   "Savings Account – NP")
        );
    }

    private static EntityBankAccount bankAccount(BankAccountType type, String label) {
        return EntityBankAccount.builder()
                .accountType(type)
                .accountLabel(label)
                .systemGenerated(true)
                .build();
    }
}
