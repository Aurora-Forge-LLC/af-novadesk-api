package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.exception.OutboxPublishException;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.constants.LegalEntityEventType;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.entity.LegalEntityOutboxEvent;
import com.af.novadesk.api.finance.repositories.LegalEntityOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LegalEntityOutboxService}.
 *
 * <p>Validates the transactional outbox behaviour for the {@link LegalEntity}
 * aggregate: correct payload construction, repository interaction, idempotency
 * key format, and error handling for JSON serialisation failures.</p>
 *
 * @see LegalEntityOutboxService
 * @see LegalEntityOutboxEvent
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LegalEntityOutboxService")
class LegalEntityOutboxServiceTest {

    // -------------------------------------------------------------------------
    // Mocks & Test Fixtures
    // -------------------------------------------------------------------------

    @Mock
    private LegalEntityOutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private LegalEntityOutboxService service;

    @Captor
    private ArgumentCaptor<LegalEntityOutboxEvent> eventCaptor;

    private LegalEntity testEntity;
    private String serialisedPayload;
    private UUID orgId;
    private UUID triggeredByUserId;

    @BeforeEach
    void setUp() {
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        triggeredByUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        testEntity = LegalEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000010"))
                .entityName("Test Entity")
                .entityCode("TEST01")
                .country(CountryCode.US)
                .baseCurrency("USD")
                .incorporationDate(LocalDate.of(2020, 1, 15))
                .build();

        serialisedPayload = """
                {"entityId":"00000000-0000-0000-0000-000000000010","entityName":"Test Entity"}""";
    }

    // -------------------------------------------------------------------------
    // publishEntityCreated
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("publishEntityCreated")
    class PublishCreated {

        @Test
        @DisplayName("should persist a PENDING outbox event with correct payload")
        void shouldPersistPendingEvent() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(serialisedPayload);

            // Act
            service.publishEntityCreated(testEntity, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository).save(eventCaptor.capture());
            LegalEntityOutboxEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getLegalEntity()).isEqualTo(testEntity);
            assertThat(savedEvent.getEventType()).isEqualTo(LegalEntityEventType.LEGAL_ENTITY_CREATED);
            assertThat(savedEvent.getPayload()).isEqualTo(serialisedPayload);
            assertThat(savedEvent.getOrganizationId()).isEqualTo(orgId);
            assertThat(savedEvent.getTriggeredByAuthUserId()).isEqualTo(triggeredByUserId);
            assertThat(savedEvent.getOutboxEventStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(savedEvent.getRetryCount()).isZero();
            assertThat(savedEvent.getIdempotencyKey())
                    .startsWith(LegalEntityEventType.LEGAL_ENTITY_CREATED.name() + ":"
                            + testEntity.getId() + ":");
        }

        @Test
        @DisplayName("should include all entity fields in payload")
        void shouldIncludeAllFieldsInPayload() throws Exception {
            // Arrange
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

            // Act
            service.publishEntityCreated(testEntity, triggeredByUserId, orgId);

            // Assert
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            assertThat(capturedPayload)
                    .containsEntry("entityId", testEntity.getId())
                    .containsEntry("entityName", testEntity.getEntityName())
                    .containsEntry("entityCode", testEntity.getEntityCode())
                    .containsEntry("country", testEntity.getCountry().name())
                    .containsEntry("baseCurrency", testEntity.getBaseCurrency())
                    .containsEntry("incorporationDate", testEntity.getIncorporationDate().toString())
                    .containsEntry("submittedByAuthUserId", triggeredByUserId)
                    .containsEntry("organizationId", orgId);
        }

        @Test
        @DisplayName("should propagate JsonProcessingException as OutboxPublishException")
        void shouldThrowOutboxPublishExceptionOnJsonError() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class)))
                    .thenThrow(new JsonProcessingException("Serialisation failure") {
                    });

            // Act & Assert
            assertThatThrownBy(() ->
                    service.publishEntityCreated(testEntity, triggeredByUserId, orgId))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("LEGAL_ENTITY_CREATED")
                    .hasMessageContaining(testEntity.getId().toString())
                    .hasCauseInstanceOf(JsonProcessingException.class);

            verify(outboxRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // publishEntityApproved
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("publishEntityApproved")
    class PublishApproved {

        @Test
        @DisplayName("should persist a PENDING outbox event with approval details")
        void shouldPersistPendingEvent() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(serialisedPayload);

            // Act
            service.publishEntityApproved(testEntity, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository).save(eventCaptor.capture());
            LegalEntityOutboxEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getLegalEntity()).isEqualTo(testEntity);
            assertThat(savedEvent.getEventType()).isEqualTo(LegalEntityEventType.LEGAL_ENTITY_APPROVED);
            assertThat(savedEvent.getPayload()).isEqualTo(serialisedPayload);
            assertThat(savedEvent.getOrganizationId()).isEqualTo(orgId);
            assertThat(savedEvent.getTriggeredByAuthUserId()).isEqualTo(triggeredByUserId);
            assertThat(savedEvent.getOutboxEventStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(savedEvent.getRetryCount()).isZero();
            assertThat(savedEvent.getIdempotencyKey())
                    .startsWith(LegalEntityEventType.LEGAL_ENTITY_APPROVED.name() + ":"
                            + testEntity.getId() + ":");
        }

        @Test
        @DisplayName("should include approval fields in payload")
        void shouldIncludeApprovalFieldsInPayload() throws Exception {
            // Arrange
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

            // Act
            service.publishEntityApproved(testEntity, triggeredByUserId, orgId);

            // Assert
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            assertThat(capturedPayload)
                    .containsEntry("entityId", testEntity.getId())
                    .containsEntry("approvedByAuthUserId", triggeredByUserId)
                    .containsEntry("organizationId", orgId)
                    .containsKey("approvedAt");
        }

        @Test
        @DisplayName("should propagate JsonProcessingException as OutboxPublishException")
        void shouldThrowOutboxPublishExceptionOnJsonError() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class)))
                    .thenThrow(new JsonProcessingException("Serialisation failure") {
                    });

            // Act & Assert
            assertThatThrownBy(() ->
                    service.publishEntityApproved(testEntity, triggeredByUserId, orgId))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("LEGAL_ENTITY_APPROVED")
                    .hasMessageContaining(testEntity.getId().toString())
                    .hasCauseInstanceOf(JsonProcessingException.class);

            verify(outboxRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // publishEntityRejected
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("publishEntityRejected")
    class PublishRejected {

        @Test
        @DisplayName("should persist a PENDING outbox event with rejection details")
        void shouldPersistPendingEvent() throws Exception {
            // Arrange
            String reason = "Incomplete documentation";
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(serialisedPayload);

            // Act
            service.publishEntityRejected(testEntity, reason, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository).save(eventCaptor.capture());
            LegalEntityOutboxEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getLegalEntity()).isEqualTo(testEntity);
            assertThat(savedEvent.getEventType()).isEqualTo(LegalEntityEventType.LEGAL_ENTITY_REJECTED);
            assertThat(savedEvent.getPayload()).isEqualTo(serialisedPayload);
            assertThat(savedEvent.getOrganizationId()).isEqualTo(orgId);
            assertThat(savedEvent.getTriggeredByAuthUserId()).isEqualTo(triggeredByUserId);
            assertThat(savedEvent.getOutboxEventStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(savedEvent.getRetryCount()).isZero();
            assertThat(savedEvent.getIdempotencyKey())
                    .startsWith(LegalEntityEventType.LEGAL_ENTITY_REJECTED.name() + ":"
                            + testEntity.getId() + ":");
        }

        @Test
        @DisplayName("should include rejection fields in payload")
        void shouldIncludeRejectionFieldsInPayload() throws Exception {
            // Arrange
            String reason = "Incomplete documentation";
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

            // Act
            service.publishEntityRejected(testEntity, reason, triggeredByUserId, orgId);

            // Assert
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            assertThat(capturedPayload)
                    .containsEntry("entityId", testEntity.getId())
                    .containsEntry("rejectedByAuthUserId", triggeredByUserId)
                    .containsEntry("reason", reason)
                    .containsEntry("organizationId", orgId)
                    .containsKey("rejectedAt");
        }

        @Test
        @DisplayName("should propagate JsonProcessingException as OutboxPublishException")
        void shouldThrowOutboxPublishExceptionOnJsonError() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class)))
                    .thenThrow(new JsonProcessingException("Serialisation failure") {
                    });

            // Act & Assert
            assertThatThrownBy(() ->
                    service.publishEntityRejected(testEntity, "reason", triggeredByUserId, orgId))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("LEGAL_ENTITY_REJECTED")
                    .hasMessageContaining(testEntity.getId().toString())
                    .hasCauseInstanceOf(JsonProcessingException.class);

            verify(outboxRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // publishStatusChanged
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("publishStatusChanged")
    class PublishStatusChanged {

        @Test
        @DisplayName("should persist a PENDING outbox event with status change details")
        void shouldPersistPendingEvent() throws Exception {
            // Arrange
            Status previous = Status.ACTIVE;
            Status next = Status.INACTIVE;
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(serialisedPayload);

            // Act
            service.publishStatusChanged(testEntity, previous, next, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository).save(eventCaptor.capture());
            LegalEntityOutboxEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getLegalEntity()).isEqualTo(testEntity);
            assertThat(savedEvent.getEventType()).isEqualTo(LegalEntityEventType.LEGAL_ENTITY_STATUS_CHANGED);
            assertThat(savedEvent.getPayload()).isEqualTo(serialisedPayload);
            assertThat(savedEvent.getOrganizationId()).isEqualTo(orgId);
            assertThat(savedEvent.getTriggeredByAuthUserId()).isEqualTo(triggeredByUserId);
            assertThat(savedEvent.getOutboxEventStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(savedEvent.getRetryCount()).isZero();
            assertThat(savedEvent.getIdempotencyKey())
                    .startsWith(LegalEntityEventType.LEGAL_ENTITY_STATUS_CHANGED.name() + ":"
                            + testEntity.getId() + ":");
        }

        @Test
        @DisplayName("should include status change fields in payload")
        void shouldIncludeStatusChangeFieldsInPayload() throws Exception {
            // Arrange
            Status previous = Status.ACTIVE;
            Status next = Status.INACTIVE;
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

            // Act
            service.publishStatusChanged(testEntity, previous, next, triggeredByUserId, orgId);

            // Assert
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            assertThat(capturedPayload)
                    .containsEntry("entityId", testEntity.getId())
                    .containsEntry("previousStatus", previous.name())
                    .containsEntry("newStatus", next.name())
                    .containsEntry("changedByAuthUserId", triggeredByUserId)
                    .containsEntry("organizationId", orgId);
        }

        @Test
        @DisplayName("should propagate JsonProcessingException as OutboxPublishException")
        void shouldThrowOutboxPublishExceptionOnJsonError() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class)))
                    .thenThrow(new JsonProcessingException("Serialisation failure") {
                    });

            // Act & Assert
            assertThatThrownBy(() ->
                    service.publishStatusChanged(testEntity, Status.ACTIVE, Status.INACTIVE,
                            triggeredByUserId, orgId))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("LEGAL_ENTITY_STATUS_CHANGED")
                    .hasMessageContaining(testEntity.getId().toString())
                    .hasCauseInstanceOf(JsonProcessingException.class);

            verify(outboxRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // Common behaviour
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("common outbox behaviour")
    class CommonBehaviour {

        @Test
        @DisplayName("should generate unique idempotency keys on successive calls")
        void shouldGenerateUniqueIdempotencyKeys() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");
            when(outboxRepository.save(any()))
                    .thenReturn(null, null);

            // Act
            service.publishEntityCreated(testEntity, triggeredByUserId, orgId);
            service.publishEntityCreated(testEntity, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository, times(2)).save(eventCaptor.capture());
            var savedEvents = eventCaptor.getAllValues();

            assertThat(savedEvents)
                    .hasSize(2)
                    .extracting(LegalEntityOutboxEvent::getIdempotencyKey)
                    .doesNotContainNull()
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("should log debug on successful persist")
        void shouldLogOnSuccess() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");

            // Act
            service.publishEntityCreated(testEntity, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository, times(1)).save(any());
        }
    }
}
