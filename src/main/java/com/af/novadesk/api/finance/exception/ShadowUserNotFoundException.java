package com.af.novadesk.api.finance.exception;

import java.util.UUID;

/**
 * Thrown when a {@code ShadowUser} cannot be resolved for a given
 * {@code authUserId} — usually indicates a JWT with an unrecognised sub claim.
 */
public class ShadowUserNotFoundException extends FinanceBaseException {
    public ShadowUserNotFoundException(UUID authUserId) {
        super("FIN_SHADOW_001",
                String.format("Shadow user not found for authUserId: %s", authUserId));
    }
}
