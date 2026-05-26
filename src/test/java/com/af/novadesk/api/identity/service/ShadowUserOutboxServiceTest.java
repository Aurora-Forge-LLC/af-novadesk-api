package com.af.novadesk.api.identity.service;

import com.af.novadesk.api.finance.repository.ShadowUserOutboxRepository;
import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.identity.constants.ShadowUserEventType;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.entity.ShadowUserOutboxEvent;
import com.af.novadesk.api.common.exception.OutboxPublishException;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ShadowUserOutboxService}.
 *
 * <p>Validates the transactional outbox behaviour for the {@link ShadowUser}
 * aggregate: correct payload construction, repository interaction, idempotency
 * key format, and error handling for JSON serialisation failures.</p>
 *
 * @see ShadowUserOutboxService
 * @see ShadowUserOutboxEvent
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShadowUserOutboxService")
class ShadowUserOutboxServiceTest {

    // -------------------------------------------------------------------------
    // Mocks & Test Fixtures
    // -------------------------------------------------------------------------

    @Mock
    private ShadowUserOutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ShadowUserOutboxService service;

    @Captor
    private ArgumentCaptor<ShadowUserOutboxEvent> eventCaptor;

    private ShadowUser testUser;
    private String serialisedPayload;
    private UUID orgId;
    private UUID triggeredByUserId;

    @BeforeEach
    void setUp() {
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        triggeredByUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        testUser = ShadowUser.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000010"))
                .authUserId(triggeredByUserId)
                .organizationId(orgId)
                .email("john.doe@example.com")
                .displayName("John Doe")
                .lastSyncedAt(LocalDateTime.of(2025, 1, 15, 10, 30, 0))
                .build();

        serialisedPayload = """
                {"shadowUserId":"00000000-0000-0000-0000-000000000010","email":"john.doe@example.com"}""";
    }

    // -------------------------------------------------------------------------
    // publishShadowUserCreated
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("publishShadowUserCreated")
    class PublishCreated {

        @Test
        @DisplayName("should persist a PENDING outbox event with correct payload")
        void shouldPersistPendingEvent() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(serialisedPayload);

            // Act
            service.publishShadowUserCreated(testUser, orgId, triggeredByUserId);

            // Assert
            verify(outboxRepository).save(eventCaptor.capture());
            ShadowUserOutboxEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getShadowUser()).isEqualTo(testUser);
            assertThat(savedEvent.getEventType()).isEqualTo(ShadowUserEventType.SHADOW_USER_CREATED);
            assertThat(savedEvent.getPayload()).isEqualTo(serialisedPayload);
            assertThat(savedEvent.getOrganizationId()).isEqualTo(orgId);
            assertThat(savedEvent.getTriggeredByAuthUserId()).isEqualTo(triggeredByUserId);
            assertThat(savedEvent.getOutboxEventStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(savedEvent.getRetryCount()).isZero();
            assertThat(savedEvent.getIdempotencyKey())
                    .startsWith(ShadowUserEventType.SHADOW_USER_CREATED.name() + ":"
                            + testUser.getId() + ":");
        }

        @Test
        @DisplayName("should include displayName even when null")
        void shouldIncludeDisplayNameWhenNull() throws Exception {
            // Arrange
            ShadowUser userWithNoDisplayName = ShadowUser.builder()
                    .id(UUID.randomUUID())
                    .authUserId(triggeredByUserId)
                    .organizationId(orgId)
                    .email("jane@example.com")
                    .displayName(null)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

            // Act
            service.publishShadowUserCreated(userWithNoDisplayName, orgId, triggeredByUserId);

            // Assert
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            assertThat(capturedPayload)
                    .containsEntry("displayName", "")
                    .containsEntry("email", "jane@example.com")
                    .containsKey("shadowUserId")
                    .containsKey("authUserId")
                    .containsKey("organizationId")
                    .containsKey("syncedAt");
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
                    service.publishShadowUserCreated(testUser, orgId, triggeredByUserId))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("SHADOW_USER_CREATED")
                    .hasMessageContaining(testUser.getId().toString())
                    .hasCauseInstanceOf(JsonProcessingException.class);

            verify(outboxRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // publishShadowUserUpdated
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("publishShadowUserUpdated")
    class PublishUpdated {

        @Test
        @DisplayName("should persist a PENDING outbox event with previous and new email")
        void shouldPersistEventWithEmailChange() throws Exception {
            // Arrange
            String previousEmail = "old.email@example.com";
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(serialisedPayload);

            // Act
            service.publishShadowUserUpdated(testUser, previousEmail, orgId, triggeredByUserId);

            // Assert
            verify(outboxRepository).save(eventCaptor.capture());
            ShadowUserOutboxEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getShadowUser()).isEqualTo(testUser);
            assertThat(savedEvent.getEventType()).isEqualTo(ShadowUserEventType.SHADOW_USER_UPDATED);
            assertThat(savedEvent.getPayload()).isEqualTo(serialisedPayload);
            assertThat(savedEvent.getOrganizationId()).isEqualTo(orgId);
            assertThat(savedEvent.getTriggeredByAuthUserId()).isEqualTo(triggeredByUserId);
            assertThat(savedEvent.getOutboxEventStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(savedEvent.getRetryCount()).isZero();
            assertThat(savedEvent.getIdempotencyKey())
                    .startsWith(ShadowUserEventType.SHADOW_USER_UPDATED.name() + ":"
                            + testUser.getId() + ":");
        }

        @Test
        @DisplayName("should include previousEmail and newEmail in payload")
        void shouldIncludeEmailFieldsInPayload() throws Exception {
            // Arrange
            String previousEmail = "old.email@example.com";
            String newEmail = testUser.getEmail();

            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

            // Act
            service.publishShadowUserUpdated(testUser, previousEmail, orgId, triggeredByUserId);

            // Assert
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            assertThat(capturedPayload)
                    .containsEntry("previousEmail", "old.email@example.com")
                    .containsEntry("newEmail", newEmail)
                    .containsEntry("shadowUserId", testUser.getId())
                    .containsEntry("authUserId", testUser.getAuthUserId())
                    .containsEntry("organizationId", orgId)
                    .containsEntry("displayName", testUser.getDisplayName())
                    .containsKey("syncedAt");
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
                    service.publishShadowUserUpdated(testUser, "old@example.com", orgId, triggeredByUserId))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("SHADOW_USER_UPDATED")
                    .hasMessageContaining(testUser.getId().toString())
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
                    .thenReturn(null, (Object) null);

            // Act
            service.publishShadowUserCreated(testUser, orgId, triggeredByUserId);
            service.publishShadowUserCreated(testUser, orgId, triggeredByUserId);

            // Assert
            verify(outboxRepository, times(2)).save(eventCaptor.capture());
            var savedEvents = eventCaptor.getAllValues();

            assertThat(savedEvents)
                    .hasSize(2)
                    .extracting(ShadowUserOutboxEvent::getIdempotencyKey)
                    .doesNotContainNull()
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("should log debug on successful persist")
        void shouldLogOnSuccess() throws Exception {
            // Our test only verifies the contract: save is called once.
            // Logging is verified via integration tests or log appender assertions.
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");

            service.publishShadowUserCreated(testUser, orgId, triggeredByUserId);

            verify(outboxRepository, times(1)).save(any());
        }
    }
}
