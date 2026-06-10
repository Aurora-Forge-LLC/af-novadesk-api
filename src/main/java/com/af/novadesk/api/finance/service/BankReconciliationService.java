package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankStatementPageDto;
import com.af.novadesk.api.finance.dto.BankStatementUploadRequest;
import com.af.novadesk.api.finance.dto.BankTransactionPageDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Service contract for Bank Reconciliation module (LLR-BNK-01/02/03/04).
 *
 * <p>Handles bank statement uploads, parsing, transaction listing, and
 * matching operations. All operations are scoped to the caller's
 * {@code organizationId} from the JWT.</p>
 */
public interface BankReconciliationService {

    // =========================================================================
    // LLR-BNK-01.1: Upload & Replace
    // =========================================================================

    /**
     * Uploads a new bank statement file, stores it in MinIO/S3, persists
     * metadata, and triggers asynchronous parsing of the file to extract
     * transactions.
     *
     * <p>If an existing non-superseded statement overlaps the same bank
     * account + period, a {@link com.af.novadesk.api.finance.exception.DuplicateStatementException}
     * is thrown containing the existing statement's details.</p>
     *
     * @param request     form fields (entity, bank account, period, notes, file password)
     * @param file        the uploaded file (CSV, XLSX)
     * @return the persisted statement DTO
     */
    BankStatementDto uploadStatement(BankStatementUploadRequest request, MultipartFile file);

    /**
     * Replaces an existing statement by superseding all overlapping active
     * statements for the same bank account + period and uploading the new file.
     *
     * @param request     form fields (entity, bank account, period, notes, file password)
     * @param file        the uploaded file (CSV, XLSX)
     * @return the newly persisted statement DTO
     */
    BankStatementDto replaceStatement(BankStatementUploadRequest request, MultipartFile file);

    // =========================================================================
    // LLR-BNK-01: Statement queries
    // =========================================================================

    /**
     * Returns a paginated list of bank statements for a given legal entity.
     */
    BankStatementPageDto listStatements(UUID legalEntityId, int page, int size);

    /**
     * Returns full details for a single bank statement.
     */
    BankStatementDto getStatement(UUID statementId);

    // =========================================================================
    // LLR-BNK-01: Transaction queries
    // =========================================================================

    /**
     * Returns a paginated list of bank transactions for a given statement.
     *
     * @param statementId the statement UUID
     * @param page        0-based page index
     * @param size        page size
     */
    BankTransactionPageDto listTransactions(UUID statementId, int page, int size);
}
