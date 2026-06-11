package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.constants.SuggestionStatus;
import com.af.novadesk.api.identity.entity.ShadowUser;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records a medium-confidence (score 60-79) match suggestion between
 * a bank transaction and an expense transaction, pending user review (LLR-BNK-02.4).
 */
@Entity
@Table(
        name = "bnk_suggested_matches",
        schema = "af_novadesk",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bnk_suggest_pair",
                columnNames = {"bank_transaction_id", "expense_transaction_id"}
        )
)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"bankTransaction", "expenseTransaction", "resolvedBy"})
public class SuggestedMatch extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_transaction_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_suggest_bank_txn"))
    private BankTransaction bankTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_transaction_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_suggest_expense"))
    private ExpenseTransaction expenseTransaction;

    @Column(name = "matching_score", nullable = false)
    private Integer matchingScore;

    @Column(name = "score_breakdown", columnDefinition = "jsonb")
    private String scoreBreakdown;  // JSON: {"amount":50,"date":20,"description":5}

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_by", nullable = false, length = 20)
    private SuggestedBy suggestedBy = SuggestedBy.SYSTEM;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_status", nullable = false, length = 20)
    private SuggestionStatus suggestionStatus = SuggestionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_shadow_user_id",
            foreignKey = @ForeignKey(name = "fk_bnk_suggest_resolved_by"))
    private ShadowUser resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // ── Inner enum ─────────────────────────────────────────────

    public enum SuggestedBy {
        SYSTEM,
        USER
    }
}
