package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.exception.StatementParseException;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * CSV parser for bank statement files (LLR-BNK-01).
 *
 * <p>Auto-detects delimiter (comma, tab, semicolon) and column headers
 * by matching common bank statement column names.</p>
 */
@Slf4j
@Component
public class CsvStatementParser implements BankStatementParser {

    private static final Set<String> DATE_HEADERS = Set.of(
            "date", "transaction date", "posting date", "value date", "transaction_date",
            "posting_date", "value_date", "txn date", "txn_date");
    private static final Set<String> DESCRIPTION_HEADERS = Set.of(
            "description", "narration", "particulars", "memo", "details",
            "transaction description", "remarks", "reference");
    private static final Set<String> DEBIT_HEADERS = Set.of(
            "debit", "withdrawal", "money out", "dr", "debit amount",
            "withdrawals", "withdrawal amount", "amount withdrawn");
    private static final Set<String> CREDIT_HEADERS = Set.of(
            "credit", "deposit", "money in", "cr", "credit amount",
            "deposits", "deposit amount", "amount deposited");
    private static final Set<String> BALANCE_HEADERS = Set.of(
            "balance", "running balance", "available balance", "closing balance",
            "balance amount", "current balance");

    private static final Set<String> AMOUNT_HEADERS = Set.of(
            "amount", "transaction amount", "txn amount", "value",
            "sum", "total");

    private static final char[] DELIMITERS = {',', '\t', ';', '|'};

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MMM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
    );

    private static final Pattern NUMBER_CLEANER = Pattern.compile("[^\\d.\\-+]");

    @Override
    public boolean supports(String fileType) {
        return "CSV".equalsIgnoreCase(fileType);
    }

    @Override
    public List<ParsedTransaction> parse(InputStream input, String password) throws Exception {
        // Read all lines into memory for delimiter detection
        List<String> lines = readAllLines(input);
        if (lines.isEmpty()) {
            throw new StatementParseException("CSV file is empty");
        }

        // Detect delimiter
        char delimiter = detectDelimiter(lines);

        // Parse with detected delimiter
        List<String[]> rows;
        try (CSVReader reader = new CSVReaderBuilder(new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(
                        String.join("\n", lines).getBytes()))))
                .withCSVParser(new com.opencsv.CSVParserBuilder()
                        .withSeparator(delimiter)
                        .build())
                .build()) {
            rows = reader.readAll();
        }

        if (rows.isEmpty()) {
            throw new StatementParseException("CSV file is empty");
        }

        // Detect column mapping from header row
        String[] headerRow = rows.get(0);
        ColumnMapping mapping = detectColumns(headerRow);

        if (mapping.dateIndex < 0) {
            throw new StatementParseException("Could not identify a date column. " +
                    "Expected headers like: date, transaction date, posting date");
        }
        if (mapping.descriptionIndex < 0) {
            throw new StatementParseException("Could not identify a description column. " +
                    "Expected headers like: description, narration, particulars");
        }
        if (mapping.debitIndex < 0 && mapping.creditIndex < 0 && mapping.amountIndex < 0) {
            throw new StatementParseException("Could not identify any amount column. " +
                    "Expected headers like: debit, credit, amount, withdrawal, deposit");
        }

        // Parse data rows
        List<ParsedTransaction> transactions = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (isRowEmpty(row)) {
                continue; // skip empty rows
            }

            try {
                ParsedTransaction parsed = parseRow(row, mapping, i + 1);
                if (parsed != null) {
                    transactions.add(parsed);
                }
            } catch (Exception e) {
                log.warn("Skipping row {} in CSV: {}", i + 1, e.getMessage());
                // Continue parsing other rows — lenient approach
            }
        }

        log.info("Parsed {} transactions from CSV file", transactions.size());
        return transactions;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private List<String> readAllLines(InputStream input) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private char detectDelimiter(List<String> lines) {
        if (lines.isEmpty()) return ',';

        // Use first data line (skip header) to detect
        String sampleLine = lines.size() > 1 ? lines.get(1) : lines.get(0);

        // If the file has commas in quoted fields, count actual delimiters
        char bestDelimiter = ',';
        int bestCount = 0;

        for (char delim : DELIMITERS) {
            int count = countDelimiters(sampleLine, delim);
            if (count > bestCount) {
                bestCount = count;
                bestDelimiter = delim;
            }
        }

        return bestDelimiter;
    }

    private int countDelimiters(String line, char delimiter) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                count++;
            }
        }
        return count;
    }

    private ColumnMapping detectColumns(String[] headerRow) {
        ColumnMapping mapping = new ColumnMapping();

        for (int i = 0; i < headerRow.length; i++) {
            String header = cleanHeader(headerRow[i]);

            if (DATE_HEADERS.contains(header)) {
                mapping.dateIndex = i;
            } else if (DESCRIPTION_HEADERS.contains(header)) {
                mapping.descriptionIndex = i;
            } else if (DEBIT_HEADERS.contains(header)) {
                mapping.debitIndex = i;
            } else if (CREDIT_HEADERS.contains(header)) {
                mapping.creditIndex = i;
            } else if (BALANCE_HEADERS.contains(header)) {
                mapping.balanceIndex = i;
            } else if (AMOUNT_HEADERS.contains(header)) {
                mapping.amountIndex = i;
            }
        }

        return mapping;
    }

    private String cleanHeader(String raw) {
        return raw.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private ParsedTransaction parseRow(String[] row, ColumnMapping mapping, int rowNum) {
        // Parse date
        String dateStr = getValue(row, mapping.dateIndex);
        if (dateStr == null || dateStr.isBlank()) {
            return null; // skip rows without dates
        }
        LocalDate date = parseDate(dateStr.trim());
        if (date == null) {
            throw new IllegalArgumentException("Unparseable date: '" + dateStr + "' at row " + rowNum);
        }

        // Parse description
        String description = getValue(row, mapping.descriptionIndex);
        if (description == null) {
            description = "";
        }
        description = description.trim();

        // Parse amounts
        BigDecimal debit = null;
        BigDecimal credit = null;
        BigDecimal balance = null;

        if (mapping.debitIndex >= 0 && mapping.creditIndex >= 0) {
            // Separate debit/credit columns
            String debitStr = getValue(row, mapping.debitIndex);
            String creditStr = getValue(row, mapping.creditIndex);
            debit = parseAmount(debitStr);
            credit = parseAmount(creditStr);
        } else if (mapping.amountIndex >= 0) {
            // Single amount column with signed values
            String amountStr = getValue(row, mapping.amountIndex);
            BigDecimal amount = parseAmount(amountStr);
            if (amount != null) {
                if (amount.signum() >= 0) {
                    credit = amount;
                } else {
                    debit = amount.abs();
                }
            }
        }

        // Parse balance
        if (mapping.balanceIndex >= 0) {
            String balanceStr = getValue(row, mapping.balanceIndex);
            balance = parseAmount(balanceStr);
        }

        // Validate: at least one amount must be present
        if (debit == null && credit == null) {
            log.debug("Skipping row {}: no debit or credit amount found", rowNum);
            return null;
        }

        return new ParsedTransaction(date, description, debit, credit, balance);
    }

    private String getValue(String[] row, int index) {
        if (index < 0 || index >= row.length) return null;
        String val = row[index];
        if (val == null) return null;
        val = val.trim();
        // Remove surrounding quotes
        if (val.startsWith("\"") && val.endsWith("\"")) {
            val = val.substring(1, val.length() - 1).trim();
        }
        return val.isEmpty() ? null : val;
    }

    private LocalDate parseDate(String value) {
        return parseDateStatic(value);
    }

    /**
     * Shared date-parsing logic used by both CSV and Excel parsers.
     */
    static LocalDate parseDateStatic(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }
        return null;
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            // Remove currency symbols, commas, spaces
            String cleaned = NUMBER_CLEANER.matcher(value).replaceAll("");
            if (cleaned.isEmpty()) return null;
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.debug("Could not parse amount: '{}'", value);
            return null;
        }
    }

    private boolean isRowEmpty(String[] row) {
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // Internal column mapping holder
    // =========================================================================

    private static class ColumnMapping {
        int dateIndex = -1;
        int descriptionIndex = -1;
        int debitIndex = -1;
        int creditIndex = -1;
        int balanceIndex = -1;
        int amountIndex = -1; // single signed amount column
    }
}
