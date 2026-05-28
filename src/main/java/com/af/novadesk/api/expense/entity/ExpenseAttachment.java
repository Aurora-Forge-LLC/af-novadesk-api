package com.af.novadesk.api.expense.entity;

import org.hibernate.annotations.Filter;

import com.af.novadesk.api.finance.entity.AbstractEntity;
import com.af.novadesk.api.identity.entity.ShadowUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * An encrypted file (invoice, receipt, or supporting document) attached to an
 * {@link ExpenseTransaction} (LLR-FIN-03.4).
 *
 * <p>The actual file content is stored in an external object store (S3 / MinIO).
 * Only the metadata and the storage reference key are persisted here.
 * The key is used by the file service to retrieve, stream, or delete the object.</p>
 *
 * <p>Access to attachments is restricted to users with the
 * {@code VIEW_FINANCIAL_DOCUMENTS} permission (LLR-FIN-03.4).</p>
 *
 * <p>Constraints:
 * <ul>
 *   <li>Allowed file types: PDF, PNG, JPG, JPEG (validated at service layer)</li>
 *   <li>Maximum file size: 5 MB (5 242 880 bytes, enforced at service layer)</li>
 *   <li>Files are encrypted at rest ({@code isEncrypted = true} by default)</li>
 * </ul>
 * </p>
 */
@Entity
@Table(
        name = "exp_expense_attachments",
        schema = "af_novadesk",
        indexes = {
                // Primary lookup: all attachments for a given transaction
                @Index(columnList = "expense_transaction_id", name = "idx_exp_attach_transaction_id")
        }
)
@Filter(name = "organizationFilter",
        condition = "expense_transaction_id IN (SELECT et.id FROM af_novadesk.exp_expense_transactions et " +
                    "WHERE et.legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId))")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"expenseTransaction", "uploadedBy"})
public class ExpenseAttachment extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Parent Transaction
    // -------------------------------------------------------------------------

    /**
     * The expense transaction this attachment belongs to.
     * Many attachments can be linked to one transaction.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "expense_transaction_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ea_expense_transaction")
    )
    @NotNull(message = "Expense transaction is required")
    private ExpenseTransaction expenseTransaction;

    // -------------------------------------------------------------------------
    // File Metadata
    // -------------------------------------------------------------------------

    /**
     * The original filename as uploaded by the user (e.g. "aws-invoice-may-2025.pdf").
     * Stored for display purposes only — the actual file is referenced by {@link #storageKey}.
     */
    @Column(name = "original_file_name", nullable = false, length = 255)
    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name must not exceed 255 characters")
    private String originalFileName;

    /**
     * MIME type / extension of the uploaded file.
     * Allowed values: PDF, PNG, JPG, JPEG. Validated at the service layer
     * before the file is written to object storage. LLR-FIN-03.4.
     */
    @Column(name = "file_type", nullable = false, length = 10)
    @NotBlank(message = "File type is required")
    @Size(max = 10)
    private String fileType;

    /**
     * Size of the uploaded file in bytes.
     * Maximum allowed: 5 242 880 bytes (5 MB). Validated at the service layer. LLR-FIN-03.4.
     */
    @Column(name = "file_size_bytes", nullable = false)
    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Integer fileSizeBytes;

    // -------------------------------------------------------------------------
    // Storage Reference
    // -------------------------------------------------------------------------

    /**
     * Object store key used to retrieve, stream, or delete the file from S3 / MinIO.
     * Convention: {@code "<organizationId>/<legalEntityId>/<transactionId>/<uuid>.<ext>"}.
     * Never expose this key directly to clients — the file service issues
     * short-lived pre-signed URLs instead.
     */
    @Column(name = "storage_key", nullable = false, length = 500)
    @NotBlank(message = "Storage key is required")
    @Size(max = 500, message = "Storage key must not exceed 500 characters")
    private String storageKey;

    /**
     * Whether the file is encrypted at rest in the object store.
     * Always {@code true} for production uploads. LLR-FIN-03.4.
     */
    @Builder.Default
    @Column(name = "is_encrypted", nullable = false)
    private Boolean isEncrypted = true;

    // -------------------------------------------------------------------------
    // Audit
    // -------------------------------------------------------------------------

    /**
     * The finance operator who uploaded this file.
     * Resolved from the JWT {@code sub} claim at upload time.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "uploaded_by_shadow_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ea_uploaded_by")
    )
    @NotNull(message = "Uploaded by is required")
    private ShadowUser uploadedBy;
}
