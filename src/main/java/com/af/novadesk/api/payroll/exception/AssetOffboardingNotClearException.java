package com.af.novadesk.api.payroll.exception;

import com.af.novadesk.api.asset.dto.OffboardingAssetCheckDto;

/**
 * Thrown when attempting to hard-delete an employee who still has unreturned assets.
 * Returns 409 Conflict with the offboarding check details so the client knows which
 * assets need to be resolved first.
 */
public class AssetOffboardingNotClearException extends RuntimeException {

    private final OffboardingAssetCheckDto checkResult;

    public AssetOffboardingNotClearException(OffboardingAssetCheckDto checkResult) {
        super("Cannot hard-delete employee " + checkResult.getEmployeeId()
                + " — " + checkResult.getUnreturnedCount() + " unreturned asset(s) remain");
        this.checkResult = checkResult;
    }

    public OffboardingAssetCheckDto getCheckResult() {
        return checkResult;
    }
}
