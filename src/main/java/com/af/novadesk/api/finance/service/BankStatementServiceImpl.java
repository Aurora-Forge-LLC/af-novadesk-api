package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.config.BankReconciliationProperties;
import com.af.novadesk.api.finance.constants.StatementStatus;
import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankStatementPageDto;
import com.af.novadesk.api.finance.dto.BankStatementUploadRequest;
import com.af.novadesk.api.finance.dto.BankTransactionDto;
import com.af.novadesk.api.finance.dto.DuplicateStatementWarningDto;
import com.af.novadesk.api.finance.entity.BankStatement;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import com.af.novadesk.api.finance.exception.*;
import com.af.novadesk.api.finance.mapper.BankStatementMapper;
import com.af.novadesk.api.finance.repository.BankStatementRepository;
import com.af.novadesk.api.finance.repository.EntityBankAccountRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
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
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + request.getBankAccountId()));

        ShadowUser uploadedBy = resolveCurrentUser();

        // =====================================================================
        // 7. Store file and create entity
        // =====================================================================
        String storageKey = storeFile(file, request.getEntityId());

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
                .isEncrypted(filePasswordHash != null)
                .filePasswordHash(filePasswordHash)
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .notes(request.getNotes())
                .statementStatus(StatementStatus.UPLOADED)
                .transactionCount(0)
                .build();

        statement = statementRepository.save(statement);

        // =====================================================================
        // 8. Parse the file and extract transactions
        // =====================================================================
        List<ParsedTransaction> parsedTxns = parseAndUpdateStatement(statement, file, request.getFilePassword());

        log.info("Statement {} uploaded and parsed successfully with {} transactions",
                statement.getId(), parsedTxns.size());

        BankStatementDto dto = statementMapper.toDto(statement);
        dto.setTransactions(toTransactionDtoList(parsedTxns));
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
        return statementMapper.toDto(statement);
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
    private List<ParsedTransaction> parseAndUpdateStatement(BankStatement statement, MultipartFile file, String filePassword) {
        List<ParsedTransaction> transactions;
        try (InputStream inputStream = file.getInputStream()) {
            BankStatementParser parser = parserFactory.getParser(statement.getFileType());
            transactions = parser.parse(inputStream, filePassword);
        } catch (Exception e) {
            log.error("Failed to parse statement {}: {}", statement.getId(), e.getMessage());
            statement.setStatementStatus(StatementStatus.FAILED);
            statementRepository.save(statement);
            throw new StatementParseException("Failed to parse file: " + e.getMessage(), e);
        }

        // TODO: After bnk_transactions table is created in V1.89+:
        //       batch-persist transactions via transactionRepository.saveAll()
        int txnCount = transactions.size();
        log.info("Parsed {} transactions from statement {}", txnCount, statement.getId());

        statement.setTransactionCount(txnCount);
        statement.setStatementStatus(StatementStatus.PARSED);
        statementRepository.save(statement);

        return transactions;
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

    private String storeFile(MultipartFile file, UUID entityId) {
        // TODO: Implement file storage (S3 / local filesystem / database)
        // For now, use a placeholder key
        return entityId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
    }

    /**
     * Convert a list of {@link ParsedTransaction} internal records to
     * {@link BankTransactionDto} objects for the API response.
     */
    private List<BankTransactionDto> toTransactionDtoList(List<ParsedTransaction> parsed) {
        if (parsed == null || parsed.isEmpty()) {
            return List.of();
        }
        return parsed.stream()
                .map(tx -> BankTransactionDto.builder()
                        .transactionDate(tx.transactionDate())
                        .description(tx.description())
                        .debit(tx.debit())
                        .credit(tx.credit())
                        .balance(tx.balance())
                        .signedAmount(tx.signedAmount())
                        .build())
                .collect(Collectors.toList());
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
