package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.dto.LeaveValidationResult;
import com.af.novadesk.api.payroll.entity.LeaveBalance;
import com.af.novadesk.api.payroll.entity.LeavePolicy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Stateless rule engine that encapsulates all leave policy validation logic.
 *
 * <p>For every leave request, this engine evaluates the employee's current
 * balance against the policy's rules — whether the leave is earned or upfront,
 * and what the borrowing limits are.</p>
 *
 * <h3>Borrow Rule Formula</h3>
 * <pre>
 * if currentBalance < 0:
 *     borrowLimit = 0          // Cannot borrow when already negative
 * elif currentBalance == 0:
 *     borrowLimit = monthlyAccrualRate × borrowMultiple  // Edge case
 * else:
 *     borrowLimit = currentBalance × borrowMultiple
 * </pre>
 */
@Service
public class LeaveRuleEngine {

    /**
     * Validates whether an employee can take the requested number of days
     * for a given leave policy.
     *
     * @param balance       the employee's current leave balance for this policy
     * @param policy        the leave policy with rules
     * @param requestedDays the number of days the employee wants to take
     * @return a validation result indicating allowed, partial, or rejected
     */
    public LeaveValidationResult validate(LeaveBalance balance,
                                           LeavePolicy policy,
                                           BigDecimal requestedDays) {

        // Unlimited policies always allow
        if (policy.getIsUnlimited()) {
            return LeaveValidationResult.allowed(requestedDays, BigDecimal.ZERO);
        }

        // Non-earned leave: simple balance check
        if (!policy.getIsEarned()) {
            BigDecimal available = balance.getAvailableDays();
            if (available.compareTo(requestedDays) >= 0) {
                return LeaveValidationResult.allowed(requestedDays, BigDecimal.ZERO);
            }
            BigDecimal shortfall = requestedDays.subtract(available);
            if (available.compareTo(BigDecimal.ZERO) > 0) {
                return LeaveValidationResult.partialAllowed(available, shortfall);
            }
            return LeaveValidationResult.rejected(
                    String.format("No %s balance available. Requested: %.1f days.",
                            policy.getName(), requestedDays));
        }

        // Earned leave: apply borrowing rules
        return validateEarnedLeave(balance, policy, requestedDays);
    }

    /**
     * Validates an earned leave request using the borrowing formula.
     */
    private LeaveValidationResult validateEarnedLeave(LeaveBalance balance,
                                                       LeavePolicy policy,
                                                       BigDecimal requestedDays) {

        BigDecimal earnedDays = balance.getEarnedDays();
        BigDecimal usedDays = balance.getUsedDays();
        BigDecimal pendingDays = balance.getPendingDays();

        BigDecimal currentBalance = earnedDays.subtract(usedDays).subtract(pendingDays);
        int borrowMultiple = policy.getEffectiveBorrowMultiple();

        BigDecimal borrowLimit;
        if (currentBalance.compareTo(BigDecimal.ZERO) < 0) {
            // Already negative — cannot borrow further
            borrowLimit = BigDecimal.ZERO;
        } else if (currentBalance.compareTo(BigDecimal.ZERO) == 0) {
            // Edge case: balance is zero, use monthly accrual rate as base
            borrowLimit = policy.getMonthlyAccrualRate()
                    .multiply(BigDecimal.valueOf(borrowMultiple));
        } else {
            // Normal case: borrow up to balance × multiple
            borrowLimit = currentBalance.multiply(BigDecimal.valueOf(borrowMultiple));
        }

        BigDecimal effectiveAvailable = currentBalance.add(borrowLimit);

        if (effectiveAvailable.compareTo(requestedDays) >= 0) {
            return LeaveValidationResult.allowed(requestedDays, borrowLimit);
        }

        // Not enough even with borrowing — allow what the borrow limit covers,
        // mark the remainder as unpaid
        if (effectiveAvailable.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal shortfall = requestedDays.subtract(effectiveAvailable);
            return LeaveValidationResult.partialAllowed(effectiveAvailable, shortfall);
        }
        // effectiveAvailable == 0 — cannot cover any days as paid
        return LeaveValidationResult.rejected(
                String.format(
                        "No balance available for '%s'. Current balance: %.1f, "
                                + "Borrow limit: %.1f, Requested: %.1f. "
                                + "Please apply for unpaid leave instead.",
                        policy.getName(), currentBalance, borrowLimit, requestedDays));
    }

    /**
     * Computes the monthly accrual amount for a given policy.
     * Used by the accrual scheduler.
     */
    public BigDecimal calculateMonthlyAccrual(LeavePolicy policy) {
        return policy.getMonthlyAccrualRate();
    }
}
