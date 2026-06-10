package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.entity.BankTransaction;
import com.af.novadesk.api.finance.exception.StatementParseException;
import com.af.novadesk.api.finance.service.StatementParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parses bank statement CSV files.
 *
 * <p>Expected header columns (case-insensitive):
 * <pre>{@code
 * Date, Description, Amount, Balance
 * 2026-01-15, POS PURCHASE AMAZON, -150.00, 5000.00
 * }</pre>
 *
 * <p>The parser auto-detects common date formats:
 * {@code yyyy-MM-dd}, {@code dd/MM/yyyy}, {@code MM/dd/yyyy}, {@code yyyy/MM/dd}.</p>
 */
@Slf4j
@Component
public class CsvStatementParser implements StatementParser {

    private static final String SUPPORTED_TYPE = "CSV";
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,           // yyyy-MM-dd
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy")
    );

    @Override
    public List<BankTransaction> parse(UUID statementId, InputStream inputStream, String filePassword) {
        List<BankTransaction> transactions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new StatementParseException("CSV file is empty");
            }

            String[] headers = parseCsvLine(headerLine);
            int dateIdx = -1, descIdx = -1, amountIdx = -1, balanceIdx = -1;

            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim().toLowerCase();
                if (h.contains("date"))            dateIdx = i;
                else if (h.contains("desc") || h.contains("narr") || h.contains("detail")) descIdx = i;
                else if (h.contains("amount") || h.contains("value") || h.contains("sum")) amountIdx = i;
                else if (h.contains("balance"))    balanceIdx = i;
            }

            if (dateIdx == -1 || descIdx == -1 || amountIdx == -1) {
                throw new StatementParseException(
                        "CSV missing required columns: date, description, amount. Found: " + headerLine);
            }

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isBlank()) continue;

                String[] fields = parseCsvLine(line);
                if (fields.length <= Math.max(dateIdx, Math.max(descIdx, amountIdx))) {
                    log.warn("Skipping CSV line {}: insufficient fields (got {}, expected at least {})",
                            lineNum, fields.length, Math.max(dateIdx, Math.max(descIdx, amountIdx)) + 1);
                    continue;
                }

                try {
                    LocalDate date = parseDate(fields[dateIdx].trim());
                    String description = fields[descIdx].trim();
                    BigDecimal amount = new BigDecimal(fields[amountIdx].trim().replace(",", ""));
                    BigDecimal balance = balanceIdx >= 0 && balanceIdx < fields.length
                            ? new BigDecimal(fields[balanceIdx].trim().replace(",", ""))
                            : null;

                    transactions.add(BankTransaction.builder()
                            .transactionDate(date)
                            .description(description.length() > 500 ? description.substring(0, 500) : description)
                            .amount(amount)
                            .balance(balance)
                            .build());
                } catch (Exception e) {
                    log.warn("Skipping CSV line {}: {} — {}", lineNum, line, e.getMessage());
                }
            }

        } catch (IOException e) {
            throw new StatementParseException("Failed to read CSV file", e);
        }

        log.info("Parsed {} transactions from CSV statement", transactions.size());
        return transactions;
    }

    @Override
    public String supportedFileType() {
        return SUPPORTED_TYPE;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Simple CSV line parser that respects quoted fields.
     */
    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * Tries to parse a date string using the supported formats.
     */
    private static LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new StatementParseException("Unrecognized date format: '" + dateStr + "'");
    }
}
