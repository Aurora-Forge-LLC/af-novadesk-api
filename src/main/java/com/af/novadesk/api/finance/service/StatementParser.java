package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.entity.BankTransaction;

import java.io.InputStream;
import java.util.List;

/**
 * Strategy interface for parsing bank statement files into structured
 * {@link BankTransaction} rows.
 *
 * <p>Implementations exist for CSV and XLSX formats. The appropriate
 * implementation is resolved at runtime by {@link StatementParserFactory}.</p>
 */
public interface StatementParser {

    /**
     * Parses the given input stream and returns extracted bank transactions.
     *
     * @param statementId  UUID of the parent {@link com.af.novadesk.api.finance.entity.BankStatement}
     * @param inputStream  file content to parse
     * @param filePassword optional password for encrypted/protected files (may be null)
     * @return list of parsed bank transactions
     * @throws com.af.novadesk.api.finance.exception.StatementParseException if parsing fails
     */
    List<BankTransaction> parse(java.util.UUID statementId,
                                InputStream inputStream,
                                String filePassword);

    /**
     * Returns the file type (extension) this parser handles.
     */
    String supportedFileType();
}
