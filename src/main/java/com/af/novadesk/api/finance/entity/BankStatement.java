package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.constants.StatementStatus;
import com.af.novadesk.api.identity.entity.ShadowUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an uploaded bank statement file for reconciliation (LLR-BNK-01).
 *
 * <p>Each statement is scoped to a single {@link LegalEntity} and
 * {@link EntityBankAccount}. After upload, the file is stored in MinIO/S3
 * and its metadata is persisted here. Transactions extracted from the file
 * are linked via {@link #transactions}.</p>
 *
 * <p>Lifecycle (statement_status): UPLOADED → PARSED, or FAILED if parsing
 * fails. SUPERSEDED when replaced by a newer upload for the same bank account + period.</p>
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
@ToString(exclude = {"legalEntity", "bankAccount", "uploadedBy", "transactions"})
public class BankStatement extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Entity & Bank Account
    // -------------------------------------------------------------------------

    /** The legal entity this statement belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "legal_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_stmt_entity")
    )
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    /** The bank account the statement is for. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "bank_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_stmt_bank_account")
    )
    @NotNull(message = "Bank account is required")
    private EntityBankAccount bankAccount;

    // -------------------------------------------------------------------------
    // Uploader
    // -------------------------------------------------------------------------

    /** The finance operator who uploaded the file. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "uploaded_by_shadow_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_stmt_uploaded_by")
    )
    @NotNull(message = "Uploaded by is required")
    private ShadowUser uploadedBy;

    // -------------------------------------------------------------------------
    // File Metadata
    // -------------------------------------------------------------------------

    /** Original filename as uploaded by the user. */
    @Column(name = "original_filename", nullable = false, length = 255)
    @NotBlank(message = "Original filename is required")
    @Size(max = 255, message = "Original filename must not exceed 255 characters")
    private String originalFilename;

    /** MinIO/S3 object key: {orgId}/bank-statements/{entityId}/{uuid}.{ext}. */
    @Column(name = "storage_key", nullable = false, length = 500)
    @NotBlank(message = "Storage key is required")
    @Size(max = 500, message = "Storage key must not exceed 500 characters")
    private String storageKey;

    /** File extension/type: CSV, XLSX. */
    @Column(name = "file_type", nullable = false, length = 10)
    @NotBlank(message = "File type is required")
    private String fileType;

    /** File size in bytes. */
    @Column(name = "file_size_bytes", nullable = false)
    @NotNull(message = "File size is required")
    private Integer fileSizeBytes;

    /** Whether the file was stored with server-side encryption. */
    @Builder.Default
    @Column(name = "is_encrypted", nullable = false)
    private Boolean isEncrypted = true;

    // -------------------------------------------------------------------------
    // Statement Period
    // -------------------------------------------------------------------------

    /** Statement period start date (inclusive). */
    @Column(name = "period_start", nullable = false)
    @NotNull(message = "Period start is required")
    private LocalDate periodStart;

    /** Statement period end date (inclusive). */
    @Column(name = "period_end", nullable = false)
    @NotNull(message = "Period end is required")
    private LocalDate periodEnd;

    // -------------------------------------------------------------------------
    // Parsing Result
    // -------------------------------------------------------------------------

    /** Number of transactions extracted from the file; set after parsing. */
    @Builder.Default
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount = 0;

    /** Optional free-text notes from the uploader (max 500 chars). */
    @Column(name = "notes", length = 500)
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    // -------------------------------------------------------------------------
    // Lifecycle State
    // -------------------------------------------------------------------------

    /** Current lifecycle state of this statement. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statement_status", nullable = false, length = 20)
    private StatementStatus statementStatus = StatementStatus.UPLOADED;

    // -------------------------------------------------------------------------
    // Owned Relationships
    // -------------------------------------------------------------------------

    /** Transactions extracted from this statement. */
    @Builder.Default
    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BankTransaction> transactions = new ArrayList<>();
}
