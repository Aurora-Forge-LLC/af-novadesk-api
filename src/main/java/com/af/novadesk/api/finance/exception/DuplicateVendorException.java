package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

/**
 * Thrown when an attempt is made to create or rename a
 * {@link com.af.novadesk.api.finance.entity.Vendor} with a name that already
 * exists within the same organization. Maps to HTTP 409 Conflict.
 */
public class DuplicateVendorException extends FinanceBaseException {

    public DuplicateVendorException(String vendorName) {
        super("FIN_VENDOR_002",
                String.format("Vendor with name '%s' already exists in this organization", vendorName));
    }
}
