package com.af.novadesk.api.finance.entity;



import com.af.novadesk.api.finance.constants.BankAccountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Represents a bank or cash account record belonging to a {@link LegalEntity}.
 *
 * <p>Separated from {@code LegalEntity} to satisfy 3NF: bank-account attributes
 * (account number, bank name, IBAN, balance) are functionally dependent only on
 * the bank-account record's own PK, not on other properties of the entity.</p>
 *
 * <p>Implements LLR-FIN-01.2: "Create default bank account records
 * (Cash, Operating Account)." Default records are seeded by the service layer
 * on entity approval using the {@link BankAccountType} enum.</p>
 */
@Entity
@Table(
        name = "entity_bank_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"legal_entity_id", "account_type"},
                        name = "uk_bank_account_entity_type"
                )
        }
)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity"})
public class EntityBankAccount extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Parent Reference
    // -------------------------------------------------------------------------

    /** The entity that owns this bank account record. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_bank_account_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Account Classification
    // -------------------------------------------------------------------------

    /** Functional type of this account (Cash, Operating, etc.). LLR-FIN-01.2 */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    @NotNull(message = "Bank account type is required")
    private BankAccountType accountType;

    /** Display name for this account record, e.g. "Main Operating Account – US". */
    @Column(name = "account_label", nullable = false, length = 150)
    @NotBlank(message = "Account label is required")
    @Size(max = 150, message = "Account label must not exceed 150 characters")
    private String accountLabel;

    // -------------------------------------------------------------------------
    // Optional Banking Details (populated when a real bank account is linked)
    // -------------------------------------------------------------------------

    /** Name of the bank institution, e.g. "Chase", "HDFC". */
    @Column(name = "bank_name", length = 150)
    @Size(max = 150, message = "Bank name must not exceed 150 characters")
    private String bankName;

    /** Bank account number; stored for reference only (not used for transactions here). */
    @Column(name = "account_number", length = 50)
    @Size(max = 50, message = "Account number must not exceed 50 characters")
    private String accountNumber;

    /** IBAN for international wire transfers (applicable for certain countries). */
    @Column(name = "iban", length = 34)
    @Size(max = 34, message = "IBAN must not exceed 34 characters")
    private String iban;

    /** SWIFT / BIC code of the bank. */
    @Column(name = "swift_code", length = 11)
    @Size(max = 11, message = "SWIFT code must not exceed 11 characters")
    private String swiftCode;

    // -------------------------------------------------------------------------
    // Flags
    // -------------------------------------------------------------------------

    /**
     * {@code true} for records seeded automatically on entity approval.
     * Distinguishes template defaults from user-added accounts.
     */
    @Builder.Default
    @Column(name = "is_system_generated", nullable = false)
    private boolean systemGenerated = false;
}
