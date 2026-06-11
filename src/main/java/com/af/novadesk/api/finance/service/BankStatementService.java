package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankStatementPageDto;
import com.af.novadesk.api.finance.dto.BankStatementUploadRequest;
import com.af.novadesk.api.finance.dto.DuplicateStatementWarningDto;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service interface for Bank Statement upload and management (LLR-BNK-01).
 */
public interface BankStatementService {

    /**
     * Upload a bank statement file, validate it, parse transactions,
     * and persist the statement metadata.
     *
     * @param request  form fields (entity, bank account, period, notes, password)
     * @param file     the uploaded file (CSV, XLSX, XLS)
     * @return the created statement DTO with parsed transaction count
     */
    BankStatementDto uploadStatement(BankStatementUploadRequest request, MultipartFile file);

    /**
     * Parse an already-uploaded statement file (LLR-BNK-01.4).
     *
     * @param statementId UUID of a statement with status UPLOADED or FAILED
     * @return the updated statement DTO with parsed transactions
     */
    BankStatementDto parseStatement(UUID statementId);

    /**
     * Check if a duplicate statement already exists for the given bank account
     * and period. Returns a warning DTO if a duplicate is found, null otherwise.
     *
     * @param bankAccountId UUID of the bank account
     * @param periodStart   statement period start
     * @param periodEnd     statement period end
     * @return warning DTO if duplicate exists, null if no duplicate
     */
    DuplicateStatementWarningDto checkDuplicate(UUID bankAccountId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Replace an existing statement with a new file upload.
     * Marks the existing statement as SUPERSEDED and creates a new one.
     *
     * @param existingStatementId UUID of the statement to replace
     * @param request             form fields for the replacement
     * @param file                the new file (CSV, XLSX, XLS)
     * @return the newly created statement DTO
     */
    BankStatementDto replaceStatement(UUID existingStatementId, BankStatementUploadRequest request, MultipartFile file);

    /**
     * Retrieve a bank statement by ID with all relations.
     */
    BankStatementDto getStatementById(UUID statementId);

    /**
     * List all bank statements for a given legal entity (paginated).
     *
     * @param legalEntityId entity UUID
     * @param page          zero-based page number
     * @param size          page size
     * @return paginated statement list
     */
    BankStatementPageDto listStatements(UUID legalEntityId, int page, int size);
}
