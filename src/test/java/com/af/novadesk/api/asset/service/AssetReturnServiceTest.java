package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.constants.*;
import com.af.novadesk.api.asset.dto.AssetDto;
import com.af.novadesk.api.asset.dto.AssetReturnRequest;
import com.af.novadesk.api.asset.dto.CustodyTransferDto;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.AssetAssignment;
import com.af.novadesk.api.asset.entity.AssetCustodyTransfer;
import com.af.novadesk.api.asset.entity.AssetReturn;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.exception.InvalidAssetStateException;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.AssetAssignmentRepository;
import com.af.novadesk.api.asset.repository.AssetCustodyTransferRepository;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.repository.AssetReturnRepository;
import com.af.novadesk.api.asset.service.impl.AssetOutboxServiceImpl;
import com.af.novadesk.api.asset.service.impl.AssetReturnServiceImpl;
import com.af.novadesk.api.finance.entity.LegalEntity;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetReturnService")
class AssetReturnServiceTest {

    @Mock private AssetRepository                assetRepository;
    @Mock private AssetAssignmentRepository      assignmentRepository;
    @Mock private AssetReturnRepository          returnRepository;
    @Mock private AssetCustodyTransferRepository custodyRepository;
    @Mock private AssetMapper                    assetMapper;
    @Mock private AssetOutboxServiceImpl         outboxService;
    @Mock private FinanceSecurityContext         securityContext;

    @InjectMocks
    private AssetReturnServiceImpl service;

    @Captor private ArgumentCaptor<AssetReturn>          returnCaptor;
    @Captor private ArgumentCaptor<AssetAssignment>      assignmentCaptor;
    @Captor private ArgumentCaptor<Asset>                assetCaptor;
    @Captor private ArgumentCaptor<AssetCustodyTransfer> transferCaptor;

    private UUID orgId;
    private UUID authUserId;
    private UUID assetId;
    private UUID employeeId;
    private UUID receiverId;
    private Asset assignedAsset;
    private AssetAssignment activeAssignment;
    private AssetDto returnedAssetDto;
    private AssetReturnRequest returnRequest;

    @BeforeEach
    void setUp() {
        orgId      = UUID.fromString("00000000-0000-0000-0000-000000000001");
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assetId    = UUID.fromString("00000000-0000-0000-0000-000000000010");
        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        receiverId = UUID.fromString("00000000-0000-0000-0000-000000000030");

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
                .netBookValue(new BigDecimal("250000.00"))
                .accumulatedDepreciation(BigDecimal.ZERO)
                .assetStatus(AssetStatus.ASSIGNED)
                .build();

        activeAssignment = AssetAssignment.builder()
                .asset(assignedAsset)
                .employeeId(employeeId)
                .assignedBy(authUserId)
                .organizationId(orgId)
                .assignmentDate(LocalDate.now().minusDays(5))
                .purpose(AssignmentPurpose.PRIMARY_WORK)
                .conditionAtAssignment(ConditionGrade.GOOD)
                .requiresAcknowledgment(false)
                .acknowledgmentStatus(AcknowledgmentStatus.WAIVED)
                .assignmentStatus(AssignmentStatus.ACTIVE)
                .build();

        returnedAssetDto = new AssetDto();
        returnedAssetDto.setAssetStatus(AssetStatus.RETURNED);

        returnRequest = new AssetReturnRequest();
        returnRequest.setReturnDate(LocalDate.now());
        returnRequest.setReceivedByEmployeeId(receiverId);
        returnRequest.setConditionAtReturn(ConditionGrade.GOOD);
        returnRequest.setRepairRequired(false);
        returnRequest.setNotes("Integration test return");
    }

    // =========================================================================
    // recordReturn
    // =========================================================================

    @Nested
    @DisplayName("recordReturn")
    class RecordReturn {

        @Test
        @DisplayName("should record return, close assignment and update asset status to RETURNED")
        void shouldRecordReturn() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(assignedAsset));
            when(assignmentRepository.findActiveByAssetId(assetId))
                    .thenReturn(Optional.of(activeAssignment));
            when(returnRepository.save(any())).thenReturn(null);
            when(assignmentRepository.save(any())).thenReturn(activeAssignment);
            when(assetRepository.save(any())).thenReturn(assignedAsset);
            when(custodyRepository.save(any())).thenReturn(null);
            when(assetMapper.toDto(any())).thenReturn(returnedAssetDto);

            AssetDto result = service.recordReturn(assetId, returnRequest);

            // return record created with correct fields
            verify(returnRepository).save(returnCaptor.capture());
            AssetReturn savedReturn = returnCaptor.getValue();
            assertThat(savedReturn.getReturnDate()).isEqualTo(LocalDate.now());
            assertThat(savedReturn.getReturnedByEmployeeId()).isEqualTo(employeeId);
            assertThat(savedReturn.getReceivedByEmployeeId()).isEqualTo(receiverId);
            assertThat(savedReturn.getConditionAtReturn()).isEqualTo(ConditionGrade.GOOD);
            assertThat(savedReturn.isRepairRequired()).isFalse();

            // assignment closed
            verify(assignmentRepository).save(assignmentCaptor.capture());
            assertThat(assignmentCaptor.getValue().getAssignmentStatus())
                    .isEqualTo(AssignmentStatus.RETURNED);

            // asset status updated
            verify(assetRepository).save(assetCaptor.capture());
            assertThat(assetCaptor.getValue().getAssetStatus()).isEqualTo(AssetStatus.RETURNED);

            // custody transfer recorded
            verify(custodyRepository).save(transferCaptor.capture());
            assertThat(transferCaptor.getValue().getTransferType()).isEqualTo(CustodyTransferType.RETURN);
            assertThat(transferCaptor.getValue().getFromCustodianType()).isEqualTo(CustodianType.EMPLOYEE);
            assertThat(transferCaptor.getValue().getToCustodianType()).isEqualTo(CustodianType.IT_DEPARTMENT);
            assertThat(transferCaptor.getValue().getFromCustodianId()).isEqualTo(employeeId);

            assertThat(result).isEqualTo(returnedAssetDto);
        }

        @Test
        @DisplayName("should set repairRequired from request")
        void shouldSetRepairRequired() {
            returnRequest.setRepairRequired(true);
            returnRequest.setConditionAtReturn(ConditionGrade.DAMAGED);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(assignedAsset));
            when(assignmentRepository.findActiveByAssetId(assetId))
                    .thenReturn(Optional.of(activeAssignment));
            when(returnRepository.save(any())).thenReturn(null);
            when(assignmentRepository.save(any())).thenReturn(activeAssignment);
            when(assetRepository.save(any())).thenReturn(assignedAsset);
            when(custodyRepository.save(any())).thenReturn(null);
            when(assetMapper.toDto(any())).thenReturn(returnedAssetDto);

            service.recordReturn(assetId, returnRequest);

            verify(returnRepository).save(returnCaptor.capture());
            assertThat(returnCaptor.getValue().isRepairRequired()).isTrue();
            assertThat(returnCaptor.getValue().getConditionAtReturn()).isEqualTo(ConditionGrade.DAMAGED);
        }

        @Test
        @DisplayName("should throw InvalidAssetStateException when asset is not ASSIGNED")
        void shouldThrowWhenNotAssigned() {
            Asset availableAsset = Asset.builder()
                    .legalEntity(assignedAsset.getLegalEntity())
                    .organizationId(orgId)
                    .assetStatus(AssetStatus.AVAILABLE)
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
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(availableAsset));

            assertThatThrownBy(() -> service.recordReturn(assetId, returnRequest))
                    .isInstanceOf(InvalidAssetStateException.class)
                    .hasMessageContaining("return")
                    .hasMessageContaining("AVAILABLE");

            verify(returnRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw AssetNotFoundException when asset not in org")
        void shouldThrowWhenAssetNotFound() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recordReturn(assetId, returnRequest))
                    .isInstanceOf(AssetNotFoundException.class);
        }

        @Test
        @DisplayName("should throw BadRequestException when no active assignment found")
        void shouldThrowWhenNoActiveAssignment() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(assignedAsset));
            when(assignmentRepository.findActiveByAssetId(assetId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recordReturn(assetId, returnRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("No active assignment");
        }
    }

    // =========================================================================
    // getCustodyHistory
    // =========================================================================

    @Nested
    @DisplayName("getCustodyHistory")
    class GetCustodyHistory {

        @Test
        @DisplayName("should return custody transfers ordered by creation time")
        void shouldReturnCustodyHistory() {
            AssetCustodyTransfer assignment = AssetCustodyTransfer.builder()
                    .asset(assignedAsset)
                    .organizationId(orgId)
                    .fromCustodianType(CustodianType.IT_DEPARTMENT)
                    .toCustodianType(CustodianType.EMPLOYEE)
                    .toCustodianId(employeeId)
                    .transferType(CustodyTransferType.ASSIGNMENT)
                    .transferDate(LocalDate.now().minusDays(5))
                    .approvedBy(authUserId)
                    .build();

            AssetCustodyTransfer returnTransfer = AssetCustodyTransfer.builder()
                    .asset(assignedAsset)
                    .organizationId(orgId)
                    .fromCustodianType(CustodianType.EMPLOYEE)
                    .fromCustodianId(employeeId)
                    .toCustodianType(CustodianType.IT_DEPARTMENT)
                    .transferType(CustodyTransferType.RETURN)
                    .transferDate(LocalDate.now())
                    .approvedBy(authUserId)
                    .build();

            CustodyTransferDto dto1 = new CustodyTransferDto();
            dto1.setTransferType(CustodyTransferType.ASSIGNMENT);
            CustodyTransferDto dto2 = new CustodyTransferDto();
            dto2.setTransferType(CustodyTransferType.RETURN);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(assignedAsset));
            when(custodyRepository.findAllByAssetIdOrderByCreatedAtAsc(assetId))
                    .thenReturn(List.of(assignment, returnTransfer));
            when(assetMapper.toCustodyTransferDtoList(any())).thenReturn(List.of(dto1, dto2));

            List<CustodyTransferDto> result = service.getCustodyHistory(assetId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTransferType()).isEqualTo(CustodyTransferType.ASSIGNMENT);
            assertThat(result.get(1).getTransferType()).isEqualTo(CustodyTransferType.RETURN);
        }

        @Test
        @DisplayName("should throw AssetNotFoundException when asset not found")
        void shouldThrowWhenNotFound() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCustodyHistory(assetId))
                    .isInstanceOf(AssetNotFoundException.class);
        }
    }
}
