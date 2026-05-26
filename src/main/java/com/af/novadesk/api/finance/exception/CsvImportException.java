package com.af.novadesk.api.finance.exception;

/**
 * Thrown when CSV import of exchange rates fails at the file level
 * (e.g., unreadable file, wrong format, all rows invalid).
 *
 * <p>Maps to HTTP 422 Unprocessable Entity.
 */
public class CsvImportException extends RuntimeException {

    public CsvImportException(String message) {
        super(message);
    }

    public CsvImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
