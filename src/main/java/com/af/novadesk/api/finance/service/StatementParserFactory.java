package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.exception.UnsupportedFileTypeException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the appropriate {@link StatementParser} implementation
 * based on the uploaded file's extension.
 */
public class StatementParserFactory {

    private final Map<String, StatementParser> parserMap;

    public StatementParserFactory(List<StatementParser> parsers) {
        this.parserMap = parsers.stream()
                .collect(Collectors.toMap(
                        p -> p.supportedFileType().toUpperCase(),
                        Function.identity()
                ));
    }

    /**
     * Returns the parser for the given file type.
     *
     * @param fileType file extension in uppercase (e.g. "CSV", "XLSX")
     * @return the matching {@link StatementParser}
     * @throws UnsupportedFileTypeException if no parser supports this type
     */
    public StatementParser getParser(String fileType) {
        String key = fileType.toUpperCase();
        StatementParser parser = parserMap.get(key);
        if (parser == null) {
            throw new UnsupportedFileTypeException(fileType);
        }
        return parser;
    }
}
