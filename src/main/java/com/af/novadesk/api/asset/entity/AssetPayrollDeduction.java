package com.af.novadesk.api.asset.entity;

import com.af.novadesk.api.asset.constants.AssetDeductionStatus;
import com.af.novadesk.api.asset.constants.WriteOffReason;
import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Records an irrevocable payroll deduction for a DAMAGED or LOST asset
 * approved with action {@code DEDUCT_FROM_PAY}.
 *
 * <p>Created automatically when {@link AssetWriteOff} is approved with
 * {@code WriteOffAction.DEDUCT_FROM_PAY}. The deduction is applied by the
 * payroll module during salary calculation for whichever payroll batch period
 * covers {@code deductionDate}.</p>
 *
 * <p>Once created this record is immutable — the decision cannot be waived
 * or reversed via payroll review.</p>
 */
@Entity
@Table(
    name   = "ast_payroll_deductions",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "organization_id, employee_id, deduction_date, status",
               name = "idx_ast_pd_org_emp_date_status"),
        @Index(columnList = "write_off_id", name = "idx_ast_pd_write_off"),
    },
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"write_off_id"},
        name        = "uk_ast_pd_write_off"
    )
)
@Filter(name = "organizationFilter", condition = "organization_id = :orgId")
@AttributeOverride(name = "status", column = @Column(name = "record_status"))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"writeOff"})
public class AssetPayrollDeduction extends AbstractEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "write_off_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_pd_write_off"))
    @NotNull
    private AssetWriteOff writeOff;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    /** cm_employees.id — the last custodian who held the asset. */
    @Column(name = "employee_id", nullable = false)
    @NotNull
    private UUID employeeId;

    /** Depreciated value (NBV) of the asset at write-off request time. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @NotNull
    @DecimalMin(value = "0.00", message = "Amount must be zero or greater")
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    @NotNull
    private String currencyCode;

    /**
     * The date on which this deduction was decided (= write-off approval date).
     * Payroll picks it up in whichever batch period covers this date.
     */
    @Column(name = "deduction_date", nullable = false)
    @NotNull
    private LocalDate deductionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "write_off_reason", nullable = false, length = 20)
    @NotNull
    private WriteOffReason writeOffReason;

    /** Denormalized asset label (assetType + serialNumber) for payslip line item description. */
    @Column(name = "asset_label", nullable = false, length = 300)
    @NotNull
    @Size(max = 300)
    private String assetLabel;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "deduction_status", nullable = false, length = 20)
    @NotNull
    private AssetDeductionStatus deductionStatus = AssetDeductionStatus.PENDING;

    /** Populated once the deduction is applied by a payroll batch. */
    @Column(name = "payroll_batch_id")
    private UUID payrollBatchId;

    /** Populated once the payslip line item is created. */
    @Column(name = "payslip_id")
    private UUID payslipId;
}
