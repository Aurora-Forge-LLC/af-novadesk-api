package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.constants.*;
import com.af.novadesk.api.asset.dto.AssetAssignmentDto;
import com.af.novadesk.api.asset.dto.AssetAssignmentRequest;
import com.af.novadesk.api.asset.dto.OffboardingAssetCheckDto;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.AssetAssignment;
import com.af.novadesk.api.asset.entity.AssetCustodyTransfer;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.exception.InvalidAssetStateException;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.AssetAssignmentRepository;
import com.af.novadesk.api.asset.repository.AssetCustodyTransferRepository;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.service.impl.AssetAssignmentServiceImpl;
import com.af.novadesk.api.asset.service.impl.AssetOutboxServiceImpl;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetAssignmentService")
class AssetAssignmentServiceTest {

    @Mock private AssetRepository                assetRepository;
    @Mock private AssetAssignmentRepository      assignmentRepository;
    @Mock private AssetCustodyTransferRepository custodyRepository;
    @Mock private AssetMapper                    assetMapper;
    @Mock private FinanceSecurityContext         securityContext;
    @Mock private AssetOutboxServiceImpl         outboxService;
    @Mock private CmEmployeeRepository           cmEmployeeRepository;

    @InjectMocks
    private AssetAssignmentServiceImpl service;

    @Captor private ArgumentCaptor<AssetAssignment>      assignmentCaptor;
    @Captor private ArgumentCaptor<Asset>                assetCaptor;
    @Captor private ArgumentCaptor<AssetCustodyTransfer> transferCaptor;

    private UUID orgId;
    private UUID authUserId;
    private UUID assetId;
    private UUID employeeId;
    private Asset availableAsset;
    private Asset assignedAsset;
    private AssetAssignment activeAssignment;
    private AssetAssignmentDto assignmentDto;
    private AssetAssignmentRequest assignRequest;

    @BeforeEach
    void setUp() {
        orgId      = UUID.fromString("00000000-0000-0000-0000-000000000001");
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assetId    = UUID.fromString("00000000-0000-0000-0000-000000000010");
        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000020");

        LegalEntity legalEntity = LegalEntity.builder()
                .id(UUID.randomUUID())
                .baseCurrency("NPR")
                .organizationId(orgId)
                .build();

        availableAsset = Asset.builder()
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
                .netBookValue(new BigDecimal("250000.00"))
                .accumulatedDepreciation(BigDecimal.ZERO)
                .assetStatus(AssetStatus.AVAILABLE)
                .build();

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
                .netBookValue(new BigDecimal("250000.00"))
                .accumulatedDepreciation(BigDecimal.ZERO)
                .assetStatus(AssetStatus.ASSIGNED)
                .build();

        activeAssignment = AssetAssignment.builder()
                .asset(availableAsset)
                .employeeId(employeeId)
                .assignedBy(authUserId)
                .organizationId(orgId)
                .assignmentDate(LocalDate.now())
                .purpose(AssignmentPurpose.PRIMARY_WORK)
                .conditionAtAssignment(ConditionGrade.GOOD)
                .assignmentStatus(AssignmentStatus.ACTIVE)
                .build();

        assignmentDto = new AssetAssignmentDto();
        assignmentDto.setId(UUID.randomUUID());
        assignmentDto.setAssignmentStatus(AssignmentStatus.ACTIVE);

        assignRequest = new AssetAssignmentRequest();
        assignRequest.setEmployeeId(employeeId);
        assignRequest.setAssignmentDate(LocalDate.now());
        assignRequest.setPurpose(AssignmentPurpose.PRIMARY_WORK);
        assignRequest.setConditionAtAssignment(ConditionGrade.GOOD);
    }

    // =========================================================================
    // assign
    // =========================================================================

    @Nested
    @DisplayName("assign")
    class Assign {

        @Test
        @DisplayName("should assign AVAILABLE asset")
        void shouldAssign() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(availableAsset));
            when(cmEmployeeRepository.findByIdAndOrganizationId(employeeId, orgId))
                    .thenReturn(Optional.of(CmEmployee.builder().id(employeeId).organizationId(orgId).build()));
            when(assignmentRepository.save(any())).thenReturn(activeAssignment);
            when(assetRepository.save(any())).thenReturn(assignedAsset);
            when(custodyRepository.save(any())).thenReturn(null);
            when(assetMapper.toAssignmentDto(activeAssignment)).thenReturn(assignmentDto);

            AssetAssignmentDto result = service.assign(assetId, assignRequest);

            verify(assignmentRepository).save(assignmentCaptor.capture());
            AssetAssignment saved = assignmentCaptor.getValue();
            assertThat(saved.getAssignmentStatus()).isEqualTo(AssignmentStatus.ACTIVE);
            assertThat(saved.getEmployeeId()).isEqualTo(employeeId);

            verify(assetRepository).save(assetCaptor.capture());
            assertThat(assetCaptor.getValue().getAssetStatus()).isEqualTo(AssetStatus.ASSIGNED);

            verify(custodyRepository).save(transferCaptor.capture());
            assertThat(transferCaptor.getValue().getTransferType()).isEqualTo(CustodyTransferType.ASSIGNMENT);
            assertThat(transferCaptor.getValue().getFromCustodianType()).isEqualTo(CustodianType.IT_DEPARTMENT);
            assertThat(transferCaptor.getValue().getToCustodianType()).isEqualTo(CustodianType.EMPLOYEE);

            assertThat(result).isEqualTo(assignmentDto);
        }

        @Test
        @DisplayName("should assign RETURNED asset (re-assignment is allowed)")
        void shouldAssignReturnedAsset() {
            Asset returnedAsset = Asset.builder()
                    .legalEntity(availableAsset.getLegalEntity())
                    .organizationId(orgId)
                    .assetStatus(AssetStatus.RETURNED)
                    .serialNumber("SN-001")
                    .purchaseDate(LocalDate.now())
                    .purchaseCost(BigDecimal.ONE)
                    .currencyCode("NPR")
                    .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                    .usefulLifeYears(3)
                    .netBookValue(BigDecimal.ONE)
                    .accumulatedDepreciation(BigDecimal.ZERO)
                    .assetType("Test")
                    .category(AssetCategory.LAPTOP)
                    .build();

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(returnedAsset));
            when(cmEmployeeRepository.findByIdAndOrganizationId(employeeId, orgId))
                    .thenReturn(Optional.of(CmEmployee.builder().id(employeeId).organizationId(orgId).build()));
            when(assignmentRepository.save(any())).thenReturn(activeAssignment);
            when(assetRepository.save(any())).thenReturn(assignedAsset);
            when(custodyRepository.save(any())).thenReturn(null);
            when(assetMapper.toAssignmentDto(any())).thenReturn(assignmentDto);

            assertThat(service.assign(assetId, assignRequest)).isNotNull();
        }

        @Test
        @DisplayName("should throw InvalidAssetStateException when asset is already ASSIGNED")
        void shouldThrowWhenAlreadyAssigned() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(assignedAsset));

            assertThatThrownBy(() -> service.assign(assetId, assignRequest))
                    .isInstanceOf(InvalidAssetStateException.class)
                    .hasMessageContaining("assign")
                    .hasMessageContaining("ASSIGNED");

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw AssetNotFoundException when asset not in org")
        void shouldThrowWhenAssetNotFound() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assign(assetId, assignRequest))
                    .isInstanceOf(AssetNotFoundException.class);
        }

    }

    // =========================================================================
    // listByEmployee
    // =========================================================================

    @Nested
    @DisplayName("listByEmployee")
    class ListByEmployee {

        @Test
        @DisplayName("should return only ACTIVE assignments for employee")
        void shouldReturnActiveAssignments() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assignmentRepository.findUnresolvedByEmployeeId(employeeId, orgId))
                    .thenReturn(List.of(activeAssignment));
            when(assetMapper.toAssignmentDto(activeAssignment)).thenReturn(assignmentDto);

            List<AssetAssignmentDto> result = service.listByEmployee(employeeId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(assignmentDto);
        }

        @Test
        @DisplayName("should return empty list when employee has no active assignments")
        void shouldReturnEmptyWhenNoActive() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assignmentRepository.findUnresolvedByEmployeeId(employeeId, orgId))
                    .thenReturn(List.of());

            List<AssetAssignmentDto> result = service.listByEmployee(employeeId);

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // checkOffboarding
    // =========================================================================

    @Nested
    @DisplayName("checkOffboarding")
    class CheckOffboarding {

        @Test
        @DisplayName("should return cleared=false when employee has unreturned assets")
        void shouldReturnBlockedWhenAssetsOutstanding() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assignmentRepository.countActiveByEmployeeId(employeeId, orgId)).thenReturn(2L);
            when(assignmentRepository.findUnresolvedByEmployeeId(employeeId, orgId))
                    .thenReturn(List.of(activeAssignment, activeAssignment));
            when(assetMapper.toAssignmentDto(any())).thenReturn(assignmentDto);

            OffboardingAssetCheckDto result = service.checkOffboarding(employeeId);

            assertThat(result.isCleared()).isFalse();
            assertThat(result.getUnreturnedCount()).isEqualTo(2L);
            assertThat(result.getUnreturnedAssets()).hasSize(2);
            assertThat(result.getEmployeeId()).isEqualTo(employeeId);
        }

        @Test
        @DisplayName("should return cleared=true when all assets have been returned")
        void shouldReturnClearedWhenNoAssets() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assignmentRepository.countActiveByEmployeeId(employeeId, orgId)).thenReturn(0L);

            OffboardingAssetCheckDto result = service.checkOffboarding(employeeId);

            assertThat(result.isCleared()).isTrue();
            assertThat(result.getUnreturnedCount()).isZero();
            assertThat(result.getUnreturnedAssets()).isEmpty();
        }
    }

}
