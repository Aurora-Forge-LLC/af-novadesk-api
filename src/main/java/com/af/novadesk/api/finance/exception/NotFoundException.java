package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

public class NotFoundException extends FinanceBaseException {

    public NotFoundException(String message) {
        super("FIN_NOT_FOUND", message);
    }

    protected NotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}

