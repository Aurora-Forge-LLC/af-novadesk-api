package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.payroll.constants.LineItemType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Dynamic line item on an employee's payslip (LLR-PAY-04.5).
 *
 * <p>Stores earnings, deductions, and employer expenses using a flexible
 * code-based system. The {@code lineItemCode} drives calculation and
 * reporting (e.g. "NP_SSF_EMPLOYEE", "IN_PF_EMPLOYEE").</p>
 *
 * <p>This structure allows any jurisdiction's tax components to be stored
 * without schema changes — no hardcoded columns for SSF, PF, or tax
 * amounts.</p>
 */
@Entity
@Table(name = "pr_payslip_line_items", schema = "af_novadesk",
    indexes = {
        @Index(columnList = "payslip_id, line_item_type", name = "idx_pli_payslip_type"),
        @Index(columnList = "line_item_code", name = "idx_pli_code")
    })
@Filter(name = "organizationFilter",
    condition = "payslip_id IN (SELECT ps.id FROM af_novadesk.pr_payslips ps " +
                "JOIN af_novadesk.legal_entities le ON ps.legal_entity_id = le.id " +
                "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"payslip"})
public class PayslipLineItem extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_pli_payslip"))
    @NotNull(message = "Payslip is required")
    private Payslip payslip;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_item_type", nullable = false, length = 20)
    @NotNull
    private LineItemType lineItemType;          // EARNING, DEDUCTION, EMPLOYER_EXPENSE

    @Column(name = "line_item_code", nullable = false, length = 50)
    @NotBlank
    private String lineItemCode;                // e.g. "NP_SSF_EMPLOYEE", "IN_PF_EMPLOYEE"

    @Column(name = "line_item_description", nullable = false, length = 200)
    @NotBlank
    private String lineItemDescription;         // e.g. "Social Security Fund - Employee Contribution"

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank
    private String currencyCode;

    @Column(name = "display_order")
    private Integer displayOrder;               // Sort order on payslip
}
