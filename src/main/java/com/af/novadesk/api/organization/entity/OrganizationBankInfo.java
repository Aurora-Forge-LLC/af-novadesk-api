package com.af.novadesk.api.organization.entity;

import com.af.novadesk.api.finance.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents an organization-level bank account record.
 *
 * <p>This entity stores bank account information at the organization level,
 * managed exclusively by users with the {@code Super_admin} role. Each
 * organization can have multiple bank accounts, but only one can be marked
 * as primary.</p>
 *
 * <p>The {@code balance} field tracks the current available funds in this
 * bank account, which serve as the source for capital injections into legal
 * entities and inter-entity money transfers.</p>
 *
 * <p>Key business rules:</p>
 * <ul>
 *   <li>{@code org_id} + {@code account_number} must be unique</li>
 *   <li>Only {@code Super_admin} users can create/update/delete these records</li>
 *   <li>The {@code user_id} is automatically populated from the authenticated JWT</li>
 *   <li>The {@code balance} cannot go negative — checked before any debit operation</li>
 * </ul>
 */
@Entity
@Table(
        name = "organization_bank_info",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"org_id", "account_number"},
                        name = "uk_org_bank_account_number"
                )
        }
)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrganizationBankInfo extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Organization & User References
    // -------------------------------------------------------------------------

    /** The organization that owns this bank account record. */
    @Column(name = "org_id", nullable = false)
    @NotNull(message = "Organization ID is required")
    private UUID orgId;

    /** The Super_admin user who manages this record (from JWT sub claim). */
    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

    // -------------------------------------------------------------------------
    // Bank Details
    // -------------------------------------------------------------------------

    /** Name of the bank institution, e.g. "Chase", "HDFC", "Nabil Bank". */
    @Column(name = "bank_name", nullable = false, length = 150)
    @NotBlank(message = "Bank name is required")
    @Size(max = 150, message = "Bank name must not exceed 150 characters")
    private String bankName;

    /** Name of the account holder as registered with the bank. */
    @Column(name = "account_holder_name", nullable = false, length = 200)
    @NotBlank(message = "Account holder name is required")
    @Size(max = 200, message = "Account holder name must not exceed 200 characters")
    private String accountHolderName;

    /** Bank account number. */
    @Column(name = "account_number", nullable = false, length = 50)
    @NotBlank(message = "Account number is required")
    @Size(max = 50, message = "Account number must not exceed 50 characters")
    private String accountNumber;

    /** International Bank Account Number (IBAN) for international transfers. */
    @Column(name = "iban", length = 34)
    @Size(max = 34, message = "IBAN must not exceed 34 characters")
    private String iban;

    /** SWIFT / BIC code of the bank. */
    @Column(name = "swift_code", length = 11)
    @Size(max = 11, message = "SWIFT code must not exceed 11 characters")
    private String swiftCode;

    /** Physical address of the bank branch. */
    @Column(name = "bank_address", length = 500)
    @Size(max = 500, message = "Bank address must not exceed 500 characters")
    private String bankAddress;

    /** ISO 4217 currency code, e.g. USD, NPR, EUR. */
    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a valid ISO 4217 3-letter code")
    private String currency;

    // -------------------------------------------------------------------------
    // Balance (Current Available Funds)
    // -------------------------------------------------------------------------

    /**
     * Current available balance in this bank account.
     *
     * <p>This balance represents the funds available for capital injections
     * into legal entities and inter-entity money transfers. It starts at
     * {@code 0.0000} when the account is created and is updated via the
     * credit/debit endpoints.</p>
     */
    @Builder.Default
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    @DecimalMin(value = "0.0000", message = "Balance cannot be negative")
    private BigDecimal balance = BigDecimal.ZERO;

    // -------------------------------------------------------------------------
    // Flags
    // -------------------------------------------------------------------------

    /**
     * Whether this is the primary bank account for the organization.
     * Only one account per organization can be primary at a time.
     */
    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;
}
