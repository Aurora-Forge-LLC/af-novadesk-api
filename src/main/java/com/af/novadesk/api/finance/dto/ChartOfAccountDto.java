package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.common.constants.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

/**
 * ChartOfAccount (outbound only — auto-generated server-side).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartOfAccountDto {
    private UUID id;
    private String accountCode;
    private String accountName;
    private AccountType accountType;
    private String description;
    private UUID parentAccountId;
    private boolean postable;
    private boolean systemGenerated;
    private Status status;
}
