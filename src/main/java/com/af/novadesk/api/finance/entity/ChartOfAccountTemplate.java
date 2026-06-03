package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.finance.constants.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Lightweight entity mapping to the {@code coa_templates} table.
 * Holds country-specific default Chart of Accounts data that is seeded
 * into the {@link ChartOfAccount} table when a legal entity is approved.
 *
 * <p>This is a reference-data entity and does NOT extend {@link com.af.novadesk.api.common.entity.AbstractEntity}
 * because it is not org-scoped or status-tracked.</p>
 */
@Entity
@Table(name = "coa_templates", schema = "af_novadesk")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartOfAccountTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", length = 5, nullable = false)
    private String countryCode;

    @Column(name = "account_code", length = 20, nullable = false)
    private String accountCode;

    @Column(name = "account_name", length = 150, nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20, nullable = false)
    private AccountType accountType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "parent_account_code", length = 20)
    private String parentAccountCode;

    @Column(name = "is_postable", nullable = false)
    private boolean postable;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
