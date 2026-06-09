package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.DepreciationMethod;
import com.af.novadesk.api.asset.dto.DepreciationScheduleDto;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.DepreciationSchedule;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.mapper.AssetMapper;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.repository.DepreciationScheduleRepository;
import com.af.novadesk.api.asset.service.impl.AssetOutboxServiceImpl;
import com.af.novadesk.api.asset.service.impl.DepreciationServiceImpl;
import com.af.novadesk.api.common.entity.LegalEntity;
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
@DisplayName("DepreciationService")
class DepreciationServiceTest {

    @Mock private AssetRepository                assetRepository;
    @Mock private DepreciationScheduleRepository scheduleRepository;
    @Mock private AssetMapper                    assetMapper;
    @Mock private FinanceSecurityContext         securityContext;
    @Mock private AssetOutboxServiceImpl         outboxService;

    @InjectMocks
    private DepreciationServiceImpl service;

    @Captor
    private ArgumentCaptor<List<DepreciationSchedule>> schedulesCaptor;

    private UUID orgId;
    private UUID assetId;
    private Asset straightLineAsset;
    private Asset decliningBalanceAsset;
    private Asset midYearAsset;

    @BeforeEach
    void setUp() {
        orgId   = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assetId = UUID.fromString("00000000-0000-0000-0000-000000000010");

        LegalEntity legalEntity = LegalEntity.builder()
                .id(UUID.randomUUID()).baseCurrency("NPR").organizationId(orgId).build();

        // Asset purchased Jan 1 — full year, simplifies pro-rate calculation
        straightLineAsset = Asset.builder()
                .legalEntity(legalEntity)
                .organizationId(orgId)
                .category(AssetCategory.LAPTOP)
                .assetType("Test Laptop")
                .serialNumber("SN-SL-001")
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .purchaseCost(new BigDecimal("120000.00"))
                .currencyCode("NPR")
                .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                .usefulLifeYears(3)
                .netBookValue(new BigDecimal("120000.00"))
                .accumulatedDepreciation(BigDecimal.ZERO)
                .assetStatus(AssetStatus.AVAILABLE)
                .build();

        // Declining balance asset
        decliningBalanceAsset = Asset.builder()
                .legalEntity(legalEntity)
                .organizationId(orgId)
                .category(AssetCategory.LAPTOP)
                .assetType("Test Server")
                .serialNumber("SN-DB-001")
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .purchaseCost(new BigDecimal("100000.00"))
                .currencyCode("NPR")
                .depreciationMethod(DepreciationMethod.DECLINING_BALANCE)
                .usefulLifeYears(5)
                .netBookValue(new BigDecimal("100000.00"))
                .accumulatedDepreciation(BigDecimal.ZERO)
                .assetStatus(AssetStatus.AVAILABLE)
                .build();

        // Mid-year purchase (July) — tests pro-rata first year
        midYearAsset = Asset.builder()
                .legalEntity(legalEntity)
                .organizationId(orgId)
                .category(AssetCategory.MONITOR)
                .assetType("Test Monitor")
                .serialNumber("SN-MY-001")
                .purchaseDate(LocalDate.of(2026, 7, 1))
                .purchaseCost(new BigDecimal("60000.00"))
                .currencyCode("NPR")
                .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                .usefulLifeYears(3)
                .netBookValue(new BigDecimal("60000.00"))
                .accumulatedDepreciation(BigDecimal.ZERO)
                .assetStatus(AssetStatus.AVAILABLE)
                .build();
    }

    // =========================================================================
    // generateSchedule
    // =========================================================================

    @Nested
    @DisplayName("generateSchedule")
    class GenerateSchedule {

        @Test
        @DisplayName("straight-line: should generate 3 rows with equal annual depreciation")
        void shouldGenerateStraightLineSchedule() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(straightLineAsset));
            when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(assetMapper.toDepreciationDtoList(any())).thenReturn(List.of());

            service.generateSchedule(assetId);

            verify(scheduleRepository).saveAll(schedulesCaptor.capture());
            List<DepreciationSchedule> schedules = schedulesCaptor.getValue();

            assertThat(schedules).hasSize(3);

            // Annual depreciation = 120000 / 3 = 40000 per year
            BigDecimal expectedAnnual = new BigDecimal("40000.0000");
            for (DepreciationSchedule s : schedules) {
                assertThat(s.getAnnualDepreciation()).isEqualByComparingTo(expectedAnnual);
                assertThat(s.isPosted()).isFalse();
            }

            // Fiscal years should be sequential
            assertThat(schedules.get(0).getFiscalYear()).isEqualTo(2026);
            assertThat(schedules.get(1).getFiscalYear()).isEqualTo(2027);
            assertThat(schedules.get(2).getFiscalYear()).isEqualTo(2028);

            // Net book value at end should be ~0
            assertThat(schedules.get(2).getNetBookValue().doubleValue()).isLessThanOrEqualTo(0.01);
        }

        @Test
        @DisplayName("straight-line: accumulated depreciation should grow monotonically")
        void shouldAccumulateMonotonically() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(straightLineAsset));
            when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(assetMapper.toDepreciationDtoList(any())).thenReturn(List.of());

            service.generateSchedule(assetId);

            verify(scheduleRepository).saveAll(schedulesCaptor.capture());
            List<DepreciationSchedule> schedules = schedulesCaptor.getValue();

            BigDecimal prev = BigDecimal.ZERO;
            for (DepreciationSchedule s : schedules) {
                assertThat(s.getAccumulatedDepreciation()).isGreaterThan(prev);
                prev = s.getAccumulatedDepreciation();
            }
        }

        @Test
        @DisplayName("straight-line: net book value should decrease each year")
        void shouldDecreaseNetBookValue() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(straightLineAsset));
            when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(assetMapper.toDepreciationDtoList(any())).thenReturn(List.of());

            service.generateSchedule(assetId);

            verify(scheduleRepository).saveAll(schedulesCaptor.capture());
            List<DepreciationSchedule> schedules = schedulesCaptor.getValue();

            BigDecimal prev = straightLineAsset.getPurchaseCost();
            for (DepreciationSchedule s : schedules) {
                assertThat(s.getNetBookValue()).isLessThan(prev);
                prev = s.getNetBookValue();
            }
        }

        @Test
        @DisplayName("declining-balance: first year depreciation should exceed straight-line")
        void shouldDepreciateMoreInFirstYear() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(decliningBalanceAsset));
            when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(assetMapper.toDepreciationDtoList(any())).thenReturn(List.of());

            service.generateSchedule(assetId);

            verify(scheduleRepository).saveAll(schedulesCaptor.capture());
            List<DepreciationSchedule> schedules = schedulesCaptor.getValue();

            // Straight-line would be 100000/5=20000. Declining 2x rate = 40000 year 1
            BigDecimal straightLineAnnual = new BigDecimal("20000.00");
            assertThat(schedules.get(0).getAnnualDepreciation())
                    .isGreaterThan(straightLineAnnual);
        }

        @Test
        @DisplayName("declining-balance: annual depreciation should decrease over time")
        void shouldDecreaseAnnualInDecliningBalance() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(decliningBalanceAsset));
            when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(assetMapper.toDepreciationDtoList(any())).thenReturn(List.of());

            service.generateSchedule(assetId);

            verify(scheduleRepository).saveAll(schedulesCaptor.capture());
            List<DepreciationSchedule> schedules = schedulesCaptor.getValue();

            // Declining balance: each year depreciates less than the previous
            for (int i = 1; i < schedules.size(); i++) {
                assertThat(schedules.get(i).getAnnualDepreciation().doubleValue())
                        .isLessThanOrEqualTo(schedules.get(i - 1).getAnnualDepreciation().doubleValue());
            }
        }

        @Test
        @DisplayName("mid-year purchase: first year should be pro-rated")
        void shouldProRateFirstYear() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.of(midYearAsset));
            when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(assetMapper.toDepreciationDtoList(any())).thenReturn(List.of());

            service.generateSchedule(assetId);

            verify(scheduleRepository).saveAll(schedulesCaptor.capture());
            List<DepreciationSchedule> schedules = schedulesCaptor.getValue();

            // Full year = 60000/3 = 20000. July purchase = 6 months remaining (13-7=6)
            // Pro-rated = 20000 * 6/12 = 10000
            BigDecimal fullYear = new BigDecimal("20000.0000");
            assertThat(schedules.get(0).getAnnualDepreciation())
                    .isLessThan(fullYear);
        }

        @Test
        @DisplayName("should throw AssetNotFoundException when asset not found")
        void shouldThrowWhenNotFound() {
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateSchedule(assetId))
                    .isInstanceOf(AssetNotFoundException.class);

            verify(scheduleRepository, never()).saveAll(any());
        }
    }

    // =========================================================================
    // getSchedule
    // =========================================================================

    @Nested
    @DisplayName("getSchedule")
    class GetSchedule {

        @Test
        @DisplayName("should return schedule from repository ordered by fiscal year")
        void shouldReturnSchedule() {
            DepreciationScheduleDto dto = new DepreciationScheduleDto();
            dto.setFiscalYear(2026);
            dto.setPosted(false);

            when(scheduleRepository.findAllByAssetIdOrderByFiscalYearAsc(assetId))
                    .thenReturn(List.of());
            when(assetMapper.toDepreciationDtoList(List.of())).thenReturn(List.of(dto));

            List<DepreciationScheduleDto> result = service.getSchedule(assetId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFiscalYear()).isEqualTo(2026);
            verify(scheduleRepository).findAllByAssetIdOrderByFiscalYearAsc(assetId);
        }

        @Test
        @DisplayName("should return empty list when no schedule exists")
        void shouldReturnEmptyWhenNoSchedule() {
            when(scheduleRepository.findAllByAssetIdOrderByFiscalYearAsc(assetId))
                    .thenReturn(List.of());
            when(assetMapper.toDepreciationDtoList(List.of())).thenReturn(List.of());

            List<DepreciationScheduleDto> result = service.getSchedule(assetId);

            assertThat(result).isEmpty();
        }
    }
}
