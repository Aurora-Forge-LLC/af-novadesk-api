package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.service.FileStorageService;
import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankStatementPageDto;
import com.af.novadesk.api.finance.dto.BankStatementUploadRequest;
import com.af.novadesk.api.finance.dto.BankTransactionDto;
import com.af.novadesk.api.finance.dto.BankTransactionPageDto;
import com.af.novadesk.api.finance.entity.BankStatement;
import com.af.novadesk.api.finance.entity.BankTransaction;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.DuplicateStatementException;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.exception.StatementNotFoundException;
import com.af.novadesk.api.finance.exception.ShadowUserNotFoundException;
import com.af.novadesk.api.finance.exception.StatementParseException;
import com.af.novadesk.api.finance.mapper.BankStatementMapper;
import com.af.novadesk.api.finance.repository.BankStatementRepository;
import com.af.novadesk.api.finance.repository.BankTransactionRepository;
import com.af.novadesk.api.finance.repository.EntityBankAccountRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.BankReconciliationService;
import com.af.novadesk.api.finance.service.StatementParser;
import com.af.novadesk.api.finance.service.StatementParserFactory;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements the Bank Reconciliation module (LLR-BNK-01.x).
 *
 * <h3>Upload workflow</h3>
 * <ol>
 *   <li>Extract {@code orgId} + {@code authUserId} from the verified JWT.</li>
 *   <li>Resolve {@link LegalEntity} by {@code entityId + orgId}.</li>
 *   <li>Resolve {@link EntityBankAccount} by {@code bankAccountId}; assert belongs to entity.</li>
 *   <li>Check for duplicate/overlapping active statements.</li>
 *   <li>Validate file type (CSV, XLSX) and size (max 15 MB).</li>
 *   <li>Build storage key: {@code {orgId}/bank-statements/{entityId}/{uuid}.{ext}}.</li>
 *   <li>Upload file to MinIO/S3.</li>
 *   <li>Persist {@link BankStatement} metadata.</li>
 *   <li>Parse file and persist {@link BankTransaction} rows.</li>
 *   <li>Update statement transaction count and status to PARSED.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankReconciliationServiceImpl implements BankReconciliationService {

    private static final long   MAX_FILE_SIZE    = 15_728_640L; // 15 MB
    private static final Set<String> ALLOWED_TYPES = Set.of("CSV", "XLSX");

    private final BankStatementRepository   bankStatementRepository;
    private final BankTransactionRepository  bankTransactionRepository;
    private final LegalEntityRepository      legalEntityRepository;
    private final EntityBankAccountRepository entityBankAccountRepository;
    private final ShadowUserRepository       shadowUserRepository;
    private final FileStorageService         fileStorageService;
    private final FinanceSecurityContext     securityContext;
    private final BankStatementMapper        bankStatementMapper;
    private final StatementParserFactory     statementParserFactory;

    // =========================================================================
    // LLR-BNK-01.1: Upload
    // =========================================================================

    @Override
    @Transactional
    public BankStatementDto uploadStatement(BankStatementUploadRequest request, MultipartFile file) {
        UUID orgId      = securityContext.getOrganizationId();
        UUID authUserId = securityContext.getAuthUserId();

        // ── 1. Resolve user ────────────────────────────────────────────────────
        ShadowUser uploader = shadowUserRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ShadowUserNotFoundException(authUserId));

        // ── 2. Resolve legal entity (org-scoped) ───────────────────────────────
        LegalEntity legalEntity = legalEntityRepository
                .findByIdAndOrganizationId(request.getEntityId(), orgId)
                .orElseThrow(() -> new EntityNotFoundException(request.getEntityId()));

        // ── 3. Resolve bank account; assert belongs to entity ──────────────────
        EntityBankAccount bankAccount = entityBankAccountRepository.findById(request.getBankAccountId())
                .orElseThrow(() -> new BadRequestException("Bank account not found: " + request.getBankAccountId()));

        if (!bankAccount.getLegalEntity().getId().equals(legalEntity.getId())) {
            throw new BadRequestException(
                    "Bank account does not belong to the selected entity");
        }

        // ── 4. Check for overlapping active statements ─────────────────────────
        Optional<BankStatement> overlapping = bankStatementRepository
                .findOverlappingActive(request.getBankAccountId(),
                        request.getPeriodStart(), request.getPeriodEnd());
        if (overlapping.isPresent()) {
            BankStatement existing = overlapping.get();
            throw new DuplicateStatementException(
                    "A statement already exists for this bank account and period (" +
                    request.getPeriodStart() + " to " + request.getPeriodEnd() + "). " +
                    "Uploaded by " + existing.getUploadedBy().getDisplayName() + " on " +
                    existing.getCreatedAt() + ". If you want to replace it, use the replace endpoint.");
        }

        // ── 5. Validate file ───────────────────────────────────────────────────
        validateFile(file);
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "bank-statement";
        String extension = extractExtension(originalName).toUpperCase();

        if (!ALLOWED_TYPES.contains(extension)) {
            throw new BadRequestException(
                    "Unsupported file type '" + extension + "'. Allowed: CSV, XLSX");
        }

        // ── 6-7. Build storage key and upload to S3 ────────────────────────────
        String storageKey = orgId + "/bank-statements/"
                + legalEntity.getId() + "/"
                + UUID.randomUUID() + "." + extension.toLowerCase();

        try {
            fileStorageService.upload(storageKey, file.getInputStream(),
                    file.getSize(), file.getContentType());
        } catch (Exception ex) {
            throw new BadRequestException("File upload to storage failed: " + ex.getMessage());
        }

        // ── 8. Persist statement metadata ──────────────────────────────────────
        BankStatement statement = BankStatement.builder()
                .legalEntity(legalEntity)
                .bankAccount(bankAccount)
                .uploadedBy(uploader)
                .originalFilename(originalName)
                .storageKey(storageKey)
                .fileType(extension)
                .fileSizeBytes((int) file.getSize())
                .isEncrypted(request.getFilePassword() != null && !request.getFilePassword().isEmpty())
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .notes(request.getNotes())
                .build();

        BankStatement saved = bankStatementRepository.save(statement);

        // ── 9. Parse file and persist transactions ─────────────────────────────
        try {
            StatementParser parser = statementParserFactory.getParser(extension);
            List<BankTransaction> parsedTxns = parser.parse(saved.getId(), file.getInputStream(),
                    request.getFilePassword());

            if (!parsedTxns.isEmpty()) {
                // Assign parent entity relationships
                List<BankTransaction> enrichedTxns = parsedTxns.stream()
                        .map(txn -> {
                            txn.setStatement(saved);
                            txn.setLegalEntity(legalEntity);
                            txn.setBankAccount(bankAccount);
                            return txn;
                        })
                        .collect(Collectors.toList());

                bankTransactionRepository.saveAll(enrichedTxns);
                saved.setTransactionCount(enrichedTxns.size());
            }

            saved.setStatementStatus(com.af.novadesk.api.finance.constants.StatementStatus.PARSED);
            bankStatementRepository.save(saved);

            log.info("Uploaded and parsed statement id={} entity={} transactions={}",
                    saved.getId(), legalEntity.getEntityCode(), saved.getTransactionCount());

        } catch (StatementParseException e) {
            log.warn("Failed to parse statement id={}: {}", saved.getId(), e.getMessage());
            saved.setStatementStatus(com.af.novadesk.api.finance.constants.StatementStatus.FAILED);
            bankStatementRepository.save(saved);
            throw e; // rethrow so the API consumer knows parsing failed
        } catch (Exception e) {
            log.warn("Unexpected error parsing statement id={}: {}", saved.getId(), e.getMessage());
            saved.setStatementStatus(com.af.novadesk.api.finance.constants.StatementStatus.FAILED);
            bankStatementRepository.save(saved);
            throw new StatementParseException("Failed to parse statement file", e);
        }

        return bankStatementMapper.toDto(saved);
    }

    @Override
    @Transactional
    public BankStatementDto replaceStatement(BankStatementUploadRequest request, MultipartFile file) {
        // Supersede all overlapping active statements
        bankStatementRepository.supersedeOverlappingStatements(
                request.getBankAccountId(),
                request.getPeriodStart(),
                request.getPeriodEnd());

        // Upload normally (the duplicate check should now pass)
        return uploadStatement(request, file);
    }

    // =========================================================================
    // LLR-BNK-01: Statement queries
    // =========================================================================

    @Override
    public BankStatementPageDto listStatements(UUID legalEntityId, int page, int size) {
        UUID orgId = securityContext.getOrganizationId();

        // Verify the entity belongs to this org
        legalEntityRepository.findByIdAndOrganizationId(legalEntityId, orgId)
                .orElseThrow(() -> new EntityNotFoundException(legalEntityId));

        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<BankStatement> stmtPage = bankStatementRepository
                .findAllByLegalEntityIdOrderByCreatedAtDesc(legalEntityId, pageRequest);

        List<BankStatementDto> content = stmtPage.getContent().stream()
                .map(bankStatementMapper::toDto)
                .collect(Collectors.toList());

        return new BankStatementPageDto(
                content,
                stmtPage.getNumber(),
                stmtPage.getSize(),
                stmtPage.getTotalElements(),
                stmtPage.getTotalPages()
        );
    }

    @Override
    public BankStatementDto getStatement(UUID statementId) {
        UUID orgId = securityContext.getOrganizationId();
        BankStatement statement = bankStatementRepository.findById(statementId)
                .filter(s -> s.getLegalEntity().getOrganizationId().equals(orgId))
                .orElseThrow(() -> new StatementNotFoundException(statementId));
        return bankStatementMapper.toDto(statement);
    }

    // =========================================================================
    // LLR-BNK-01: Transaction queries
    // =========================================================================

    @Override
    public BankTransactionPageDto listTransactions(UUID statementId, int page, int size) {
        // Verify access (org-scoped)
        getStatement(statementId);

        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "transactionDate"));

        Page<BankTransaction> txnPage = bankTransactionRepository
                .findAllByStatementId(statementId, pageRequest);

        List<BankTransactionDto> content = txnPage.getContent().stream()
                .map(bankStatementMapper::toTxnDto)
                .collect(Collectors.toList());

        return new BankTransactionPageDto(
                content,
                txnPage.getNumber(),
                txnPage.getSize(),
                txnPage.getTotalElements(),
                txnPage.getTotalPages()
        );
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException(
                    "File size " + file.getSize() + " bytes exceeds the 15 MB limit");
        }
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1)
                : "";
    }
}
