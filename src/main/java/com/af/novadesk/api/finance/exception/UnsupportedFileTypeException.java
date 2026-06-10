package com.af.novadesk.api.finance.exception;

/**
 * Thrown when the uploaded file type is not one of the supported formats (CSV, XLSX, PDF).
 */
public class UnsupportedFileTypeException extends FinanceBaseException {

    private static final String ERROR_CODE = "FIN_BNK_004";

    public UnsupportedFileTypeException(String fileType) {
        super(ERROR_CODE, "Unsupported file type: '" + fileType + "'. Allowed: CSV, XLSX");
    }
}
