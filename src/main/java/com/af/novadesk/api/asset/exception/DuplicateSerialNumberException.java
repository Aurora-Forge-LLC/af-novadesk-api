package com.af.novadesk.api.asset.exception;

import com.af.novadesk.api.finance.exception.DuplicateEntityException;

public class DuplicateSerialNumberException extends DuplicateEntityException {
    private final String serialNumber;

    public DuplicateSerialNumberException(String serialNumber) {
        super("serialNumber", serialNumber);
        this.serialNumber = serialNumber;
    }

    @Override
    public String getErrorCode() {
        return "AST_002";
    }

    @Override
    public String getMessage() {
        return "Asset with serialNumber '" + serialNumber + "' already exists";
    }
}
