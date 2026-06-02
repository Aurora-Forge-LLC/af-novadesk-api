package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.service.impl.LegalEntityServiceImpl;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.dto.ApproveEntityDto;
import com.af.novadesk.api.finance.dto.FiscalYearSettingDto;
import com.af.novadesk.api.finance.dto.LegalEntityDto;
import com.af.novadesk.api.finance.dto.LegalEntityPageDto;
import com.af.novadesk.api.finance.dto.RejectEntityDto;
import com.af.novadesk.api.finance.dto.UpdateEntityStatusRequest;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.DuplicateEntityException;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.exception.InvalidEntityStateException;
import com.af.novadesk.api.finance.mapper.FiscalYearSettingMapper;
import com.af.novadesk.api.finance.mapper.LegalEntityMapper;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
import com.af.novadesk.api.finance.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LegalEntityService}.
 *
 * <p>Validates the full entity lifecycle: creation with uniqueness guards,
 * approval/rejection with state validation, status toggling, and queries.</p>
 *
 * @see LegalEntityService
 * @see LegalEntity
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LegalEntityService")
class LegalEntityServiceTest {

    // -------------------------------------------------------------------------
    // Mocks & Test Fixtures
    // -------------------------------------------------------------------------

    @Mock
    private LegalEntityRepository legalEntityRepository;

    @Mock
    private FiscalYearSettingRepository fiscalYearSettingRepository;

    @Mock
    private LegalEntityMapper mapper;

    @Mock
    private FiscalYearSettingMapper fiscalYearSettingMapper;

    @Mock
    private FiscalYearTemplateService fiscalYearTemplateService;

    @Mock
    private ChartOfAccountTemplateService chartOfAccountTemplateService;

    @Mock
    private BankAccountTemplateService bankAccountTemplateService;

    @Mock
    private AccountTemplateService accountTemplateService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LegalEntityOutboxService outboxService;

    @Mock
    private FinanceSecurityContext securityContext;

    @Mock
    private ShadowUserRepository shadowUserRepository;

    @Mock
    private EntityUserAccessRepository entityUserAccessRepository;

    @InjectMocks
    private LegalEntityServiceImpl service;

    @Captor
    private ArgumentCaptor<LegalEntity> entityCaptor;

    private UUID orgId;
    private UUID authUserId;
    private UUID entityId;
    private LegalEntityDto requestDto;
    private LegalEntity pendingEntity;
    private LegalEntity approvedEntity;
    private LegalEntity rejectedEntity;
    private LegalEntityDto responseDto;

    @BeforeEach
    void setUp() {
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        entityId = UUID.fromString("00000000-0000-0000-0000-000000000010");

        requestDto = new LegalEntityDto();
        requestDto.setEntityName("Test Entity");
        requestDto.setEntityCode("TEST01");
        requestDto.setCountry(CountryCode.US);
        requestDto.setIncorporationDate(LocalDate.of(2020, 1, 15));

        pendingEntity = LegalEntity.builder()
                .id(entityId)
                .entityName("Test Entity")
                .entityCode("TEST01")
                .country(CountryCode.US)
                .baseCurrency("USD")
                .incorporationDate(LocalDate.of(2020, 1, 15))
                .approvalStatus(ApprovalStatus.PENDING)
                .status(Status.ACTIVE)
                .organizationId(orgId)
                .build();

        approvedEntity = LegalEntity.builder()
                .id(entityId)
                .entityName("Test Entity")
                .entityCode("TEST01")
                .country(CountryCode.US)
                .baseCurrency("USD")
                .incorporationDate(LocalDate.of(2020, 1, 15))
                .approvalStatus(ApprovalStatus.APPROVED)
                .status(Status.ACTIVE)
                .organizationId(orgId)
                .build();

        rejectedEntity = LegalEntity.builder()
                .id(entityId)
                .entityName("Test Entity")
                .entityCode("TEST01")
                .country(CountryCode.US)
                .baseCurrency("USD")
                .incorporationDate(LocalDate.of(2020, 1, 15))
                .approvalStatus(ApprovalStatus.REJECTED)
                .status(Status.ACTIVE)
                .organizationId(orgId)
                .build();

        responseDto = new LegalEntityDto();
        responseDto.setId(entityId);
        responseDto.setEntityName("Test Entity");
        responseDto.setEntityCode("TEST01");
        responseDto.setCountry(CountryCode.US);
        responseDto.setBaseCurrency("USD");
        responseDto.setApprovalStatus(ApprovalStatus.PENDING);
        responseDto.setStatus(Status.ACTIVE);
        responseDto.setIncorporationDate(LocalDate.of(2020, 1, 15));
    }

    // =========================================================================
    // createLegalEntity
    // =========================================================================

    @Nested
    @DisplayName("createLegalEntity")
    class CreateLegalEntity {

        @Test
        @DisplayName("should create a PENDING entity and publish created event")
        void shouldCreatePendingEntity() {
            // Arrange
            ShadowUser creator = ShadowUser.builder()
                    .id(authUserId)
                    .authUserId(authUserId)
                    .build();
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.existsByEntityNameAndOrganizationId(requestDto.getEntityName(), orgId))
                    .thenReturn(false);
            when(legalEntityRepository.existsByEntityCodeAndOrganizationId(requestDto.getEntityCode(), orgId))
                    .thenReturn(false);
            when(mapper.toEntity(requestDto)).thenReturn(pendingEntity);
            when(legalEntityRepository.save(any(LegalEntity.class))).thenReturn(pendingEntity);
            when(mapper.toDto(pendingEntity)).thenReturn(responseDto);
            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(creator));
            when(entityUserAccessRepository.save(any(EntityUserAccess.class))).thenReturn(null);

            // Act
            LegalEntityDto result = service.createLegalEntity(requestDto);

            // Assert
            verify(legalEntityRepository).save(entityCaptor.capture());
            LegalEntity saved = entityCaptor.getValue();
            assertThat(saved.getBaseCurrency()).isEqualTo("USD");
            assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
            assertThat(saved.getStatus()).isEqualTo(Status.ACTIVE);

            verify(outboxService).publishEntityCreated(pendingEntity, authUserId, orgId);
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("should throw DuplicateEntityException when entity name already exists")
        void shouldThrowOnDuplicateName() {
            // Arrange
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.existsByEntityNameAndOrganizationId(requestDto.getEntityName(), orgId))
                    .thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.createLegalEntity(requestDto))
                    .isInstanceOf(DuplicateEntityException.class)
                    .hasMessageContaining("name")
                    .hasMessageContaining(requestDto.getEntityName());

            verify(legalEntityRepository, never()).save(any());
            verify(outboxService, never()).publishEntityCreated(any(), any(), any());
        }

        @Test
        @DisplayName("should throw DuplicateEntityException when entity code already exists")
        void shouldThrowOnDuplicateCode() {
            // Arrange
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.existsByEntityNameAndOrganizationId(requestDto.getEntityName(), orgId))
                    .thenReturn(false);
            when(legalEntityRepository.existsByEntityCodeAndOrganizationId(requestDto.getEntityCode(), orgId))
                    .thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.createLegalEntity(requestDto))
                    .isInstanceOf(DuplicateEntityException.class)
                    .hasMessageContaining("code")
                    .hasMessageContaining(requestDto.getEntityCode());

            verify(legalEntityRepository, never()).save(any());
            verify(outboxService, never()).publishEntityCreated(any(), any(), any());
        }

        @Test
        @DisplayName("should set base currency from country code")
        void shouldSetBaseCurrencyFromCountry() {
            // Arrange
            requestDto.setCountry(CountryCode.IN);
            ShadowUser creator = ShadowUser.builder()
                    .id(authUserId)
                    .authUserId(authUserId)
                    .build();
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.existsByEntityNameAndOrganizationId(requestDto.getEntityName(), orgId))
                    .thenReturn(false);
            when(legalEntityRepository.existsByEntityCodeAndOrganizationId(requestDto.getEntityCode(), orgId))
                    .thenReturn(false);
            when(mapper.toEntity(requestDto)).thenReturn(pendingEntity);
            when(legalEntityRepository.save(any(LegalEntity.class))).thenReturn(pendingEntity);
            when(mapper.toDto(pendingEntity)).thenReturn(responseDto);
            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(creator));
            when(entityUserAccessRepository.save(any(EntityUserAccess.class))).thenReturn(null);

            // Act
            service.createLegalEntity(requestDto);

            // Assert
            verify(legalEntityRepository).save(entityCaptor.capture());
            assertThat(entityCaptor.getValue().getBaseCurrency()).isEqualTo("INR");
        }
    }

    // =========================================================================
    // approveEntity
    // =========================================================================

    @Nested
    @DisplayName("approveEntity")
    class ApproveEntity {

        @Test
        @DisplayName("should approve a PENDING entity and init fiscal year")
        void shouldApprovePendingEntity() {
            // Arrange
            ApproveEntityDto approveDto = new ApproveEntityDto();
            approveDto.setFiscalYearOverride(null);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(pendingEntity));
            when(chartOfAccountTemplateService.buildFromCountry(CountryCode.US))
                    .thenReturn(List.of());
            when(bankAccountTemplateService.buildFromCountry(CountryCode.US))
                    .thenReturn(List.of());
            when(accountTemplateService.buildFromCountry(CountryCode.US, pendingEntity))
                    .thenReturn(List.of());
            when(fiscalYearTemplateService.buildFromCountry(CountryCode.US))
                    .thenReturn(new FiscalYearSetting());
            when(legalEntityRepository.save(any(LegalEntity.class))).thenReturn(approvedEntity);
            when(mapper.toDto(approvedEntity)).thenReturn(responseDto);

            // Act
            LegalEntityDto result = service.approveEntity(entityId, approveDto);

            // Assert
            verify(accountTemplateService).buildFromCountry(CountryCode.US, pendingEntity);
            verify(accountRepository).saveAll(List.of());
            verify(fiscalYearTemplateService).buildFromCountry(CountryCode.US);
            verify(fiscalYearSettingRepository).save(any(FiscalYearSetting.class));
            verify(legalEntityRepository).save(entityCaptor.capture());
            assertThat(entityCaptor.getValue().getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
            verify(outboxService).publishEntityApproved(approvedEntity, authUserId, orgId);
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("should use fiscal year override when provided")
        void shouldUseFiscalYearOverride() {
            // Arrange
            FiscalYearSettingDto overrideDto = new FiscalYearSettingDto();
            overrideDto.setFiscalStartMonth(4);
            overrideDto.setFiscalStartDay(1);
            overrideDto.setFiscalEndMonth(3);
            overrideDto.setFiscalEndDay(31);
            overrideDto.setCurrentFiscalYear(2025);
            overrideDto.setPeriodsPerYear(12);

            ApproveEntityDto approveDto = new ApproveEntityDto();
            approveDto.setFiscalYearOverride(overrideDto);

            FiscalYearSetting overrideEntity = new FiscalYearSetting();

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(pendingEntity));
            when(chartOfAccountTemplateService.buildFromCountry(CountryCode.US))
                    .thenReturn(List.of());
            when(bankAccountTemplateService.buildFromCountry(CountryCode.US))
                    .thenReturn(List.of());
            when(accountTemplateService.buildFromCountry(CountryCode.US, pendingEntity))
                    .thenReturn(List.of());
            when(mapper.toEntity(overrideDto)).thenReturn(overrideEntity);
            when(legalEntityRepository.save(any(LegalEntity.class))).thenReturn(approvedEntity);
            when(mapper.toDto(approvedEntity)).thenReturn(responseDto);

            // Act
            LegalEntityDto result = service.approveEntity(entityId, approveDto);

            // Assert
            verify(fiscalYearTemplateService, never()).buildFromCountry(any());
            verify(fiscalYearSettingRepository).save(overrideEntity);
            assertThat(overrideEntity.getLegalEntity()).isEqualTo(pendingEntity);
            verify(outboxService).publishEntityApproved(approvedEntity, authUserId, orgId);
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("should throw InvalidEntityStateException when entity is not PENDING")
        void shouldThrowWhenNotPending() {
            // Arrange
            ApproveEntityDto approveDto = new ApproveEntityDto();
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(approvedEntity));

            // Act & Assert
            assertThatThrownBy(() -> service.approveEntity(entityId, approveDto))
                    .isInstanceOf(InvalidEntityStateException.class)
                    .hasMessageContaining("approve")
                    .hasMessageContaining(entityId.toString())
                    .hasMessageContaining(ApprovalStatus.APPROVED.name());

            verify(outboxService, never()).publishEntityApproved(any(), any(), any());
        }

        @Test
        @DisplayName("should throw InvalidEntityStateException when entity is REJECTED")
        void shouldThrowWhenRejected() {
            // Arrange
            ApproveEntityDto approveDto = new ApproveEntityDto();
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(rejectedEntity));

            // Act & Assert
            assertThatThrownBy(() -> service.approveEntity(entityId, approveDto))
                    .isInstanceOf(InvalidEntityStateException.class)
                    .hasMessageContaining("approve")
                    .hasMessageContaining(entityId.toString())
                    .hasMessageContaining(ApprovalStatus.REJECTED.name());

            verify(outboxService, never()).publishEntityApproved(any(), any(), any());
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when entity does not exist")
        void shouldThrowWhenEntityNotFound() {
            // Arrange
            ApproveEntityDto approveDto = new ApproveEntityDto();
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.approveEntity(entityId, approveDto))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(entityId.toString());

            verify(outboxService, never()).publishEntityApproved(any(), any(), any());
        }
    }

    // =========================================================================
    // rejectEntity
    // =========================================================================

    @Nested
    @DisplayName("rejectEntity")
    class RejectEntity {

        @Test
        @DisplayName("should reject a PENDING entity and publish rejected event")
        void shouldRejectPendingEntity() {
            // Arrange
            RejectEntityDto rejectDto = new RejectEntityDto();
            rejectDto.setReason("Incomplete documentation");

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(pendingEntity));
            when(legalEntityRepository.save(any(LegalEntity.class))).thenReturn(rejectedEntity);
            when(mapper.toDto(rejectedEntity)).thenReturn(responseDto);

            // Act
            LegalEntityDto result = service.rejectEntity(entityId, rejectDto);

            // Assert
            verify(legalEntityRepository).save(entityCaptor.capture());
            assertThat(entityCaptor.getValue().getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
            verify(outboxService).publishEntityRejected(rejectedEntity, "Incomplete documentation", authUserId, orgId);
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("should throw InvalidEntityStateException when entity is not PENDING")
        void shouldThrowWhenNotPending() {
            // Arrange
            RejectEntityDto rejectDto = new RejectEntityDto();
            rejectDto.setReason("Reason");
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(rejectedEntity));

            // Act & Assert
            assertThatThrownBy(() -> service.rejectEntity(entityId, rejectDto))
                    .isInstanceOf(InvalidEntityStateException.class)
                    .hasMessageContaining("reject")
                    .hasMessageContaining(entityId.toString())
                    .hasMessageContaining(ApprovalStatus.REJECTED.name());

            verify(legalEntityRepository, never()).save(any());
            verify(outboxService, never()).publishEntityRejected(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when entity does not exist")
        void shouldThrowWhenEntityNotFound() {
            // Arrange
            RejectEntityDto rejectDto = new RejectEntityDto();
            rejectDto.setReason("Reason");
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.rejectEntity(entityId, rejectDto))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(entityId.toString());

            verify(legalEntityRepository, never()).save(any());
            verify(outboxService, never()).publishEntityRejected(any(), any(), any(), any());
        }
    }

    // =========================================================================
    // updateStatus
    // =========================================================================

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("should toggle status and publish status changed event")
        void shouldToggleStatus() {
            // Arrange
            UpdateEntityStatusRequest statusUpdateDto = new UpdateEntityStatusRequest();
            statusUpdateDto.setStatus(Status.INACTIVE);

            LegalEntity inactiveEntity = LegalEntity.builder()
                    .id(entityId)
                    .entityName("Test Entity")
                    .entityCode("TEST01")
                    .country(CountryCode.US)
                    .baseCurrency("USD")
                    .incorporationDate(LocalDate.of(2020, 1, 15))
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .status(Status.INACTIVE)
                    .organizationId(orgId)
                    .build();

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(approvedEntity));
            when(legalEntityRepository.save(any(LegalEntity.class))).thenReturn(inactiveEntity);
            when(mapper.toDto(inactiveEntity)).thenReturn(responseDto);

            // Act
            LegalEntityDto result = service.updateStatus(entityId, statusUpdateDto);

            // Assert
            verify(legalEntityRepository).save(entityCaptor.capture());
            assertThat(entityCaptor.getValue().getStatus()).isEqualTo(Status.INACTIVE);
            verify(outboxService).publishStatusChanged(
                    eq(inactiveEntity), eq(Status.ACTIVE), eq(Status.INACTIVE),
                    eq(authUserId), eq(orgId));
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when entity does not exist")
        void shouldThrowWhenEntityNotFound() {
            // Arrange
            UpdateEntityStatusRequest statusUpdateDto = new UpdateEntityStatusRequest();
            statusUpdateDto.setStatus(Status.INACTIVE);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.updateStatus(entityId, statusUpdateDto))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(entityId.toString());

            verify(legalEntityRepository, never()).save(any());
            verify(outboxService, never()).publishStatusChanged(any(), any(), any(), any(), any());
        }
    }

    // =========================================================================
    // Queries
    // =========================================================================

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("getById should return entity DTO when found")
        void shouldGetById() {
            // Arrange
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(pendingEntity));
            when(mapper.toDto(pendingEntity)).thenReturn(responseDto);

            // Act
            LegalEntityDto result = service.getById(entityId);

            // Assert
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("getById should throw EntityNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            // Arrange
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.getById(entityId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(entityId.toString());
        }

        @Test
        @DisplayName("listAll should return paginated results")
        void shouldListAll() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<LegalEntity> entities = List.of(pendingEntity, approvedEntity);
            Page<LegalEntity> page = new PageImpl<>(entities, pageable, 2);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findAllByOrganizationId(orgId, pageable)).thenReturn(page);
            when(mapper.toSummaryDtoList(entities)).thenReturn(List.of());

            // Act
            LegalEntityPageDto result = service.listAll(pageable);

            // Assert
            assertThat(result.getPage()).isZero();
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(1);
            verify(mapper).toSummaryDtoList(entities);
        }

        @Test
        @DisplayName("listAll should return empty page when no entities")
        void shouldReturnEmptyPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<LegalEntity> emptyPage = Page.empty(pageable);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findAllByOrganizationId(orgId, pageable)).thenReturn(emptyPage);
            when(mapper.toSummaryDtoList(emptyPage.getContent())).thenReturn(List.of());

            // Act
            LegalEntityPageDto result = service.listAll(pageable);

            // Assert
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }
    }
}
