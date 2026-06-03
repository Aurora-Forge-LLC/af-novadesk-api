package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.finance.constants.BankAccountType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Lightweight entity mapping to the {@code bank_account_templates} table.
 * Holds country-specific default bank account data that is seeded into
 * the {@link EntityBankAccount} table when a legal entity is approved.
 *
 * <p>This is a reference-data entity and does NOT extend {@link com.af.novadesk.api.common.entity.AbstractEntity}.</p>
 */
@Entity
@Table(name = "bank_account_templates", schema = "af_novadesk")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", length = 5, nullable = false)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20, nullable = false)
    private BankAccountType accountType;

    @Column(name = "account_label", length = 150, nullable = false)
    private String accountLabel;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
