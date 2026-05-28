package com.af.novadesk.api.expense.entity;

import com.af.novadesk.api.expense.constants.VendorType;
import com.af.novadesk.api.finance.entity.AbstractEntity;
import com.af.novadesk.api.finance.entity.Account;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * A payee that receives money from the organization (LLR-FIN-03.3).
 *
 * <p>Vendors are scoped to an {@code organizationId} — each organization maintains
 * its own vendor list. A vendor optionally remembers a {@code defaultAccount} so
 * the expense entry form can auto-fill the destination account when the vendor is
 * selected, reducing manual input on recurring payments.</p>
 *
 * <p>Vendor type is stored as a string enum — no separate lookup table is needed
 * for a fixed, infrequently changing list of categories.</p>
 *
 * <p>Relationships:
 * <ul>
 *   <li>{@code defaultAccount} – optional FK to {@link Account}; the expense category
 *       most commonly used when recording expenses for this vendor.</li>
 * </ul>
 * </p>
 */
import org.hibernate.annotations.Filter;

@Entity
@Table(
        name = "exp_vendors",
        schema = "af_novadesk",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"vendor_name", "organization_id"},
                        name = "uk_exp_vendor_name_org"
                )
        },
        indexes = {
                @Index(columnList = "organization_id", name = "idx_exp_vendor_org_id")
        }
)
@Filter(name = "organizationFilter",
        condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "defaultAccount")
public class Vendor extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Organization Scope
    // -------------------------------------------------------------------------

    /**
     * The organization this vendor belongs to.
     * Not a FK to a local organization table — the organization aggregate is
     * owned by AuthHub. Stored as a plain UUID for org-level scoping and filtering.
     */
    @Column(name = "organization_id", nullable = false, columnDefinition = "UUID")
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    /**
     * Full legal or trading name of the vendor.
     * Unique within the organization scope. LLR-FIN-03.3.
     */
    @Column(name = "vendor_name", nullable = false, length = 100)
    @NotBlank(message = "Vendor name is required")
    @Size(max = 100, message = "Vendor name must not exceed 100 characters")
    private String vendorName;

    /**
     * Business category of the vendor. Drives reporting groupings and
     * helps the finance team classify expenses quickly. LLR-FIN-03.3.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "vendor_type", nullable = false, length = 30)
    @NotNull(message = "Vendor type is required")
    private VendorType vendorType;

    // -------------------------------------------------------------------------
    // Registration Details
    // -------------------------------------------------------------------------

    /**
     * Government-issued tax or company registration number of the vendor.
     * Optional — not all vendors will have or share a tax ID. LLR-FIN-03.3.
     */
    @Column(name = "tax_id", length = 50)
    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;

    // -------------------------------------------------------------------------
    // Default Expense Category  (LLR-FIN-03.3)
    // -------------------------------------------------------------------------

    /**
     * The expense account most commonly used when recording expenses for this vendor.
     * When the user picks this vendor on the expense form, the destination account
     * is auto-filled with this value, reducing manual input on recurring payments.
     *
     * <p>Optional — null until the user explicitly sets it.
     * Nullable FK so that deleting an account does not cascade-delete vendors.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "default_account_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_ev_default_account")
    )
    private Account defaultAccount;
}
