package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.constants.ReconciliationStatus;
import com.af.novadesk.api.finance.constants.StatementStatus;
import com.af.novadesk.api.identity.entity.ShadowUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

/**
 * Represents an uploaded bank statement file for reconciliation (LLR-BNK-01.1).
 *
 * <p>Each statement belongs to a {@link LegalEntity} and a specific
 * {@link EntityBankAccount}, covers a statement period, and contains the
 * metadata about the uploaded file. Transactions extracted from the file
 * are stored separately in {@code bnk_transactions} (future phase).</p>
 *
 * <p>Org-scoping uses the {@code legal_entity_id → legal_entities.organization_id}
 * chain, enforced via the {@code organizationFilter} Hibernate filter.</p>
 */
@Entity
@Table(
        name = "bnk_statements",
        schema = "af_novadesk"
)
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "bankAccount", "uploadedBy"})
public class BankStatement extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Entity & Bank Account References
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "legal_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_statement_legal_entity")
    )
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "bank_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_statement_bank_account")
    )
    @NotNull(message = "Bank account is required")
    private EntityBankAccount bankAccount;

    // -------------------------------------------------------------------------
    // Uploader
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "uploaded_by_shadow_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_statement_uploaded_by")
    )
    @NotNull(message = "Uploaded by is required")
    private ShadowUser uploadedBy;

    // -------------------------------------------------------------------------
    // File Metadata
    // -------------------------------------------------------------------------

    @Column(name = "original_filename", nullable = false, length = 255)
    @NotBlank(message = "Original filename is required")
    @Size(max = 255, message = "Original filename must not exceed 255 characters")
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, length = 500)
    @NotBlank(message = "Storage key is required")
    @Size(max = 500, message = "Storage key must not exceed 500 characters")
    private String storageKey;

    @Column(name = "file_type", nullable = false, length = 10)
    @NotBlank(message = "File type is required")
    private String fileType;      // CSV, XLSX, XLS, PDF

    @Column(name = "file_size_bytes", nullable = false)
    @NotNull(message = "File size is required")
    private Integer fileSizeBytes;

    /**
     * Whether the uploaded file is encrypted (e.g., password-protected Excel).
     * Defaults to {@code false}. Set to {@code true} if a file password was provided.
     */
    @Builder.Default
    @Column(name = "is_encrypted", nullable = false)
    private boolean isEncrypted = false;

    /** SHA-256 hash of the file password (not stored in plaintext). Null if no password. */
    @Column(name = "file_password_hash", length = 255)
    private String filePasswordHash;

    // -------------------------------------------------------------------------
    // Statement Period
    // -------------------------------------------------------------------------

    @Column(name = "period_start", nullable = false)
    @NotNull(message = "Period start date is required")
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    @NotNull(message = "Period end date is required")
    private LocalDate periodEnd;

    // -------------------------------------------------------------------------
    // Processing State
    // -------------------------------------------------------------------------

    @Builder.Default
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount = 0;

    @Column(name = "notes", length = 500)
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    /** SHA-256 hash of the file content for content-based deduplication. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statement_status", nullable = false, length = 20)
    private StatementStatus statementStatus = StatementStatus.UPLOADED;

    /** Overall reconciliation state for this statement. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 20)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.UNMATCHED;
}
