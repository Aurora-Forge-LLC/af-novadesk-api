package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

/**
 * Thrown when the uploaded file type is not supported (LLR-BNK-01).
 */
public class UnsupportedFileTypeException extends FinanceBaseException {
    public UnsupportedFileTypeException(String fileType) {
        super("FIN_BNK_004",
                String.format("Unsupported file type '%s'. Allowed: CSV, XLSX, XLS", fileType));
    }
}
