package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when a {@link com.af.novadesk.api.finance.entity.Vendor} with the given
 * ID does not exist or is not accessible within the caller's organization scope.
 * Maps to HTTP 404 Not Found.
 */
public class VendorNotFoundException extends FinanceBaseException {

    public VendorNotFoundException(UUID vendorId) {
        super("FIN_VENDOR_001",
                String.format("Vendor not found: %s", vendorId));
    }

    public VendorNotFoundException(String message) {
        super("FIN_VENDOR_001", message);
    }
}
