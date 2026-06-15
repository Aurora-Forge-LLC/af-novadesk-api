package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.exception.StatementParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Excel parser for bank statement files (.xlsx and .xls) (LLR-BNK-01).
 *
 * <p>Reads the first sheet, auto-detects column headers by matching
 * common bank statement column names, and extracts transactions.</p>
 *
 * <p>Supports password-protected Excel files via Apache POI's
 * {@link WorkbookFactory#create(InputStream, String)}.</p>
 */
@Slf4j
@Component
public class ExcelStatementParser implements BankStatementParser {

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

    @Override
    public boolean supports(String fileType) {
        return "XLSX".equalsIgnoreCase(fileType) || "XLS".equalsIgnoreCase(fileType);
    }

    @Override
    public List<ParsedTransaction> parse(InputStream input, String password) throws Exception {
        Workbook workbook;

        try {
            if (password != null && !password.isBlank()) {
                // Try to open with password first
                workbook = WorkbookFactory.create(input, password);
            } else {
                workbook = WorkbookFactory.create(input);
            }
        } catch (Exception e) {
            // Detect if the error is about encryption
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("password") || msg.contains("encrypt") || msg.contains("protected")) {
                throw new StatementParseException("File is encrypted or password-protected. " +
                        "Please provide the file password.", e);
            }
            throw new StatementParseException("Failed to open Excel workbook: " + e.getMessage(), e);
        }

        try (workbook) {
            Sheet sheet = workbook.getSheetAt(0); // Read first sheet
            return parseSheet(sheet);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private List<ParsedTransaction> parseSheet(Sheet sheet) throws StatementParseException {
        List<ParsedTransaction> transactions = new ArrayList<>();

        if (sheet.getPhysicalNumberOfRows() == 0) {
            throw new StatementParseException("Excel sheet is empty");
        }

        // Read header row
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new StatementParseException("Excel sheet has no header row");
        }

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

        // Parse data rows (skip header row)
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row)) {
                continue;
            }

            try {
                ParsedTransaction parsed = parseRow(row, mapping, i + 1);
                if (parsed != null) {
                    transactions.add(parsed);
                }
            } catch (Exception e) {
                log.warn("Skipping Excel row {}: {}", i + 1, e.getMessage());
                // Continue parsing other rows — lenient approach
            }
        }

        log.info("Parsed {} transactions from Excel sheet '{}'",
                transactions.size(), sheet.getSheetName());
        return transactions;
    }

    private ColumnMapping detectColumns(Row headerRow) {
        ColumnMapping mapping = new ColumnMapping();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;

            String header = cleanHeader(getCellValueAsString(cell));

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

    private ParsedTransaction parseRow(Row row, ColumnMapping mapping, int rowNum) {
        // Parse date
        LocalDate date = getDateValue(row, mapping.dateIndex);
        if (date == null) {
            return null; // skip rows without dates
        }

        // Parse description
        String description = getStringValue(row, mapping.descriptionIndex);
        if (description == null) description = "";
        description = description.trim();

        // Parse amounts
        BigDecimal debit = null;
        BigDecimal credit = null;
        BigDecimal balance = null;

        if (mapping.debitIndex >= 0 && mapping.creditIndex >= 0) {
            // Separate debit/credit columns
            debit = getNumericValue(row, mapping.debitIndex);
            credit = getNumericValue(row, mapping.creditIndex);
        } else if (mapping.amountIndex >= 0) {
            // Single amount column with signed values
            BigDecimal amount = getNumericValue(row, mapping.amountIndex);
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
            balance = getNumericValue(row, mapping.balanceIndex);
        }

        // Validate: at least one amount must be present
        if (debit == null && credit == null) {
            log.debug("Skipping Excel row {}: no debit or credit amount found", rowNum);
            return null;
        }

        return new ParsedTransaction(date, description, debit, credit, balance);
    }

    private LocalDate getDateValue(Row row, int index) {
        if (index < 0) return null;
        Cell cell = row.getCell(index);
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
        } catch (Exception ignored) {}

        // Try parsing as string
        String strVal = getCellValueAsString(cell);
        if (strVal != null && !strVal.isBlank()) {
            return CsvStatementParser.parseDateStatic(strVal.trim());
        }
        return null;
    }

    private String getStringValue(Row row, int index) {
        if (index < 0) return null;
        Cell cell = row.getCell(index);
        if (cell == null) return null;
        return getCellValueAsString(cell);
    }

    private BigDecimal getNumericValue(Row row, int index) {
        if (index < 0) return null;
        Cell cell = row.getCell(index);
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
        } catch (Exception ignored) {}

        // Try parsing as string
        String strVal = getCellValueAsString(cell);
        if (strVal != null && !strVal.isBlank()) {
            try {
                String cleaned = strVal.replaceAll("[^\\d.\\-+]", "");
                if (!cleaned.isEmpty()) {
                    return new BigDecimal(cleaned);
                }
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String getCellValueAsString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                // Format numeric without scientific notation
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception e2) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellValueAsString(cell);
                if (val != null && !val.isBlank()) {
                    return false;
                }
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
        int amountIndex = -1;
    }
}
