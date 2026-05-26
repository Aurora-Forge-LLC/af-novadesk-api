package com.af.novadesk.api.organization.dto;

import com.af.novadesk.api.common.constants.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for an organization bank account record.
 *
 * <p>The {@code balance} field shows the current available funds in this
 * bank account, which can be used as the source for capital injections
 * into legal entities or inter-entity money transfers.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationBankInfoResponse {

    private UUID id;
    private UUID orgId;
    private UUID userId;
    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String iban;
    private String swiftCode;
    private String bankAddress;
    private String currency;
    private BigDecimal balance;
    private boolean primary;
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
