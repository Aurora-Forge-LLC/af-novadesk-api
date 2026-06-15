package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.exception.UnsupportedFileTypeException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory that selects the appropriate {@link BankStatementParser} based on file type.
 *
 * <p>Auto-discovers all {@link BankStatementParser} beans registered in the Spring context.</p>
 */
@Component
public class BankStatementParserFactory {

    private final Map<String, BankStatementParser> parserMap;

    /**
     * Constructor injection — builds a lookup map from all {@link BankStatementParser} beans.
     * Each parser is indexed by the file type(s) it supports.
     */
    public BankStatementParserFactory(List<BankStatementParser> parsers) {
        this.parserMap = parsers.stream()
                .flatMap(p -> supportedTypes(p).stream()
                        .map(type -> Map.entry(type.toUpperCase(), p)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing));
    }

    /**
     * Get the appropriate parser for the given file type.
     *
     * @param fileType uppercase file extension (e.g. "CSV", "XLSX")
     * @return the matching parser
     * @throws UnsupportedFileTypeException if no parser supports the given type
     */
    public BankStatementParser getParser(String fileType) {
        BankStatementParser parser = parserMap.get(fileType.toUpperCase());
        if (parser == null) {
            throw new UnsupportedFileTypeException(fileType);
        }
        return parser;
    }

    private List<String> supportedTypes(BankStatementParser parser) {
        // Infer supported types from the supports() method by testing common types
        return List.of("CSV", "XLSX", "XLS", "PDF").stream()
                .filter(parser::supports)
                .collect(Collectors.toList());
    }
}
