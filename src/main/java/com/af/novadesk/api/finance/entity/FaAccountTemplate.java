package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Lightweight entity mapping to the {@code fa_account_templates} table.
 * Holds country-specific default funding account data that is seeded
 * into the {@link Account} (fa_accounts) table when a legal entity is approved.
 *
 * <p>This is a reference-data entity and does NOT extend {@link com.af.novadesk.api.common.entity.AbstractEntity}.</p>
 */
@Entity
@Table(name = "fa_account_templates", schema = "af_novadesk")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaAccountTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", length = 5, nullable = false)
    private String countryCode;

    @Column(name = "account_code", length = 30, nullable = false)
    private String accountCode;

    @Column(name = "account_name", length = 150, nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", length = 50, nullable = false)
    private AccountRole accountRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 30, nullable = false)
    private AccountType accountType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
