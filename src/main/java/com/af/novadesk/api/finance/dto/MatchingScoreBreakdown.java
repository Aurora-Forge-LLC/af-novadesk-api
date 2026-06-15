package com.af.novadesk.api.finance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured breakdown of a matching score between a bank transaction
 * and an expense transaction (LLR-BNK-02.2).
 *
 * <p>Exposes which components contributed to the total score, enabling
 * the UI to display a transparent "why was this matched?" explanation.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Breakdown of how a matching score was calculated")
public class MatchingScoreBreakdown {

    /** Total score 0-100. */
    @Schema(description = "Total matching score (0-100)", example = "85")
    private int totalScore;

    /** Score from amount comparison (0 or 50). */
    @Schema(description = "Points from amount match (0 or 50)", example = "50")
    private int amountScore;

    /** Score from date proximity (0, 10, 20, or 30). */
    @Schema(description = "Points from date proximity (0/10/20/30)", example = "20")
    private int dateScore;

    /** Number of days between the two transaction dates. */
    @Schema(description = "Absolute days difference between dates", example = "1")
    private long daysDifference;

    /** Score from description similarity (0, 5, 10, or 15). */
    @Schema(description = "Points from description similarity (0/5/10/15)", example = "15")
    private int descriptionScore;

    /** How the description score was earned. Null if score is 0. */
    @Schema(description = "Method used for description matching", example = "VENDOR_NAME_MATCH")
    private String descriptionMatchMethod;

    /** The matching tier based on threshold. */
    @Schema(description = "Resulting tier: AUTO_MATCH, SUGGEST, or MANUAL_REVIEW", example = "AUTO_MATCH")
    private String tier;

    // ── Tier constants ─────────────────────────────────────────
    public static final String TIER_AUTO_MATCH = "AUTO_MATCH";
    public static final String TIER_SUGGEST = "SUGGEST";
    public static final String TIER_MANUAL_REVIEW = "MANUAL_REVIEW";

    // ── Description match method constants ─────────────────────
    public static final String METHOD_VENDOR_NAME = "VENDOR_NAME_MATCH";
    public static final String METHOD_LEVENSHTEIN_CLOSE = "LEVENSHTEIN_CLOSE";
    public static final String METHOD_LEVENSHTEIN_FUZZY = "LEVENSHTEIN_FUZZY";
    public static final String METHOD_NONE = "NONE";
}
