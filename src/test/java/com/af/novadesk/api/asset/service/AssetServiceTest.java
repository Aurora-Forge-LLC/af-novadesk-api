package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.DepreciationMethod;
import com.af.novadesk.api.asset.dto.AssetDto;
import com.af.novadesk.api.asset.dto.AssetPageDto;
import com.af.novadesk.api.asset.dto.AssetRegistrationRequest;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.exception.DuplicateSerialNumberException;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.service.impl.AssetOutboxServiceImpl;
import com.af.novadesk.api.asset.service.impl.AssetServiceImpl;
import com.af.novadesk.api.asset.service.impl.QrCodeServiceImpl;
import com.af.novadesk.api.common.service.FileStorageService;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetService")
class AssetServiceTest {

    @Mock private AssetRepository        assetRepository;
    @Mock private LegalEntityRepository  legalEntityRepository;
    @Mock private AssetMapper            assetMapper;
    @Mock private FinanceSecurityContext securityContext;
    @Mock private DepreciationService    depreciationService;
    @Mock private QrCodeServiceImpl      qrCodeService;
    @Mock private AssetOutboxServiceImpl outboxService;
    @Mock private FileStorageService     fileStorageService;

    @InjectMocks
    private AssetServiceImpl service;

    @Captor
    private ArgumentCaptor<Asset> assetCaptor;

    private UUID orgId;
    private UUID entityId;
    private UUID assetId;
    private LegalEntity legalEntity;
    private Asset availableAsset;
    private AssetDto assetDto;
    private AssetRegistrationRequest regRequest;

    @BeforeEach
    void setUp() {
        orgId    = UUID.fromString("00000000-0000-0000-0000-000000000001");
        entityId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assetId  = UUID.fromString("00000000-0000-0000-0000-000000000010");

        legalEntity = LegalEntity.builder()
                .id(entityId)
                .entityName("Sajha Sarkar")
                .entityCode("SAJ01")
                .baseCurrency("NPR")
                .organizationId(orgId)
                .build();

        availableAsset = Asset.builder()
                .legalEntity(legalEntity)
                .organizationId(orgId)
                .category(AssetCategory.LAPTOP)
                .assetType("Test Laptop")
                .serialNumber("SN-TEST-001")
                .purchaseDate(LocalDate.of(2026, 1, 15))
                .purchaseCost(new BigDecimal("250000.00"))
                .currencyCode("NPR")
                .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                .usefulLifeYears(3)
                .netBookValue(new BigDecimal("250000.00"))
                .accumulatedDepreciation(BigDecimal.ZERO)
                .assetStatus(AssetStatus.AVAILABLE)
                .build();

        assetDto = new AssetDto();
        assetDto.setId(assetId);
        assetDto.setAssetStatus(AssetStatus.AVAILABLE);
        assetDto.setCurrencyCode("NPR");

        regRequest = new AssetRegistrationRequest();
        regRequest.setLegalEntityId(entityId);
        regRequest.setCategory(AssetCategory.LAPTOP);
        regRequest.setAssetType("Test Laptop");
        regRequest.setSerialNumber("SN-TEST-001");
        regRequest.setPurchaseDate(LocalDate.of(2026, 1, 15));
        regRequest.setPurchaseCost(new BigDecimal("250000.00"));
        regRequest.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        regRequest.setUsefulLifeYears(3);
    }

    // =========================================================================
    // register
    // =========================================================================

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should register asset, generate depreciation schedule and publish event")
        void shouldRegisterAsset() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(legalEntity));
            when(assetRepository.existsBySerialNumberAndOrganizationId("SN-TEST-001", orgId))
                    .thenReturn(false);
            when(assetRepository.save(any(Asset.class))).thenReturn(availableAsset);
            when(assetMapper.toDto(availableAsset)).thenReturn(assetDto);
            doNothing().when(outboxService).publishAssetPurchased(any());

            AssetDto result = service.register(regRequest);

            verify(assetRepository).save(assetCaptor.capture());
            Asset saved = assetCaptor.getValue();
            assertThat(saved.getSerialNumber()).isEqualTo("SN-TEST-001");
            assertThat(saved.getCurrencyCode()).isEqualTo("NPR");
            assertThat(saved.getAssetStatus()).isEqualTo(AssetStatus.AVAILABLE);
            assertThat(saved.getNetBookValue()).isEqualByComparingTo(new BigDecimal("250000.00"));

            verify(depreciationService).generateSchedule(any());
            verify(outboxService).publishAssetPurchased(availableAsset);
            assertThat(result).isEqualTo(assetDto);
        }

        @Test
        @DisplayName("should trim and uppercase serial number before uniqueness check")
        void shouldNormalizeSerialNumber() {
            regRequest.setSerialNumber("  sn-test-001  ");
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(legalEntity));
            when(assetRepository.existsBySerialNumberAndOrganizationId("SN-TEST-001", orgId))
                    .thenReturn(false);
            when(assetRepository.save(any(Asset.class))).thenReturn(availableAsset);
            when(assetMapper.toDto(availableAsset)).thenReturn(assetDto);

            service.register(regRequest);

            verify(assetRepository).save(assetCaptor.capture());
            assertThat(assetCaptor.getValue().getSerialNumber()).isEqualTo("SN-TEST-001");
        }

        @Test
        @DisplayName("should inherit currencyCode from legal entity baseCurrency")
        void shouldSetCurrencyFromEntity() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(legalEntity));
            when(assetRepository.existsBySerialNumberAndOrganizationId(any(), any()))
                    .thenReturn(false);
            when(assetRepository.save(any(Asset.class))).thenReturn(availableAsset);
            when(assetMapper.toDto(any())).thenReturn(assetDto);

            service.register(regRequest);

            verify(assetRepository).save(assetCaptor.capture());
            assertThat(assetCaptor.getValue().getCurrencyCode()).isEqualTo("NPR");
        }

        @Test
        @DisplayName("should throw DuplicateSerialNumberException when serial already exists")
        void shouldThrowOnDuplicateSerial() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(legalEntity));
            when(assetRepository.existsBySerialNumberAndOrganizationId("SN-TEST-001", orgId))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.register(regRequest))
                    .isInstanceOf(DuplicateSerialNumberException.class)
                    .hasMessageContaining("SN-TEST-001");

            verify(assetRepository, never()).save(any());
            verify(depreciationService, never()).generateSchedule(any());
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when legal entity not found")
        void shouldThrowWhenEntityNotFound() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.register(regRequest))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(assetRepository, never()).save(any());
        }

        @Test
        @DisplayName("should continue without QR URL when QR generation fails")
        void shouldContinueWhenQrFails() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(legalEntity));
            when(assetRepository.existsBySerialNumberAndOrganizationId(any(), any()))
                    .thenReturn(false);
            when(assetRepository.save(any(Asset.class))).thenReturn(availableAsset);
            when(assetMapper.toDto(any())).thenReturn(assetDto);
            doThrow(new RuntimeException("MinIO unavailable"))
                    .when(qrCodeService).generateAndStore(any());

            AssetDto result = service.register(regRequest);

            assertThat(result).isNotNull();
            verify(assetRepository, atLeastOnce()).save(any());
        }
    }

    // =========================================================================
    // getById
    // =========================================================================

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("should return DTO when asset found in org")
        void shouldReturnAsset() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(availableAsset));
            when(assetMapper.toDto(availableAsset)).thenReturn(assetDto);

            AssetDto result = service.getById(assetId);

            assertThat(result).isEqualTo(assetDto);
        }

        @Test
        @DisplayName("should throw AssetNotFoundException when asset not found")
        void shouldThrowWhenNotFound() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(assetId))
                    .isInstanceOf(AssetNotFoundException.class)
                    .hasMessageContaining(assetId.toString());
        }
    }

    // =========================================================================
    // list
    // =========================================================================

    @Nested
    @DisplayName("list")
    class ListAssets {

        @Test
        @DisplayName("should return page of all assets when no filter")
        void shouldListAll() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Asset> page = new PageImpl<>(List.of(availableAsset), pageable, 1);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.search(orgId, null, null, null, pageable)).thenReturn(page);
            when(assetMapper.toDtoList(any())).thenReturn(List.of(assetDto));

            AssetPageDto result = service.list(null, null, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should filter by status when provided")
        void shouldFilterByStatus() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Asset> page = new PageImpl<>(List.of(availableAsset), pageable, 1);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.search(orgId, null, AssetStatus.AVAILABLE, null, pageable))
                    .thenReturn(page);
            when(assetMapper.toDtoList(any())).thenReturn(List.of(assetDto));

            AssetPageDto result = service.list(null, AssetStatus.AVAILABLE, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(assetRepository).search(orgId, null, AssetStatus.AVAILABLE, null, pageable);
        }

        @Test
        @DisplayName("should filter by category when provided")
        void shouldFilterByCategory() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Asset> page = new PageImpl<>(List.of(availableAsset), pageable, 1);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.search(orgId, null, null, AssetCategory.LAPTOP, pageable))
                    .thenReturn(page);
            when(assetMapper.toDtoList(any())).thenReturn(List.of(assetDto));

            AssetPageDto result = service.list(null, null, AssetCategory.LAPTOP, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(assetRepository).search(orgId, null, null, AssetCategory.LAPTOP, pageable);
        }

        @Test
        @DisplayName("should filter by legalEntityId when provided")
        void shouldFilterByEntity() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Asset> page = new PageImpl<>(List.of(availableAsset), pageable, 1);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.search(orgId, entityId, null, null, pageable))
                    .thenReturn(page);
            when(assetMapper.toDtoList(any())).thenReturn(List.of(assetDto));

            AssetPageDto result = service.list(entityId, null, null, pageable);

            verify(assetRepository).search(orgId, entityId, null, null, pageable);
        }

        @Test
        @DisplayName("should combine legalEntityId with status filter")
        void shouldFilterByEntityAndStatus() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Asset> page = new PageImpl<>(List.of(availableAsset), pageable, 1);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.search(orgId, entityId, AssetStatus.AVAILABLE, null, pageable))
                    .thenReturn(page);
            when(assetMapper.toDtoList(any())).thenReturn(List.of(assetDto));

            AssetPageDto result = service.list(entityId, AssetStatus.AVAILABLE, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(assetRepository).search(orgId, entityId, AssetStatus.AVAILABLE, null, pageable);
        }

        @Test
        @DisplayName("should return empty page when no assets exist")
        void shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Asset> emptyPage = Page.empty(pageable);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.search(orgId, null, null, null, pageable)).thenReturn(emptyPage);
            when(assetMapper.toDtoList(List.of())).thenReturn(List.of());

            AssetPageDto result = service.list(null, null, null, pageable);

            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }
    }

    // =========================================================================
    // generateNextSerialNumber
    // =========================================================================

    @Nested
    @DisplayName("generateNextSerialNumber")
    class GenerateNextSerialNumber {

        private final int year = LocalDate.now().getYear();

        @Test
        @DisplayName("should generate first serial number of the year for a category")
        void shouldGenerateFirstSerial() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            String prefix = "LAP-" + year + "-";
            when(assetRepository.countByOrganizationIdAndSerialNumberStartingWith(orgId, prefix)).thenReturn(0L);
            when(assetRepository.existsBySerialNumberAndOrganizationId(prefix + "0001", orgId)).thenReturn(false);

            String result = service.generateNextSerialNumber(AssetCategory.LAPTOP);

            assertThat(result).isEqualTo(prefix + "0001");
        }

        @Test
        @DisplayName("should continue sequence from existing count")
        void shouldContinueSequence() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            String prefix = "DSK-" + year + "-";
            when(assetRepository.countByOrganizationIdAndSerialNumberStartingWith(orgId, prefix)).thenReturn(7L);
            when(assetRepository.existsBySerialNumberAndOrganizationId(prefix + "0008", orgId)).thenReturn(false);

            String result = service.generateNextSerialNumber(AssetCategory.DESKTOP);

            assertThat(result).isEqualTo(prefix + "0008");
        }

        @Test
        @DisplayName("should skip serial numbers already taken due to collision")
        void shouldSkipCollisions() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            String prefix = "SRV-" + year + "-";
            when(assetRepository.countByOrganizationIdAndSerialNumberStartingWith(orgId, prefix)).thenReturn(0L);
            when(assetRepository.existsBySerialNumberAndOrganizationId(prefix + "0001", orgId)).thenReturn(true);
            when(assetRepository.existsBySerialNumberAndOrganizationId(prefix + "0002", orgId)).thenReturn(false);

            String result = service.generateNextSerialNumber(AssetCategory.SERVER);

            assertThat(result).isEqualTo(prefix + "0002");
        }

        @Test
        @DisplayName("should use OTH prefix for OTHER category")
        void shouldUseOthPrefixForOther() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            String prefix = "OTH-" + year + "-";
            when(assetRepository.countByOrganizationIdAndSerialNumberStartingWith(orgId, prefix)).thenReturn(0L);
            when(assetRepository.existsBySerialNumberAndOrganizationId(prefix + "0001", orgId)).thenReturn(false);

            String result = service.generateNextSerialNumber(AssetCategory.OTHER);

            assertThat(result).isEqualTo(prefix + "0001");
        }
    }

    // =========================================================================
    // getManufacturerSuggestions
    // =========================================================================

    @Nested
    @DisplayName("getManufacturerSuggestions")
    class GetManufacturerSuggestions {

        @Test
        @DisplayName("should return manufacturers used by this org for the category, most-used first")
        void shouldReturnManufacturers() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findManufacturersByOrganizationIdAndCategory(orgId, AssetCategory.LAPTOP))
                    .thenReturn(List.of("Dell", "HP"));

            List<String> result = service.getManufacturerSuggestions(AssetCategory.LAPTOP);

            assertThat(result).containsExactly("Dell", "HP");
        }

        @Test
        @DisplayName("should return empty list when org has no history for the category")
        void shouldReturnEmptyWhenNoHistory() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findManufacturersByOrganizationIdAndCategory(orgId, AssetCategory.TABLET))
                    .thenReturn(List.of());

            List<String> result = service.getManufacturerSuggestions(AssetCategory.TABLET);

            assertThat(result).isEmpty();
        }
    }
}
