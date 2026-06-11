package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.constants.VendorMappingConfidence;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

/**
 * Stores learned vendor name patterns from bank statement descriptions,
 * enabling automatic vendor recognition in future matching (LLR-BNK-02.5).
 *
 * <p>Example: bank description "AMZN MKTP US*1234ABC" → pattern "AMZN%" → maps to "Amazon" vendor.
 * Patterns are entity-specific to prevent cross-entity contamination.</p>
 */
@Entity
@Table(
        name = "bnk_vendor_mappings",
        schema = "af_novadesk",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bnk_vendor_mapping",
                columnNames = {"legal_entity_id", "bank_description_pattern"}
        )
)
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "vendor"})
public class VendorMapping extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_vm_legal_entity"))
    private LegalEntity legalEntity;

    @Column(name = "bank_description_pattern", nullable = false, length = 255)
    private String bankDescriptionPattern;  // SQL LIKE pattern, e.g. "AMZN%"

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_vm_vendor"))
    private Vendor vendor;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 20)
    private VendorMappingConfidence confidenceLevel = VendorMappingConfidence.USER_CONFIRMED;

    @Builder.Default
    @Column(name = "match_count", nullable = false)
    private Integer matchCount = 1;
}
