package com.af.novadesk.api.finance.dto;

import java.math.BigDecimal;

/**
 * Type-safe holder for dual-currency aggregate sums returned by
 * {@code SELECT SUM(local), SUM(usd)} repository queries in the
 * finance module.
 *
 * <p>Replaces fragile {@code Object[]} casting that can break when
 * Hibernate's result structure changes due to {@code @Filter} or
 * other query transformations.</p>
 *
 * @param local the aggregated local-currency amount
 * @param usd   the aggregated USD-converted amount
 */
public record AggregateSum(BigDecimal local, BigDecimal usd) {
}
