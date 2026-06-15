package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.exception.StatementParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF parser for bank statement files (LLR-BNK-01).
 *
 * <p>Extracts text from PDF documents and attempts to parse tabular
 * transaction data. Handles text-based PDFs (most bank-generated statements).
 * Scanned/image-based PDFs would require OCR which is not included.</p>
 */
@Slf4j
@Component
public class PdfStatementParser implements BankStatementParser {

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{2,4}[-/. ]\\d{2}[-/. ]\\d{2,4})\\b");
    private static final Pattern MULTI_AMOUNT_LINE = Pattern.compile(
            "(\\d{2,4}[-/. ]\\d{2}[-/. ]\\d{2,4})\\s+(.+?)\\s+([\\d,]+\\.\\d{2})\\s*([\\d,]+\\.\\d{2})?\\s*([\\d,]+\\.\\d{2})?");

    private static final Set<String> SKIP_LINE_KEYWORDS = Set.of(
            "statement", "page", "opening balance", "closing balance",
            "total", "summary", "continued", "brought forward",
            "carried forward", "subtotal", "balance b/f", "balance c/f");

    @Override
    public boolean supports(String fileType) {
        return "PDF".equalsIgnoreCase(fileType);
    }

    @Override
    public List<ParsedTransaction> parse(InputStream input, String password) throws Exception {
        PDDocument document;
        try {
            if (password != null && !password.isBlank()) {
                document = PDDocument.load(input, password);
            } else {
                document = PDDocument.load(input);
            }
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("password") || msg.contains("encrypt") || msg.contains("protected")) {
                throw new StatementParseException(
                        "PDF file is encrypted or password-protected. Please provide the file password.", e);
            }
            throw new StatementParseException("Failed to read PDF file: " + e.getMessage(), e);
        }

        try {
            if (document.getNumberOfPages() == 0) {
                throw new StatementParseException("PDF document has no pages");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String fullText = stripper.getText(document);

            if (fullText == null || fullText.isBlank()) {
                throw new StatementParseException(
                        "PDF does not contain extractable text. " +
                        "This may be a scanned/image-based PDF which requires OCR.");
            }

            return extractTransactions(fullText);
        } finally {
            document.close();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private List<ParsedTransaction> extractTransactions(String text) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        String[] rawLines = text.split("\\r?\\n");

        List<String> mergedLines = mergeContinuationLines(rawLines);

        for (int i = 0; i < mergedLines.size(); i++) {
            String line = mergedLines.get(i).trim();
            if (line.isEmpty()) continue;
            if (isSkipLine(line)) continue;

            ParsedTransaction parsed = tryParseTransactionLine(line, i + 1);
            if (parsed != null) {
                transactions.add(parsed);
            }
        }

        log.info("Parsed {} transactions from PDF text ({} lines processed)",
                transactions.size(), mergedLines.size());
        return transactions;
    }

    private List<String> mergeContinuationLines(String[] rawLines) {
        List<String> result = new ArrayList<>();
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (!result.isEmpty() && !looksLikeTransactionStart(trimmed)) {
                int lastIdx = result.size() - 1;
                result.set(lastIdx, result.get(lastIdx) + " " + trimmed);
            } else {
                result.add(trimmed);
            }
        }
        return result;
    }

    private boolean looksLikeTransactionStart(String line) {
        return DATE_PATTERN.matcher(line).lookingAt();
    }

    private boolean isSkipLine(String line) {
        String lower = line.toLowerCase().trim();
        for (String keyword : SKIP_LINE_KEYWORDS) {
            if (lower.startsWith(keyword)) return true;
        }
        return false;
    }

    private ParsedTransaction tryParseTransactionLine(String line, int lineNum) {
        // Strategy 1: Try structured pattern with 3-5 numeric columns
        Matcher m = MULTI_AMOUNT_LINE.matcher(line);
        if (m.matches()) {
            return parseStructuredLine(m);
        }

        // Strategy 2: Extract date, then split remainder into description + trailing numbers
        Matcher dateMatcher = DATE_PATTERN.matcher(line);
        if (!dateMatcher.find()) {
            log.debug("Skipping PDF line {}: no date found", lineNum);
            return null;
        }

        LocalDate date = parseDate(dateMatcher.group(1));
        if (date == null) {
            log.debug("Skipping PDF line {}: unparseable date '{}'", lineNum, dateMatcher.group(1));
            return null;
        }

        String afterDate = line.substring(dateMatcher.end()).trim();
        List<BigDecimal> trailingNumbers = extractTrailingNumbers(afterDate);
        if (trailingNumbers.isEmpty()) {
            log.debug("Skipping PDF line {}: no numeric amounts found", lineNum);
            return null;
        }

        String description = stripTrailingNumbers(afterDate, trailingNumbers.size()).trim();
        if (description.isEmpty()) {
            description = "Transaction";
        }

        BigDecimal debit = null;
        BigDecimal credit = null;
        BigDecimal balance = null;

        switch (trailingNumbers.size()) {
            case 1:
                BigDecimal amt = trailingNumbers.get(0);
                if (amt.signum() < 0) {
                    debit = amt.abs();
                } else {
                    credit = amt;
                }
                break;
            case 2:
                debit = trailingNumbers.get(0).abs();
                balance = trailingNumbers.get(1).abs();
                break;
            case 3:
                debit = trailingNumbers.get(0).abs();
                credit = trailingNumbers.get(1).abs();
                balance = trailingNumbers.get(2).abs();
                break;
            default:
                debit = trailingNumbers.get(0).abs();
                if (trailingNumbers.size() >= 2) {
                    balance = trailingNumbers.get(trailingNumbers.size() - 1).abs();
                }
                break;
        }

        if (debit != null && debit.compareTo(BigDecimal.ZERO) == 0) debit = null;
        if (credit != null && credit.compareTo(BigDecimal.ZERO) == 0) credit = null;

        return new ParsedTransaction(date, description, debit, credit, balance);
    }

    private ParsedTransaction parseStructuredLine(Matcher m) {
        String dateStr = m.group(1);
        String description = m.group(2) != null ? m.group(2).trim() : "Transaction";
        String col3 = m.group(3);
        String col4 = m.group(4);
        String col5 = m.group(5);

        LocalDate date = parseDate(dateStr);
        if (date == null) return null;

        BigDecimal val1 = parseAmount(col3);
        BigDecimal val2 = parseAmount(col4);
        BigDecimal val3 = parseAmount(col5);

        BigDecimal debit = null;
        BigDecimal credit = null;
        BigDecimal balance = null;

        if (val3 != null) {
            debit = val1;
            credit = val2;
            balance = val3;
        } else if (val2 != null) {
            debit = val1;
            balance = val2;
        } else {
            if (val1.signum() < 0) {
                debit = val1.abs();
            } else {
                credit = val1;
            }
        }

        if (debit != null && debit.compareTo(BigDecimal.ZERO) == 0) debit = null;
        if (credit != null && credit.compareTo(BigDecimal.ZERO) == 0) credit = null;

        return new ParsedTransaction(date, description, debit, credit, balance);
    }

    private List<BigDecimal> extractTrailingNumbers(String text) {
        List<BigDecimal> numbers = new ArrayList<>();
        Matcher amountMatcher = Pattern.compile("-?[\\d,]+\\.\\d{2}|-?[\\d,]+\\.\\d{1}|-?\\d+").matcher(text);
        while (amountMatcher.find()) {
            BigDecimal val = parseAmount(amountMatcher.group());
            if (val != null) {
                numbers.add(val);
            }
        }
        int keep = Math.min(numbers.size(), 4);
        return numbers.subList(numbers.size() - keep, numbers.size());
    }

    private String stripTrailingNumbers(String text, int count) {
        if (count <= 0) return text;
        String result = text;
        for (int i = 0; i < count; i++) {
            result = result.replaceFirst("\\s*-?[\\d,]+\\.?\\d*\\s*$", "");
        }
        return result;
    }

    private LocalDate parseDate(String value) {
        return CsvStatementParser.parseDateStatic(value.trim());
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String cleaned = value.replaceAll("[^\\d.\\-]", "");
            if (cleaned.isEmpty()) return null;
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.debug("Could not parse PDF amount: '{}'", value);
            return null;
        }
    }
}
