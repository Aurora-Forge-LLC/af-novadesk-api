package com.af.novadesk.api.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating an organization bank account record.
 *
 * <p>The {@code orgId} and {@code userId} are populated server-side from
 * the authenticated JWT claims, so they are not part of this request body.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationBankInfoRequest {

    @NotBlank(message = "Bank name is required")
    @Size(max = 150, message = "Bank name must not exceed 150 characters")
    private String bankName;

    @NotBlank(message = "Account holder name is required")
    @Size(max = 200, message = "Account holder name must not exceed 200 characters")
    private String accountHolderName;

    @NotBlank(message = "Account number is required")
    @Size(max = 50, message = "Account number must not exceed 50 characters")
    private String accountNumber;

    @Size(max = 34, message = "IBAN must not exceed 34 characters")
    private String iban;

    @Size(max = 11, message = "SWIFT code must not exceed 11 characters")
    private String swiftCode;

    @Size(max = 500, message = "Bank address must not exceed 500 characters")
    private String bankAddress;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a valid ISO 4217 3-letter code")
    private String currency;

    /** Whether this should be the primary bank account for the organization. */
    private boolean primary;
}
