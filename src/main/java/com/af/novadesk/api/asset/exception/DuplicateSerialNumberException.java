package com.af.novadesk.api.asset.exception;

import com.af.novadesk.api.finance.exception.DuplicateEntityException;

public class DuplicateSerialNumberException extends DuplicateEntityException {
    public DuplicateSerialNumberException(String serialNumber) {
        super("serialNumber", serialNumber);
    }
}
