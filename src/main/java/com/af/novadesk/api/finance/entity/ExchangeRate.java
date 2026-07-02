package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.finance.constants.ExchangeRateApprovalStatus;
import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores a daily exchange rate snapshot between two ISO 4217 currency codes.
 *
 * <p>Used by {@code DefaultExchangeRateService} to resolve the conversion rate
 * needed for USD reporting during capital injection (LLR-FIN-02.3).  If no rate
 * exists for the exact transaction date, the service walks back up to
 * {@code finance.funding.lookback-days} days to find the nearest available rate.</p>
 *
 * <p>Manual rates (entered by Finance team for air-gapped mode) require a
 * {@code createdBy} (submitter) and {@code approvedBy} (approver) for audit.</p>
 *
 * <p>Organisation-scoped via {@link #organizationId} — every rate belongs to
 * exactly one organisation.  The Hibernate {@code organizationFilter} is
 * automatically applied to all queries so cross-org data leakage is
 * impossible at the persistence layer.</p>
 */
@Entity
@Table(
        name = "fa_exchange_rates",
        schema = "af_novadesk"
)
@Filter(name = "organizationFilter",
        condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExchangeRate extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Currency Pair
    // -------------------------------------------------------------------------

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank(message = "Source currency is required")
    @Size(min = 3, max = 3, message = "Source currency must be exactly 3 characters")
    private String sourceCurrency;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "target_currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank(message = "Target currency is required")
    @Size(min = 3, max = 3, message = "Target currency must be exactly 3 characters")
    private String targetCurrency;

    // -------------------------------------------------------------------------
    // Rate
    // -------------------------------------------------------------------------

    @Column(name = "rate_date", nullable = false)
    @NotNull(message = "Rate date is required")
    private LocalDate rateDate;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    @NotNull(message = "Exchange rate is required")
    @DecimalMin(value = "0.000001", message = "Exchange rate must be positive")
    private BigDecimal exchangeRate;

    // -------------------------------------------------------------------------
    // Provenance Audit
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_source", nullable = false, length = 30)
    @NotNull(message = "Rate source is required")
    private RateSource rateSource;

    /** User or system that submitted this rate entry. */
    @Column(name = "created_by", length = 100)
    @Size(max = 100)
    private String createdBy;

    /** Approver required only for MANUAL rates (LLR-FIN-02.3). */
    @Column(name = "approved_by", length = 100)
    @Size(max = 100)
    private String approvedBy;

    /** Timestamp when this rate was approved. Null until approved. */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Approval workflow state. Defaults to APPROVAL_PENDING on creation. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ExchangeRateApprovalStatus approvalStatus = ExchangeRateApprovalStatus.APPROVAL_PENDING;

    // -------------------------------------------------------------------------
    // Multi-Tenancy
    // -------------------------------------------------------------------------

    /** Organisation that owns this exchange rate. */
    @Column(name = "organization_id")
    private UUID organizationId;
}

