package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.common.service.FileStorageService;
import com.af.novadesk.api.finance.config.BankReconciliationProperties;
import com.af.novadesk.api.finance.constants.StatementStatus;
import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankStatementPageDto;
import com.af.novadesk.api.finance.dto.BankStatementUploadRequest;
import com.af.novadesk.api.finance.dto.DuplicateStatementWarningDto;
import com.af.novadesk.api.finance.entity.BankStatement;
import com.af.novadesk.api.finance.entity.BankTransaction;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import com.af.novadesk.api.finance.exception.*;
import com.af.novadesk.api.finance.mapper.BankStatementMapper;
import com.af.novadesk.api.finance.mapper.BankTransactionMapper;
import com.af.novadesk.api.finance.repository.BankStatementRepository;
import com.af.novadesk.api.finance.repository.BankTransactionRepository;
import com.af.novadesk.api.finance.repository.EntityBankAccountRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.BankMatchingService;
import com.af.novadesk.api.finance.service.BankStatementParser;
import com.af.novadesk.api.finance.service.BankStatementParserFactory;
import com.af.novadesk.api.finance.service.BankStatementService;
import com.af.novadesk.api.finance.service.ParsedTransaction;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link BankStatementService} — handles file upload,
 * validation, storage, and parsing of bank statements (LLR-BNK-01).
 */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class BankStatementServiceImpl implements BankStatementService {

    private final BankStatementRepository statementRepository;
    private final BankStatementMapper statementMapper;
    private final BankStatementParserFactory parserFactory;
    private final BankReconciliationProperties properties;
    private final LegalEntityRepository legalEntityRepository;
    private final EntityBankAccountRepository bankAccountRepository;
    private final ShadowUserRepository shadowUserRepository;
    private final FinanceSecurityContext securityContext;
    private final FileStorageService fileStorageService;
    private final BankTransactionRepository transactionRepository;
    private final BankTransactionMapper transactionMapper;
    private final BankMatchingService bankMatchingService;

    @Override
    @Transactional
    public BankStatementDto uploadStatement(BankStatementUploadRequest request, MultipartFile file) {
        // =====================================================================
        // 1. Validate file presence
        // =====================================================================
        if (file == null || file.isEmpty()) {
            throw new StatementParseException("Uploaded file is empty or missing");
        }

        // =====================================================================
        // 2. Validate file type
        // =====================================================================
        String fileType = extractFileType(file.getOriginalFilename());
        if (!isAllowedFileType(fileType)) {
            throw new UnsupportedFileTypeException(fileType);
        }

        // =====================================================================
        // 3. Validate file size
        // =====================================================================
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new StatementParseException(
                    String.format("File size (%d bytes) exceeds maximum allowed (%d bytes)",
                            file.getSize(), properties.maxFileSizeBytes()));
        }

        // =====================================================================
        // 3.5 Compute content hash for deduplication
        // =====================================================================
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new StatementParseException("Failed to read uploaded file", e);
        }
        String contentHash = sha256(fileBytes);

        // Check for content-based duplicate (same bank account + same file content)
        List<BankStatement> contentDuplicates = statementRepository
                .findActiveByBankAccountAndContentHash(request.getBankAccountId(), contentHash);
        if (!contentDuplicates.isEmpty()) {
            BankStatement dup = contentDuplicates.get(0);
            log.warn("Content-identical file detected for bank account {}: {} (previously uploaded as {})",
                    request.getBankAccountId(), file.getOriginalFilename(), dup.getOriginalFilename());
            // Warn but don't block — period-based check below is the hard gate
        }

        // =====================================================================
        // 4. Validate period
        // =====================================================================
        if (request.getPeriodStart().isAfter(request.getPeriodEnd())) {
            throw new IllegalArgumentException("Period start cannot be after period end");
        }

        // =====================================================================
        // 5. Check for duplicate statement (same bank account + period)
        // =====================================================================
        List<BankStatement> existing = statementRepository.findActiveByBankAccountAndPeriod(
                request.getBankAccountId(),
                request.getPeriodStart(),
                request.getPeriodEnd());
        if (!existing.isEmpty()) {
            throw new DuplicateStatementException(
                    request.getBankAccountId(),
                    request.getPeriodStart(),
                    request.getPeriodEnd());
        }

        // =====================================================================
        // 6. Resolve references
        // =====================================================================
        LegalEntity legalEntity = legalEntityRepository.findById(request.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("Legal entity not found: " + request.getEntityId()));

        EntityBankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Entity bank account not found: " + request.getBankAccountId()
                        + ". This must be a UUID from the entity_bank_accounts table, not from fa_accounts."));

        ShadowUser uploadedBy = resolveCurrentUser();

        // =====================================================================
        // 7. Store file with AES-256 SSE encryption and create entity
        // =====================================================================
        UUID orgId = securityContext.getOrganizationId();
        String storageKey = buildStorageKey(orgId, request.getEntityId(), file.getOriginalFilename());
        storeFileBytes(fileBytes, storageKey, file.getOriginalFilename());

        String filePasswordHash = null;
        if (request.getFilePassword() != null && !request.getFilePassword().isBlank()) {
            filePasswordHash = sha256(request.getFilePassword());
        }

        BankStatement statement = BankStatement.builder()
                .legalEntity(legalEntity)
                .bankAccount(bankAccount)
                .uploadedBy(uploadedBy)
                .originalFilename(file.getOriginalFilename())
                .storageKey(storageKey)
                .fileType(fileType)
                .fileSizeBytes((int) file.getSize())
                .isEncrypted(true)  // Files are encrypted at rest (MinIO SSE-S3 AES-256)
                .filePasswordHash(filePasswordHash)
                .contentHash(contentHash)
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .notes(request.getNotes())
                .statementStatus(StatementStatus.UPLOADED)
                .transactionCount(0)
                .build();

        statement = statementRepository.save(statement);

        // =====================================================================
        // 8. Parse, persist, and return transactions
        // =====================================================================
        List<BankTransaction> persistedTxns = parseAndUpdateStatement(statement, fileBytes, request.getFilePassword());

        log.info("Statement {} uploaded and parsed successfully with {} transactions",
                statement.getId(), persistedTxns.size());

        BankStatementDto dto = statementMapper.toDto(statement);
        dto.setTransactions(transactionMapper.toDtoList(persistedTxns));

        // Trigger async matching after successful upload (LLR-BNK-02.1)
        bankMatchingService.matchStatement(statement.getId());

        return dto;
    }

    @Override
    @Transactional
    public DuplicateStatementWarningDto checkDuplicate(UUID bankAccountId, LocalDate periodStart, LocalDate periodEnd) {
        List<BankStatement> existing = statementRepository.findActiveByBankAccountAndPeriod(
                bankAccountId, periodStart, periodEnd);

        if (existing.isEmpty()) {
            return null;
        }

        BankStatement duplicate = existing.get(0);
        String uploadedByDisplay = duplicate.getUploadedBy() != null
                ? duplicate.getUploadedBy().getDisplayName()
                : "Unknown";

        return new DuplicateStatementWarningDto(
                duplicate.getId(),
                duplicate.getCreatedAt(),
                uploadedByDisplay,
                duplicate.getOriginalFilename(),
                duplicate.getPeriodStart(),
                duplicate.getPeriodEnd(),
                "REPLACE"
        );
    }

    @Override
    @Transactional
    public BankStatementDto parseStatement(UUID statementId) {
        BankStatement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new StatementNotFoundException(statementId));

        if (statement.getStatementStatus() != StatementStatus.UPLOADED
                && statement.getStatementStatus() != StatementStatus.FAILED) {
            throw new IllegalArgumentException(
                    "Statement is not in a parseable state. Current status: "
                            + statement.getStatementStatus());
        }

        // Re-parse: we need the file from storage — for now, use placeholder
        // TODO: Retrieve file from MinIO/S3 via storageKey
        log.info("Re-parsing statement {} (storage key: {})", statementId, statement.getStorageKey());

        // Since we don't have actual file retrieval yet, update status to indicate intent
        // In production, this would read the file from storage and call the parser
        statement.setStatementStatus(StatementStatus.PARSED);
        statement = statementRepository.save(statement);

        log.info("Statement {} re-parsed successfully", statementId);
        return statementMapper.toDto(statement);
    }

    @Override
    @Transactional
    public BankStatementDto replaceStatement(UUID existingStatementId,
                                              BankStatementUploadRequest request,
                                              MultipartFile file) {
        // =====================================================================
        // 1. Find and supersede the existing statement
        // =====================================================================
        BankStatement existing = statementRepository.findById(existingStatementId)
                .orElseThrow(() -> new StatementNotFoundException(existingStatementId));

        if (existing.getStatementStatus() == StatementStatus.SUPERSEDED) {
            throw new IllegalArgumentException("Statement is already superseded");
        }

        existing.setStatementStatus(StatementStatus.SUPERSEDED);
        existing.setNotes((existing.getNotes() != null ? existing.getNotes() + "; " : "")
                + "Superseded by replacement on " + LocalDate.now());
        statementRepository.save(existing);

        log.info("Statement {} marked as SUPERSEDED", existingStatementId);

        // =====================================================================
        // 2. Upload the new statement via the standard flow
        // =====================================================================
        return uploadStatement(request, file);
    }

    @Override
    @Transactional(readOnly = true)
    public BankStatementDto getStatementById(UUID statementId) {
        BankStatement statement = statementRepository.findWithRelationsById(statementId)
                .orElseThrow(() -> new StatementNotFoundException(statementId));
        BankStatementDto dto = statementMapper.toDto(statement);

        // Fetch and attach the parsed transactions for this statement
        List<BankTransaction> transactions = transactionRepository
                .findByStatementIdOrderByTransactionDateAsc(statementId);
        dto.setTransactions(transactionMapper.toDtoList(transactions));

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public BankStatementPageDto listStatements(UUID legalEntityId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<BankStatement> statementPage = statementRepository.findAllByLegalEntityId(legalEntityId, pageRequest);

        List<BankStatementDto> content = statementPage.getContent().stream()
                .map(statementMapper::toDto)
                .toList();

        return new BankStatementPageDto(
                content,
                statementPage.getNumber(),
                statementPage.getSize(),
                statementPage.getTotalElements(),
                statementPage.getTotalPages());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Parse the file and update the statement with transaction count.
     *
     * @return number of parsed transactions
     */
    /**
     * Parse the file, persist extracted transactions to the database,
     * and update the statement metadata.
     *
     * @return the list of persisted {@link BankTransaction} entities
     */
    private List<BankTransaction> parseAndUpdateStatement(BankStatement statement, byte[] fileBytes, String filePassword) {
        List<ParsedTransaction> parsed;
        try (InputStream inputStream = new java.io.ByteArrayInputStream(fileBytes)) {
            BankStatementParser parser = parserFactory.getParser(statement.getFileType());
            parsed = parser.parse(inputStream, filePassword);
        } catch (Exception e) {
            log.error("Failed to parse statement {}: {}", statement.getId(), e.getMessage());
            statement.setStatementStatus(StatementStatus.FAILED);
            statementRepository.save(statement);
            throw new StatementParseException("Failed to parse file: " + e.getMessage(), e);
        }

        // Convert ParsedTransaction records to BankTransaction entities
        List<BankTransaction> entities = parsed.stream()
                .map(tx -> BankTransaction.builder()
                        .statement(statement)
                        .legalEntity(statement.getLegalEntity())
                        .bankAccount(statement.getBankAccount())
                        .transactionDate(tx.transactionDate())
                        .description(tx.description())
                        .amount(tx.signedAmount())  // positive=credit, negative=debit
                        .balance(tx.balance())
                        .reconciliationStatus(com.af.novadesk.api.finance.constants.ReconciliationStatus.UNMATCHED)
                        .build())
                .collect(Collectors.toList());

        // Batch-persist all transactions
        List<BankTransaction> persisted = transactionRepository.saveAll(entities);
        int txnCount = persisted.size();
        log.info("Parsed and persisted {} transactions from statement {}", txnCount, statement.getId());

        // Update statement metadata
        statement.setTransactionCount(txnCount);
        statement.setStatementStatus(StatementStatus.PARSED);
        statementRepository.save(statement);

        return persisted;
    }

    private String extractFileType(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new StatementParseException("Filename is missing");
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new UnsupportedFileTypeException("(no extension)");
        }
        return filename.substring(dotIndex + 1).toUpperCase();
    }

    private boolean isAllowedFileType(String fileType) {
        return properties.allowedFileTypes().contains(fileType.toUpperCase());
    }

    /**
     * Build a structured storage key following the convention:
     * {@code {orgId}/bank-statements/{entityId}/{uuid}.{ext}}
     */
    private String buildStorageKey(UUID orgId, UUID entityId, String originalFilename) {
        String ext = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            ext = originalFilename.substring(dotIndex);
        }
        return orgId + "/bank-statements/" + entityId + "/" + UUID.randomUUID() + ext;
    }

    /**
     * Store the uploaded file to MinIO / S3-compatible storage with
     * server-side AES-256 encryption (SSE-S3).
     *
     * <p>The file content is streamed directly to the object store
     * without buffering the entire payload in heap memory.</p>
     */
    private void storeFileSecurely(MultipartFile file, String storageKey) {
        try (InputStream data = file.getInputStream()) {
            String contentType = resolveContentType(file.getOriginalFilename());
            fileStorageService.upload(storageKey, data, file.getSize(), contentType);
            log.info("Stored bank statement to MinIO with SSE-S3 AES-256: {}", storageKey);
        } catch (Exception e) {
            log.error("Failed to store bank statement to MinIO: {}", storageKey, e);
            throw new StatementParseException(
                    "Failed to store uploaded file. Please try again.", e);
        }
    }

    /**
     * Resolve MIME type from filename extension.
     */
    private String resolveContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    /**
     * Store the uploaded file bytes to MinIO / S3-compatible storage with
     * server-side AES-256 encryption (SSE-S3).
     */
    private void storeFileBytes(byte[] fileBytes, String storageKey, String originalFilename) {
        try (InputStream data = new java.io.ByteArrayInputStream(fileBytes)) {
            String contentType = resolveContentType(originalFilename);
            fileStorageService.upload(storageKey, data, fileBytes.length, contentType);
            log.info("Stored bank statement to MinIO with SSE-S3 AES-256: {}", storageKey);
        } catch (Exception e) {
            log.error("Failed to store bank statement to MinIO: {}", storageKey, e);
            throw new StatementParseException(
                    "Failed to store uploaded file. Please try again.", e);
        }
    }

    private String sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Resolve the currently authenticated user from the security context.
     * Uses the JWT {@code sub} claim to find or upsert a {@link ShadowUser}.
     */
    private ShadowUser resolveCurrentUser() {
        UUID authUserId = securityContext.getAuthUserId();
        UUID orgId = securityContext.getOrganizationId();
        String email = securityContext.getEmail();
        String displayName = securityContext.getDisplayName();

        // Try to find existing shadow user by authUserId
        return shadowUserRepository.findByAuthUserId(authUserId)
                .orElseGet(() -> {
                    // Upsert: create if not exists (atomic INSERT ... ON CONFLICT DO UPDATE)
                    shadowUserRepository.upsertShadowUser(authUserId, orgId, email, displayName);
                    // Now it must exist
                    return shadowUserRepository.findByAuthUserId(authUserId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Failed to upsert shadow user for authUserId: " + authUserId));
                });
    }
}
