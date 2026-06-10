package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.entity.BankTransaction;
import com.af.novadesk.api.finance.exception.StatementParseException;
import com.af.novadesk.api.finance.service.StatementParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Parses bank statement Excel (XLSX) files using Apache POI.
 *
 * <p>Reads the first sheet in the workbook. Expects a header row
 * with columns: Date, Description, Amount, Balance.
 * Auto-detects column indices by header name (case-insensitive).</p>
 */
@Slf4j
@Component
public class ExcelStatementParser implements StatementParser {

    private static final String SUPPORTED_TYPE = "XLSX";

    @Override
    public List<BankTransaction> parse(UUID statementId, InputStream inputStream, String filePassword) {
        return parseWithPoi(statementId, inputStream, filePassword);
    }

    @Override
    public String supportedFileType() {
        return SUPPORTED_TYPE;
    }

    /**
     * Uses Apache POI to read XLSX files.
     */
    private List<BankTransaction> parseWithPoi(UUID statementId, InputStream inputStream, String filePassword) {
        try {
            // Use reflection to avoid compile-time dependency issues if POI is not on classpath
            Object workbook = createWorkbook(inputStream, filePassword);
            Object sheet = workbook.getClass()
                    .getMethod("getSheetAt", int.class)
                    .invoke(workbook, 0);

            // Determine column indices from header row
            Object headerRow = sheet.getClass()
                    .getMethod("getRow", int.class)
                    .invoke(sheet, 0);

            if (headerRow == null) {
                throw new StatementParseException("Excel file has no header row");
            }

            int dateIdx = -1, descIdx = -1, amountIdx = -1, balanceIdx = -1;
            int lastCellNum = (int) headerRow.getClass().getMethod("getLastCellNum").invoke(headerRow);

            for (int i = 0; i < lastCellNum; i++) {
                Object cell = headerRow.getClass().getMethod("getCell", int.class).invoke(headerRow, i);
                if (cell == null) continue;

                String value = getCellStringValue(cell).toLowerCase();
                if (value.contains("date"))            dateIdx = i;
                else if (value.contains("desc") || value.contains("narr") || value.contains("detail")) descIdx = i;
                else if (value.contains("amount") || value.contains("value") || value.contains("sum")) amountIdx = i;
                else if (value.contains("balance"))    balanceIdx = i;
            }

            if (dateIdx == -1 || descIdx == -1 || amountIdx == -1) {
                throw new StatementParseException(
                        "Excel missing required columns: date, description, amount");
            }

            List<BankTransaction> transactions = new ArrayList<>();
            int lastRowNum = (int) sheet.getClass().getMethod("getLastRowNum").invoke(sheet);

            for (int rowIdx = 1; rowIdx <= lastRowNum; rowIdx++) {
                Object row = sheet.getClass().getMethod("getRow", int.class).invoke(sheet, rowIdx);
                if (row == null) continue;

                try {
                    LocalDate date = getCellDateValue(row, dateIdx);
                    String description = getCellStringValue(
                            row.getClass().getMethod("getCell", int.class).invoke(row, descIdx));
                    BigDecimal amount = getCellNumericValue(row, amountIdx);
                    BigDecimal balance = balanceIdx >= 0 ? getCellNumericValue(row, balanceIdx) : null;

                    if (description == null || description.isBlank()) continue;

                    transactions.add(BankTransaction.builder()
                            .transactionDate(date)
                            .description(description.length() > 500 ? description.substring(0, 500) : description)
                            .amount(amount)
                            .balance(balance)
                            .build());
                } catch (Exception e) {
                    log.warn("Skipping Excel row {}: {}", rowIdx + 1, e.getMessage());
                }
            }

            workbook.getClass().getMethod("close").invoke(workbook);
            log.info("Parsed {} transactions from Excel statement", transactions.size());
            return transactions;

        } catch (StatementParseException e) {
            throw e;
        } catch (Exception e) {
            throw new StatementParseException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }

    private Object createWorkbook(InputStream inputStream, String filePassword) throws Exception {
        try {
            // Try XSSFWorkbook for .xlsx files
            Class<?> xssfClass = Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
            if (filePassword != null && !filePassword.isBlank()) {
                // Try XSSFWorkbook(InputStream, boolean) with password via Biff8EncryptionKey
                Class<?> biff8Class = Class.forName("org.apache.poi.poifs.crypt.Decryptor");
                log.warn("Excel file password provided but XLSX encryption not fully supported; attempting without password");
            }
            return xssfClass.getConstructor(InputStream.class).newInstance(inputStream);
        } catch (ClassNotFoundException e) {
            throw new StatementParseException(
                    "Apache POI is not available on the classpath. Cannot parse Excel files.", e);
        }
    }

    private LocalDate getCellDateValue(Object row, int cellIdx) throws Exception {
        Object cell = row.getClass().getMethod("getCell", int.class).invoke(row, cellIdx);
        if (cell == null) {
            throw new StatementParseException("Date cell is empty at row");
        }

        // Try DateUtil first
        try {
            Class<?> dateUtil = Class.forName("org.apache.poi.ss.usermodel.DateUtil");
            boolean isDate = (boolean) dateUtil.getMethod("isCellDateFormatted", Class.forName("org.apache.poi.ss.usermodel.Cell"))
                    .invoke(null, cell);
            if (isDate) {
                Date date = (Date) cell.getClass().getMethod("getDateCellValue").invoke(cell);
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception ignored) {
        }

        // Fallback: parse as string
        String str = getCellStringValue(cell).trim();
        // Try common date formats
        for (var fmt : List.of(
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("MM-dd-yyyy"))) {
            try {
                return LocalDate.parse(str, fmt);
            } catch (Exception ignored) {
            }
        }
        throw new StatementParseException("Unrecognized date format in Excel: '" + str + "'");
    }

    private BigDecimal getCellNumericValue(Object row, int cellIdx) throws Exception {
        Object cell = row.getClass().getMethod("getCell", int.class).invoke(row, cellIdx);
        if (cell == null) return BigDecimal.ZERO;
        try {
            double val = (double) cell.getClass().getMethod("getNumericCellValue").invoke(cell);
            return BigDecimal.valueOf(val);
        } catch (Exception e) {
            String str = getCellStringValue(cell).trim().replace(",", "");
            return new BigDecimal(str);
        }
    }

    private String getCellStringValue(Object cell) throws Exception {
        try {
            return (String) cell.getClass().getMethod("getStringCellValue").invoke(cell);
        } catch (Exception e) {
            try {
                double val = (double) cell.getClass().getMethod("getNumericCellValue").invoke(cell);
                return String.valueOf(val);
            } catch (Exception e2) {
                return "";
            }
        }
    }
}
