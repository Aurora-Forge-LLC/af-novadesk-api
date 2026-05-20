package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.exception.OutboxPublishException;
import com.af.novadesk.api.finance.constants.EntityUserAccessEventType;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.finance.entity.EntityUserAccessOutboxEvent;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.repositories.EntityUserAccessOutboxRepository;
import com.af.novadesk.api.identity.entity.ShadowUser;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EntityUserAccessOutboxService}.
 *
 * <p>Validates the transactional outbox behaviour for the {@link EntityUserAccess}
 * aggregate: correct payload construction, repository interaction, idempotency
 * key format, and error handling for JSON serialisation failures.</p>
 *
 * @see EntityUserAccessOutboxService
 * @see EntityUserAccessOutboxEvent
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EntityUserAccessOutboxService")
class EntityUserAccessOutboxServiceTest {

    // -------------------------------------------------------------------------
    // Mocks & Test Fixtures
    // -------------------------------------------------------------------------

    @Mock
    private EntityUserAccessOutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EntityUserAccessOutboxService service;

    @Captor
    private ArgumentCaptor<EntityUserAccessOutboxEvent> eventCaptor;

    private EntityUserAccess testAccess;
    private ShadowUser testShadowUser;
    private LegalEntity testLegalEntity;
    private String serialisedPayload;
    private UUID orgId;
    private UUID triggeredByUserId;
    private UUID affectedAuthUserId;

    @BeforeEach
    void setUp() {
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        triggeredByUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        affectedAuthUserId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        testShadowUser = ShadowUser.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000030"))
                .authUserId(affectedAuthUserId)
                .organizationId(orgId)
                .email("john.doe@example.com")
                .displayName("John Doe")
                .lastSyncedAt(LocalDateTime.now())
                .status(Status.ACTIVE)
                .build();

        testLegalEntity = LegalEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000010"))
                .build();

        testAccess = EntityUserAccess.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000020"))
                .shadowUser(testShadowUser)
                .legalEntity(testLegalEntity)
                .entityRole("VIEWER")
                .status(Status.ACTIVE)
                .build();

        serialisedPayload = """
                {"accessId":"00000000-0000-0000-0000-000000000020","role":"VIEWER"}""";
    }

    // =========================================================================
    // publishAccessGranted
    // =========================================================================

    @Nested
    @DisplayName("publishAccessGranted")
    class PublishAccessGranted {

        @Test
        @DisplayName("should persist a PENDING outbox event with correct payload")
        void shouldPersistPendingEvent() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(serialisedPayload);

            // Act
            service.publishAccessGranted(testAccess, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository).save(eventCaptor.capture());
            EntityUserAccessOutboxEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getEntityUserAccess()).isEqualTo(testAccess);
            assertThat(savedEvent.getEventType()).isEqualTo(EntityUserAccessEventType.USER_ACCESS_GRANTED);
            assertThat(savedEvent.getPayload()).isEqualTo(serialisedPayload);
            assertThat(savedEvent.getOrganizationId()).isEqualTo(orgId);
            assertThat(savedEvent.getAffectedAuthUserId()).isEqualTo(affectedAuthUserId);
            assertThat(savedEvent.getTriggeredByAuthUserId()).isEqualTo(triggeredByUserId);
            assertThat(savedEvent.getOutboxEventStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(savedEvent.getRetryCount()).isZero();
            assertThat(savedEvent.getIdempotencyKey())
                    .startsWith(EntityUserAccessEventType.USER_ACCESS_GRANTED.name() + ":"
                            + testAccess.getId() + ":");
        }

        @Test
        @DisplayName("should include all access fields in payload")
        void shouldIncludeAllFieldsInPayload() throws Exception {
            // Arrange
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

            // Act
            service.publishAccessGranted(testAccess, triggeredByUserId, orgId);

            // Assert
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            assertThat(capturedPayload)
                    .containsEntry("accessId", testAccess.getId())
                    .containsEntry("authUserId", affectedAuthUserId)
                    .containsEntry("entityId", testLegalEntity.getId())
                    .containsEntry("entityRole", "VIEWER")
                    .containsEntry("grantedByAuthUserId", triggeredByUserId)
                    .containsEntry("organizationId", orgId)
                    .containsKey("grantedAt");
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
                    service.publishAccessGranted(testAccess, triggeredByUserId, orgId))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("USER_ACCESS_GRANTED")
                    .hasMessageContaining(testAccess.getId().toString())
                    .hasCauseInstanceOf(JsonProcessingException.class);

            verify(outboxRepository, never()).save(any());
        }
    }

    // =========================================================================
    // publishAccessRevoked
    // =========================================================================

    @Nested
    @DisplayName("publishAccessRevoked")
    class PublishAccessRevoked {

        @Test
        @DisplayName("should persist a PENDING outbox event with revocation details")
        void shouldPersistPendingEvent() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(serialisedPayload);

            // Act
            service.publishAccessRevoked(testAccess, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository).save(eventCaptor.capture());
            EntityUserAccessOutboxEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getEntityUserAccess()).isEqualTo(testAccess);
            assertThat(savedEvent.getEventType()).isEqualTo(EntityUserAccessEventType.USER_ACCESS_REVOKED);
            assertThat(savedEvent.getPayload()).isEqualTo(serialisedPayload);
            assertThat(savedEvent.getOrganizationId()).isEqualTo(orgId);
            assertThat(savedEvent.getAffectedAuthUserId()).isEqualTo(affectedAuthUserId);
            assertThat(savedEvent.getTriggeredByAuthUserId()).isEqualTo(triggeredByUserId);
            assertThat(savedEvent.getOutboxEventStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(savedEvent.getRetryCount()).isZero();
            assertThat(savedEvent.getIdempotencyKey())
                    .startsWith(EntityUserAccessEventType.USER_ACCESS_REVOKED.name() + ":"
                            + testAccess.getId() + ":");
        }

        @Test
        @DisplayName("should include revocation fields in payload")
        void shouldIncludeRevocationFieldsInPayload() throws Exception {
            // Arrange
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

            // Act
            service.publishAccessRevoked(testAccess, triggeredByUserId, orgId);

            // Assert
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            assertThat(capturedPayload)
                    .containsEntry("accessId", testAccess.getId())
                    .containsEntry("authUserId", affectedAuthUserId)
                    .containsEntry("entityId", testLegalEntity.getId())
                    .containsEntry("entityRole", "VIEWER")
                    .containsEntry("revokedByAuthUserId", triggeredByUserId)
                    .containsEntry("organizationId", orgId)
                    .containsKey("revokedAt");
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
                    service.publishAccessRevoked(testAccess, triggeredByUserId, orgId))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("USER_ACCESS_REVOKED")
                    .hasMessageContaining(testAccess.getId().toString())
                    .hasCauseInstanceOf(JsonProcessingException.class);

            verify(outboxRepository, never()).save(any());
        }
    }

    // =========================================================================
    // publishRoleChanged
    // =========================================================================

    @Nested
    @DisplayName("publishRoleChanged")
    class PublishRoleChanged {

        @Test
        @DisplayName("should persist a PENDING outbox event with role change details")
        void shouldPersistPendingEvent() throws Exception {
            // Arrange
            String previousRole = "VIEWER";
            String newRole = "ADMIN";
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn(serialisedPayload);

            // Act
            service.publishRoleChanged(testAccess, previousRole, newRole, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository).save(eventCaptor.capture());
            EntityUserAccessOutboxEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getEntityUserAccess()).isEqualTo(testAccess);
            assertThat(savedEvent.getEventType()).isEqualTo(EntityUserAccessEventType.USER_ACCESS_ROLE_CHANGED);
            assertThat(savedEvent.getPayload()).isEqualTo(serialisedPayload);
            assertThat(savedEvent.getOrganizationId()).isEqualTo(orgId);
            assertThat(savedEvent.getAffectedAuthUserId()).isEqualTo(affectedAuthUserId);
            assertThat(savedEvent.getTriggeredByAuthUserId()).isEqualTo(triggeredByUserId);
            assertThat(savedEvent.getOutboxEventStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(savedEvent.getRetryCount()).isZero();
            assertThat(savedEvent.getIdempotencyKey())
                    .startsWith(EntityUserAccessEventType.USER_ACCESS_ROLE_CHANGED.name() + ":"
                            + testAccess.getId() + ":");
        }

        @Test
        @DisplayName("should include role change fields in payload")
        void shouldIncludeRoleChangeFieldsInPayload() throws Exception {
            // Arrange
            String previousRole = "VIEWER";
            String newRole = "ADMIN";
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

            // Act
            service.publishRoleChanged(testAccess, previousRole, newRole, triggeredByUserId, orgId);

            // Assert
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            assertThat(capturedPayload)
                    .containsEntry("accessId", testAccess.getId())
                    .containsEntry("authUserId", affectedAuthUserId)
                    .containsEntry("entityId", testLegalEntity.getId())
                    .containsEntry("previousRole", previousRole)
                    .containsEntry("newRole", newRole)
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
                    service.publishRoleChanged(testAccess, "VIEWER", "ADMIN",
                            triggeredByUserId, orgId))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("USER_ACCESS_ROLE_CHANGED")
                    .hasMessageContaining(testAccess.getId().toString())
                    .hasCauseInstanceOf(JsonProcessingException.class);

            verify(outboxRepository, never()).save(any());
        }
    }

    // =========================================================================
    // Common behaviour
    // =========================================================================

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
            service.publishAccessGranted(testAccess, triggeredByUserId, orgId);
            service.publishAccessGranted(testAccess, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository, times(2)).save(eventCaptor.capture());
            var savedEvents = eventCaptor.getAllValues();

            assertThat(savedEvents)
                    .hasSize(2)
                    .extracting(EntityUserAccessOutboxEvent::getIdempotencyKey)
                    .doesNotContainNull()
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("should log debug on successful persist")
        void shouldLogOnSuccess() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");

            // Act
            service.publishAccessGranted(testAccess, triggeredByUserId, orgId);

            // Assert
            verify(outboxRepository, times(1)).save(any());
        }
    }
}
