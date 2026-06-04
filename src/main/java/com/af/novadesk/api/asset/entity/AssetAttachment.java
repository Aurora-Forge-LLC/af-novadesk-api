package com.af.novadesk.api.asset.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.identity.entity.ShadowUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * A file (manual, purchase invoice, warranty certificate, photo) attached to an {@link Asset}.
 *
 * <p>The actual file content lives in MinIO/S3; only the metadata and
 * storage key are persisted here. The storage key is used to generate
 * short-lived pre-signed download URLs — never expose it directly to clients.</p>
 *
 * <p>Allowed types: PDF, PNG, JPG, JPEG, DOCX, XLSX. Max size: 10 MB.</p>
 */
@Entity
@Table(
    name   = "ast_asset_attachments",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "asset_id",       name = "idx_ast_attach_asset_id"),
        @Index(columnList = "organization_id", name = "idx_ast_attach_org"),
    }
)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"asset", "uploadedBy"})
public class AssetAttachment extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_attach_asset"))
    @NotNull
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_shadow_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_attach_uploaded_by"))
    @NotNull
    private ShadowUser uploadedBy;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    /** Original filename as uploaded (display only — storage uses {@link #storageKey}). */
    @Column(name = "original_file_name", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String originalFileName;

    /** File extension / MIME type: PDF, PNG, JPG, JPEG, DOCX, XLSX. */
    @Column(name = "file_type", nullable = false, length = 10)
    @NotBlank
    @Size(max = 10)
    private String fileType;

    /** File size in bytes. Max 10 MB enforced at service layer and DB constraint. */
    @Column(name = "file_size_bytes", nullable = false)
    @NotNull
    @Positive
    private Integer fileSizeBytes;

    /**
     * Object store key for retrieval/deletion.
     * Convention: {@code assets/attachments/<assetId>/<uuid>.<ext>}
     */
    @Column(name = "storage_key", nullable = false, length = 500)
    @NotBlank
    @Size(max = 500)
    private String storageKey;
}
