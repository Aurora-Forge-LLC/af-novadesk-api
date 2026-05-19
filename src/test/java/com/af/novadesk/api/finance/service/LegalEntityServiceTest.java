package com.af.novadesk.api.finance.service;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.constants.Status;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryResponse;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.service.impl.LegalEntityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class LegalEntityServiceTest {
    @Mock private LegalEntityRepository legalEntityRepository;
    private LegalEntityService service;
    @BeforeEach
    void setUp() { service = new LegalEntityServiceImpl(legalEntityRepository); }
    @Nested @DisplayName("list()")
    class ListEntities {
        @Test @DisplayName("Returns all entities mapped to DTOs")
        void list_returnsMapped() {
            UUID id = UUID.randomUUID();
            LegalEntity e = buildEntity(id, "INDIA", CountryCode.IN, "INR", ApprovalStatus.APPROVED, Status.ACTIVE);
            when(legalEntityRepository.findAll()).thenReturn(List.of(e));
            List<LegalEntitySummaryResponse> r = service.list();
            assertThat(r).hasSize(1);
            assertThat(r.get(0).id()).isEqualTo(id);
            assertThat(r.get(0).entityCode()).isEqualTo("INDIA");
            assertThat(r.get(0).country()).isEqualTo(CountryCode.IN);
            assertThat(r.get(0).baseCurrency()).isEqualTo("INR");
            assertThat(r.get(0).approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        }
        @Test @DisplayName("Empty list when none exist")
        void list_empty() {
            when(legalEntityRepository.findAll()).thenReturn(List.of());
            assertThat(service.list()).isEmpty();
        }
        @Test @DisplayName("PENDING entity is included without filter")
        void list_pendingIncluded() {
            LegalEntity p = buildEntity(UUID.randomUUID(), "US", CountryCode.US, "USD", ApprovalStatus.PENDING, Status.ACTIVE);
            when(legalEntityRepository.findAll()).thenReturn(List.of(p));
            assertThat(service.list().get(0).approvalStatus()).isEqualTo(ApprovalStatus.PENDING);
        }
    }
    @Nested @DisplayName("getById()")
    class GetById {
        @Test @DisplayName("Existing entity returns DTO")
        void getById_existing() {
            UUID id = UUID.randomUUID();
            LegalEntity e = buildEntity(id, "US", CountryCode.US, "USD", ApprovalStatus.APPROVED, Status.ACTIVE);
            when(legalEntityRepository.findById(id)).thenReturn(Optional.of(e));
            assertThat(service.getById(id).entityCode()).isEqualTo("US");
        }
        @Test @DisplayName("Unknown ID throws NotFoundException with ID in message")
        void getById_notFound() {
            UUID missing = UUID.randomUUID();
            when(legalEntityRepository.findById(missing)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(missing))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(missing.toString());
        }
        @Test @DisplayName("INACTIVE entity is still retrievable")
        void getById_inactive_returned() {
            UUID id = UUID.randomUUID();
            LegalEntity e = buildEntity(id, "INDIA", CountryCode.IN, "INR", ApprovalStatus.APPROVED, Status.INACTIVE);
            when(legalEntityRepository.findById(id)).thenReturn(Optional.of(e));
            assertThat(service.getById(id).status()).isEqualTo(Status.INACTIVE);
        }
    }
    private LegalEntity buildEntity(UUID id, String code, CountryCode country, String currency,
                                    ApprovalStatus approval, Status status) {
        LegalEntity e = LegalEntity.builder()
                .id(id).entityCode(code).entityName(code + " Entity")
                .country(country).baseCurrency(currency)
                .incorporationDate(LocalDate.of(2020, 1, 1))
                .approvalStatus(approval).build();
        e.setStatus(status);
        return e;
    }
}
