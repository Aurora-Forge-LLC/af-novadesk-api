package com.af.novadesk.api.finance.service;

import java.io.InputStream;
import java.util.List;

/**
 * Parser interface for extracting transactions from bank statement files (LLR-BNK-01).
 *
 * <p>Each implementation handles a specific file format (CSV, XLSX, etc.)
 * and is discovered automatically by {@link BankStatementParserFactory}.</p>
 */
public interface BankStatementParser {

    /**
     * Whether this parser supports the given file type.
     *
     * @param fileType uppercase file extension, e.g. "CSV", "XLSX", "XLS"
     * @return true if this parser can handle the file type
     */
    boolean supports(String fileType);

    /**
     * Parse the given input stream and extract transactions.
     *
     * @param input    the file content as an input stream
     * @param password optional password for encrypted files; null if not encrypted
     * @return list of parsed transactions; may be empty
     * @throws com.af.novadesk.api.finance.exception.StatementParseException if parsing fails
     */
    List<ParsedTransaction> parse(InputStream input, String password) throws Exception;
}
