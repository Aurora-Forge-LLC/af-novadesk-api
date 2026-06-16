package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.constants.*;
import com.af.novadesk.api.asset.dto.AssetDto;
import com.af.novadesk.api.asset.dto.WriteOffRequest;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.AssetWriteOff;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.exception.InvalidAssetStateException;
import com.af.novadesk.api.asset.entity.AssetAssignment;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.AssetAssignmentRepository;
import com.af.novadesk.api.asset.repository.AssetCustodyTransferRepository;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.repository.AssetWriteOffRepository;
import com.af.novadesk.api.asset.service.impl.AssetOutboxServiceImpl;
import com.af.novadesk.api.asset.service.impl.AssetWriteOffServiceImpl;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.BadRequestException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetWriteOffService")
class AssetWriteOffServiceTest {

    @Mock private AssetRepository                assetRepository;
    @Mock private AssetAssignmentRepository      assignmentRepository;
    @Mock private AssetWriteOffRepository        writeOffRepository;
    @Mock private AssetCustodyTransferRepository custodyRepository;
    @Mock private AssetMapper                    assetMapper;
    @Mock private AssetOutboxServiceImpl         outboxService;
    @Mock private FinanceSecurityContext         securityContext;

    @InjectMocks
    private AssetWriteOffServiceImpl service;

    @Captor private ArgumentCaptor<AssetWriteOff> writeOffCaptor;
    @Captor private ArgumentCaptor<Asset>         assetCaptor;

    private UUID orgId;
    private UUID authUserId;
    private UUID assetId;
    private UUID writeOffId;
    private UUID employeeId;
    private Asset assignedAsset;
    private AssetWriteOff pendingWriteOff;
    private WriteOffRequest writeOffRequest;

    @BeforeEach
    void setUp() {
        orgId      = UUID.fromString("00000000-0000-0000-0000-000000000001");
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assetId    = UUID.fromString("00000000-0000-0000-0000-000000000010");
        writeOffId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000020");

        LegalEntity legalEntity = LegalEntity.builder()
                .id(UUID.randomUUID()).baseCurrency("NPR").organizationId(orgId).build();

        assignedAsset = Asset.builder()
                .legalEntity(legalEntity)
                .organizationId(orgId)
                .category(AssetCategory.LAPTOP)
                .assetType("Test Laptop")
                .serialNumber("SN-001")
                .purchaseDate(LocalDate.of(2026, 1, 15))
                .purchaseCost(new BigDecimal("250000.00"))
                .currencyCode("NPR")
                .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                .usefulLifeYears(3)
                .netBookValue(new BigDecimal("200000.00"))
                .accumulatedDepreciation(new BigDecimal("50000.00"))
                .assetStatus(AssetStatus.ASSIGNED)
                .build();

        pendingWriteOff = AssetWriteOff.builder()
                .asset(assignedAsset)
                .organizationId(orgId)
                .requestedBy(authUserId)
                .lastCustodianId(employeeId)
                .reason(WriteOffReason.LOST)
                .depreciatedValue(new BigDecimal("200000.00"))
                .writeOffStatus(WriteOffStatus.PENDING)
                .build();

        writeOffRequest = new WriteOffRequest();
        writeOffRequest.setReason(WriteOffReason.LOST);
    }

    // =========================================================================
    // requestWriteOff
    // =========================================================================

    @Nested
    @DisplayName("requestWriteOff")
    class RequestWriteOff {

        @Test
        @DisplayName("should create PENDING write-off and set asset status to LOST")
        void shouldRequestWriteOff() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(assignedAsset));
            when(writeOffRepository.findByAssetId(assetId)).thenReturn(Optional.empty());
            when(writeOffRepository.save(any())).thenReturn(pendingWriteOff);
            when(assetRepository.save(any())).thenReturn(assignedAsset);

            AssetWriteOff result = service.requestWriteOff(assetId, writeOffRequest);

            verify(writeOffRepository).save(writeOffCaptor.capture());
            AssetWriteOff saved = writeOffCaptor.getValue();
            assertThat(saved.getWriteOffStatus()).isEqualTo(WriteOffStatus.PENDING);
            assertThat(saved.getReason()).isEqualTo(WriteOffReason.LOST);
            assertThat(saved.getDepreciatedValue()).isEqualByComparingTo(new BigDecimal("200000.00"));

            verify(assetRepository).save(assetCaptor.capture());
            assertThat(assetCaptor.getValue().getAssetStatus()).isEqualTo(AssetStatus.LOST);
        }

        @Test
        @DisplayName("should also allow write-off for already LOST asset")
        void shouldAllowWriteOffForLostAsset() {
            Asset lostAsset = Asset.builder()
                    .legalEntity(assignedAsset.getLegalEntity())
                    .organizationId(orgId)
                    .assetStatus(AssetStatus.LOST)
                    .serialNumber("SN-002")
                    .purchaseDate(LocalDate.now())
                    .purchaseCost(BigDecimal.ONE)
                    .currencyCode("NPR")
                    .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                    .usefulLifeYears(3)
                    .netBookValue(BigDecimal.ONE)
                    .accumulatedDepreciation(BigDecimal.ZERO)
                    .assetType("Test").category(AssetCategory.LAPTOP)
                    .build();

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(lostAsset));
            when(writeOffRepository.findByAssetId(assetId)).thenReturn(Optional.empty());
            when(writeOffRepository.save(any())).thenReturn(pendingWriteOff);
            when(assetRepository.save(any())).thenReturn(lostAsset);

            assertThat(service.requestWriteOff(assetId, writeOffRequest)).isNotNull();
        }

        @Test
        @DisplayName("should throw BadRequestException when write-off already exists")
        void shouldThrowOnDuplicateWriteOff() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(assignedAsset));
            when(writeOffRepository.findByAssetId(assetId)).thenReturn(Optional.of(pendingWriteOff));

            assertThatThrownBy(() -> service.requestWriteOff(assetId, writeOffRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already exists");

            verify(writeOffRepository, never()).save(any());
            verify(assetRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidAssetStateException when asset is DISPOSED")
        void shouldThrowWhenAssetDisposed() {
            Asset disposedAsset = Asset.builder()
                    .legalEntity(assignedAsset.getLegalEntity())
                    .organizationId(orgId)
                    .assetStatus(AssetStatus.DISPOSED)
                    .serialNumber("SN-001")
                    .purchaseDate(LocalDate.now())
                    .purchaseCost(BigDecimal.ONE)
                    .currencyCode("NPR")
                    .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                    .usefulLifeYears(3)
                    .netBookValue(BigDecimal.ONE)
                    .accumulatedDepreciation(BigDecimal.ZERO)
                    .assetType("Test").category(AssetCategory.LAPTOP)
                    .build();

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(disposedAsset));

            assertThatThrownBy(() -> service.requestWriteOff(assetId, writeOffRequest))
                    .isInstanceOf(InvalidAssetStateException.class)
                    .hasMessageContaining("write-off")
                    .hasMessageContaining("DISPOSED");
        }

        @Test
        @DisplayName("should also allow write-off for AVAILABLE asset")
        void shouldAllowWriteOffForAvailableAsset() {
            Asset availableAsset = Asset.builder()
                    .legalEntity(assignedAsset.getLegalEntity())
                    .organizationId(orgId)
                    .assetStatus(AssetStatus.AVAILABLE)
                    .serialNumber("SN-003")
                    .purchaseDate(LocalDate.now())
                    .purchaseCost(BigDecimal.ONE)
                    .currencyCode("NPR")
                    .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                    .usefulLifeYears(3)
                    .netBookValue(BigDecimal.ONE)
                    .accumulatedDepreciation(BigDecimal.ZERO)
                    .assetType("Test").category(AssetCategory.LAPTOP)
                    .build();

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(availableAsset));
            when(writeOffRepository.findByAssetId(assetId)).thenReturn(Optional.empty());
            when(writeOffRepository.save(any())).thenReturn(pendingWriteOff);
            when(assetRepository.save(any())).thenReturn(availableAsset);

            assertThat(service.requestWriteOff(assetId, writeOffRequest)).isNotNull();
        }

        @Test
        @DisplayName("should throw AssetNotFoundException when asset not in org")
        void shouldThrowWhenNotFound() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.requestWriteOff(assetId, writeOffRequest))
                    .isInstanceOf(AssetNotFoundException.class);
        }
    }

    // =========================================================================
    // approveWriteOff
    // =========================================================================

    @Nested
    @DisplayName("approveWriteOff")
    class ApproveWriteOff {

        @Test
        @DisplayName("should approve PENDING write-off and set asset status to DISPOSED")
        void shouldApproveWriteOff() {
            writeOffRequest.setAction(WriteOffAction.WRITE_OFF);
            writeOffRequest.setAuditNotes("Approved — asset is unrecoverable");

            AssetDto disposedDto = new AssetDto();
            disposedDto.setAssetStatus(AssetStatus.DISPOSED);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(writeOffRepository.findByIdAndOrganizationId(writeOffId, orgId))
                    .thenReturn(Optional.of(pendingWriteOff));
            when(writeOffRepository.save(any())).thenReturn(pendingWriteOff);
            when(assetRepository.save(any())).thenReturn(assignedAsset);
            when(custodyRepository.save(any())).thenReturn(null);
            when(assetMapper.toDto(any())).thenReturn(disposedDto);

            AssetDto result = service.approveWriteOff(writeOffId, writeOffRequest);

            verify(writeOffRepository).save(writeOffCaptor.capture());
            AssetWriteOff saved = writeOffCaptor.getValue();
            assertThat(saved.getWriteOffStatus()).isEqualTo(WriteOffStatus.APPROVED);
            assertThat(saved.getAction()).isEqualTo(WriteOffAction.WRITE_OFF);
            assertThat(saved.getApprovedBy()).isEqualTo(authUserId);

            verify(assetRepository).save(assetCaptor.capture());
            assertThat(assetCaptor.getValue().getAssetStatus()).isEqualTo(AssetStatus.DISPOSED);

            verify(custodyRepository).save(any());
            assertThat(result).isEqualTo(disposedDto);
        }

        @Test
        @DisplayName("should throw BadRequestException when action is missing")
        void shouldThrowWhenActionMissing() {
            writeOffRequest.setAction(null);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(writeOffRepository.findByIdAndOrganizationId(writeOffId, orgId))
                    .thenReturn(Optional.of(pendingWriteOff));

            assertThatThrownBy(() -> service.approveWriteOff(writeOffId, writeOffRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Action is required");
        }

        @Test
        @DisplayName("should throw BadRequestException when write-off is already approved")
        void shouldThrowWhenAlreadyApproved() {
            AssetWriteOff approvedWriteOff = AssetWriteOff.builder()
                    .asset(assignedAsset)
                    .organizationId(orgId)
                    .requestedBy(authUserId)
                    .lastCustodianId(employeeId)
                    .reason(WriteOffReason.LOST)
                    .depreciatedValue(BigDecimal.ONE)
                    .writeOffStatus(WriteOffStatus.APPROVED)
                    .build();

            writeOffRequest.setAction(WriteOffAction.WRITE_OFF);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(writeOffRepository.findByIdAndOrganizationId(writeOffId, orgId))
                    .thenReturn(Optional.of(approvedWriteOff));

            assertThatThrownBy(() -> service.approveWriteOff(writeOffId, writeOffRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("APPROVED");
        }

        @Test
        @DisplayName("should throw BadRequestException when write-off not found")
        void shouldThrowWhenNotFound() {
            writeOffRequest.setAction(WriteOffAction.WRITE_OFF);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(writeOffRepository.findByIdAndOrganizationId(writeOffId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approveWriteOff(writeOffId, writeOffRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Write-off not found");
        }
    }

    // =========================================================================
    // rejectWriteOff
    // =========================================================================

    @Nested
    @DisplayName("rejectWriteOff")
    class RejectWriteOff {

        @Test
        @DisplayName("should reject PENDING write-off and revert asset to ASSIGNED")
        void shouldRejectWriteOff() {
            Asset lostAsset = Asset.builder()
                    .legalEntity(assignedAsset.getLegalEntity())
                    .organizationId(orgId)
                    .assetStatus(AssetStatus.LOST)
                    .serialNumber("SN-001")
                    .purchaseDate(LocalDate.now())
                    .purchaseCost(BigDecimal.ONE)
                    .currencyCode("NPR")
                    .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                    .usefulLifeYears(3)
                    .netBookValue(BigDecimal.ONE)
                    .accumulatedDepreciation(BigDecimal.ZERO)
                    .assetType("Test").category(AssetCategory.LAPTOP)
                    .build();

            AssetWriteOff writeOffForLostAsset = AssetWriteOff.builder()
                    .asset(lostAsset)
                    .organizationId(orgId)
                    .requestedBy(authUserId)
                    .lastCustodianId(employeeId)
                    .reason(WriteOffReason.LOST)
                    .depreciatedValue(BigDecimal.ONE)
                    .writeOffStatus(WriteOffStatus.PENDING)
                    .previousAssetStatus(AssetStatus.ASSIGNED)
                    .build();

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(writeOffRepository.findByIdAndOrganizationId(writeOffId, orgId))
                    .thenReturn(Optional.of(writeOffForLostAsset));
            when(writeOffRepository.save(any())).thenReturn(writeOffForLostAsset);
            when(assetRepository.save(any())).thenReturn(lostAsset);

            service.rejectWriteOff(writeOffId, "Asset was found");

            verify(writeOffRepository).save(writeOffCaptor.capture());
            assertThat(writeOffCaptor.getValue().getWriteOffStatus()).isEqualTo(WriteOffStatus.REJECTED);
            assertThat(writeOffCaptor.getValue().getAuditNotes()).isEqualTo("Asset was found");

            verify(assetRepository).save(assetCaptor.capture());
            assertThat(assetCaptor.getValue().getAssetStatus()).isEqualTo(AssetStatus.ASSIGNED);
        }

        @Test
        @DisplayName("should throw BadRequestException when write-off is already rejected")
        void shouldThrowWhenAlreadyRejected() {
            AssetWriteOff rejectedWriteOff = AssetWriteOff.builder()
                    .asset(assignedAsset)
                    .organizationId(orgId)
                    .requestedBy(authUserId)
                    .lastCustodianId(employeeId)
                    .reason(WriteOffReason.LOST)
                    .depreciatedValue(BigDecimal.ONE)
                    .writeOffStatus(WriteOffStatus.REJECTED)
                    .build();

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(writeOffRepository.findByIdAndOrganizationId(writeOffId, orgId))
                    .thenReturn(Optional.of(rejectedWriteOff));

            assertThatThrownBy(() -> service.rejectWriteOff(writeOffId, "reason"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("REJECTED");
        }
    }
}
