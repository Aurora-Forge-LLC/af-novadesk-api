package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.CmEmployee;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root for individual employee payslips (LLR-PAY-02.5, PAY-04).
 *
 * <p>Generated as a child of a {@link PayrollBatch}. Contains dynamic
 * {@link PayslipLineItem} rows for earnings, deductions, and employer
 * expenses (LLR-PAY-04.5).</p>
 *
 * <p>Outbox event: {@code PAYSLIP_GENERATED} — triggers PDF generation
 * and employee notification.</p>
 */
@Entity
@Table(name = "pr_payslips", schema = "af_novadesk",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"payroll_batch_id", "employee_id"},
            name = "uk_ps_batch_employee")
    },
    indexes = {
        @Index(columnList = "employee_id, pay_period_start",  name = "idx_ps_employee_period"),
        @Index(columnList = "organization_id, pay_period_start", name = "idx_ps_org_period")
    })
@Filter(name = "organizationFilter", condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"payrollBatch", "employee", "lineItems"})
public class Payslip extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_batch_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_ps_batch"))
    @NotNull(message = "Payroll batch is required")
    private PayrollBatch payrollBatch;

    /** Org scope — denormalized for @Filter and queries. */
    @Column(name = "organization_id", nullable = false)
    @NotNull
    private java.util.UUID organizationId;

    /**
     * Loose reference to {@code cm_employee_entity_assignments.id} — the primary
     * entity assignment that processed this payslip.
     */
    @Column(name = "entity_assignment_id")
    private java.util.UUID entityAssignmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_ps_employee"))
    @NotNull(message = "Employee is required")
    private CmEmployee employee;

    // -------------------------------------------------------------------------
    // Period
    // -------------------------------------------------------------------------

    @Column(name = "pay_period_start", nullable = false)
    @NotNull
    private LocalDate payPeriodStart;

    @Column(name = "pay_period_end", nullable = false)
    @NotNull
    private LocalDate payPeriodEnd;

    @Column(name = "payment_date", nullable = false)
    @NotNull
    private LocalDate paymentDate;

    // -------------------------------------------------------------------------
    // Attendance Summary
    // -------------------------------------------------------------------------

    @Column(name = "total_working_days", nullable = false)
    @NotNull
    private Integer totalWorkingDays;

    @Column(name = "days_worked", nullable = false, precision = 4, scale = 1)
    @NotNull
    private BigDecimal daysWorked;

    @Column(name = "paid_leave_days", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    @NotNull
    private BigDecimal paidLeaveDays = BigDecimal.ZERO;

    @Column(name = "sick_leave_days", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    @NotNull
    private BigDecimal sickLeaveDays = BigDecimal.ZERO;

    @Column(name = "unpaid_leave_days", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    @NotNull
    private BigDecimal unpaidLeaveDays = BigDecimal.ZERO;

    // -------------------------------------------------------------------------
    // Financial
    // -------------------------------------------------------------------------

    @Column(name = "gross_salary", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal grossSalary;

    @Column(name = "total_deductions", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal totalDeductions;

    @Column(name = "net_salary", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal netSalary;

    // -------------------------------------------------------------------------
    // YTD Totals
    // -------------------------------------------------------------------------

    @Column(name = "ytd_gross_earnings", precision = 19, scale = 4)
    private BigDecimal ytdGrossEarnings;

    @Column(name = "ytd_taxes", precision = 19, scale = 4)
    private BigDecimal ytdTaxes;

    // -------------------------------------------------------------------------
    // Currency
    // -------------------------------------------------------------------------

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotNull
    private String currencyCode;

    // -------------------------------------------------------------------------
    // PDF Storage
    // -------------------------------------------------------------------------

    @Column(name = "payslip_pdf_path", length = 500)
    private String payslipPdfPath;              // MinIO encrypted path

    @Column(name = "is_downloaded")
    @Builder.Default
    private Boolean isDownloaded = false;

    // -------------------------------------------------------------------------
    // Children
    // -------------------------------------------------------------------------

    @Builder.Default
    @OneToMany(mappedBy = "payslip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PayslipLineItem> lineItems = new ArrayList<>();
}
