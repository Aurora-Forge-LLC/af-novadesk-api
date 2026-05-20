package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.BankAccountType;
import com.af.novadesk.api.common.constants.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

/**
 * EntityBankAccount (outbound only — auto-seeded server-side).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityBankAccountDto {
    private UUID id;
    private BankAccountType accountType;
    private String accountLabel;
    private String bankName;
    private String accountNumber;
    private String iban;
    private String swiftCode;
    private boolean systemGenerated;
    private Status status;
}