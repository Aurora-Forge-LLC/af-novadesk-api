package com.af.novadesk.api.identity.service;

import com.af.novadesk.api.common.constants.Status;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ShadowUserSyncService}.
 *
 * <p>Validates the upsert logic: creation of new shadow users, update of existing
 * ones when claims change, and the read-only fast path when nothing has changed.</p>
 *
 * @see ShadowUserSyncService
 * @see ShadowUser
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShadowUserSyncService")
class ShadowUserSyncServiceTest {

    // -------------------------------------------------------------------------
    // Mocks & Test Fixtures
    // -------------------------------------------------------------------------

    @Mock
    private ShadowUserRepository shadowUserRepository;

    @Mock
    private ShadowUserOutboxService outboxService;

    @InjectMocks
    private ShadowUserSyncService service;

    @Captor
    private ArgumentCaptor<ShadowUser> shadowUserCaptor;

    private UUID authUserId;
    private UUID orgId;
    private String email;
    private String displayName;
    private ShadowUser existingUser;

    @BeforeEach
    void setUp() {
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        email = "john.doe@example.com";
        displayName = "John Doe";

        existingUser = ShadowUser.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000010"))
                .authUserId(authUserId)
                .organizationId(orgId)
                .email(email)
                .displayName(displayName)
                .lastSyncedAt(LocalDateTime.of(2025, 1, 15, 10, 30, 0))
                .status(Status.ACTIVE)
                .build();
    }

    // -------------------------------------------------------------------------
    // upsert — new user (create path)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("upsert — new user")
    class UpsertNewUser {

        @Test
        @DisplayName("should create a new ShadowUser when none exists")
        void shouldCreateNewUser() {
            // Arrange
            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());
            when(shadowUserRepository.save(any(ShadowUser.class))).thenAnswer(invocation -> {
                ShadowUser saved = invocation.getArgument(0);
                // Simulate ID assignment by the DB
                saved.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
                return saved;
            });

            // Act
            ShadowUser result = service.upsert(authUserId, orgId, email, displayName);

            // Assert
            verify(shadowUserRepository).save(shadowUserCaptor.capture());
            ShadowUser captured = shadowUserCaptor.getValue();

            assertThat(captured.getAuthUserId()).isEqualTo(authUserId);
            assertThat(captured.getOrganizationId()).isEqualTo(orgId);
            assertThat(captured.getEmail()).isEqualTo(email);
            assertThat(captured.getDisplayName()).isEqualTo(displayName);
            assertThat(captured.getStatus()).isEqualTo(Status.ACTIVE);
            assertThat(captured.getLastSyncedAt()).isNotNull();

            verify(outboxService).publishShadowUserCreated(any(ShadowUser.class), eq(orgId), eq(authUserId));
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
        }

        @Test
        @DisplayName("should create a new ShadowUser with null displayName")
        void shouldCreateUserWithNullDisplayName() {
            // Arrange
            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());
            when(shadowUserRepository.save(any(ShadowUser.class))).thenAnswer(invocation -> {
                ShadowUser saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            // Act
            ShadowUser result = service.upsert(authUserId, orgId, email, null);

            // Assert
            verify(shadowUserRepository).save(shadowUserCaptor.capture());
            assertThat(shadowUserCaptor.getValue().getDisplayName()).isNull();
            verify(outboxService).publishShadowUserCreated(any(ShadowUser.class), eq(orgId), eq(authUserId));
            assertThat(result).isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // upsert — existing user, claims changed (update path)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("upsert — existing user with changed claims")
    class UpsertExistingChanged {

        @Test
        @DisplayName("should update email and publish updated event when email changes")
        void shouldUpdateEmail() {
            // Arrange
            String newEmail = "new.email@example.com";
            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(existingUser));
            when(shadowUserRepository.save(any(ShadowUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ShadowUser result = service.upsert(authUserId, orgId, newEmail, displayName);

            // Assert
            verify(shadowUserRepository).save(shadowUserCaptor.capture());
            assertThat(shadowUserCaptor.getValue().getEmail()).isEqualTo(newEmail);
            assertThat(shadowUserCaptor.getValue().getLastSyncedAt()).isNotNull();

            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(email), eq(orgId), eq(authUserId));
            assertThat(result.getEmail()).isEqualTo(newEmail);
        }

        @Test
        @DisplayName("should update displayName and publish updated event when name changes")
        void shouldUpdateDisplayName() {
            // Arrange
            String newDisplayName = "John Updated";
            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(existingUser));
            when(shadowUserRepository.save(any(ShadowUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ShadowUser result = service.upsert(authUserId, orgId, email, newDisplayName);

            // Assert
            verify(shadowUserRepository).save(shadowUserCaptor.capture());
            assertThat(shadowUserCaptor.getValue().getDisplayName()).isEqualTo(newDisplayName);

            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(email), eq(orgId), eq(authUserId));
            assertThat(result.getDisplayName()).isEqualTo(newDisplayName);
        }

        @Test
        @DisplayName("should update when both email and displayName change")
        void shouldUpdateBothEmailAndDisplayName() {
            // Arrange
            String newEmail = "new.email@example.com";
            String newDisplayName = "New Name";
            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(existingUser));
            when(shadowUserRepository.save(any(ShadowUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ShadowUser result = service.upsert(authUserId, orgId, newEmail, newDisplayName);

            // Assert
            verify(shadowUserRepository).save(shadowUserCaptor.capture());
            ShadowUser captured = shadowUserCaptor.getValue();
            assertThat(captured.getEmail()).isEqualTo(newEmail);
            assertThat(captured.getDisplayName()).isEqualTo(newDisplayName);

            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(email), eq(orgId), eq(authUserId));
            assertThat(result.getEmail()).isEqualTo(newEmail);
            assertThat(result.getDisplayName()).isEqualTo(newDisplayName);
        }

        @Test
        @DisplayName("should handle email change with case difference as changed")
        void shouldDetectEmailCaseChange() {
            // Arrange
            String sameEmailDifferentCase = "John.Doe@example.com";
            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(existingUser));
            when(shadowUserRepository.save(any(ShadowUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ShadowUser result = service.upsert(authUserId, orgId, sameEmailDifferentCase, displayName);

            // Assert
            verify(shadowUserRepository).save(any(ShadowUser.class));
            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(email), eq(orgId), eq(authUserId));
            assertThat(result.getEmail()).isEqualTo(sameEmailDifferentCase);
        }

        @Test
        @DisplayName("should update when displayName changes from null to a value")
        void shouldUpdateWhenDisplayNameChangesFromNull() {
            // Arrange
            ShadowUser userWithNullDisplayName = ShadowUser.builder()
                    .id(existingUser.getId())
                    .authUserId(authUserId)
                    .organizationId(orgId)
                    .email(email)
                    .displayName(null)
                    .lastSyncedAt(LocalDateTime.of(2025, 1, 15, 10, 30, 0))
                    .status(Status.ACTIVE)
                    .build();

            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(userWithNullDisplayName));
            when(shadowUserRepository.save(any(ShadowUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ShadowUser result = service.upsert(authUserId, orgId, email, displayName);

            // Assert
            verify(shadowUserRepository).save(shadowUserCaptor.capture());
            assertThat(shadowUserCaptor.getValue().getDisplayName()).isEqualTo(displayName);
            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(email), eq(orgId), eq(authUserId));
            assertThat(result.getDisplayName()).isEqualTo(displayName);
        }
    }

    // -------------------------------------------------------------------------
    // upsert — existing user, no changes (fast path)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("upsert — existing user with no changes")
    class UpsertExistingNoChanges {

        @Test
        @DisplayName("should return existing user without saving or publishing when nothing changed")
        void shouldReturnExistingWithoutChanges() {
            // Arrange
            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(existingUser));

            // Act
            ShadowUser result = service.upsert(authUserId, orgId, email, displayName);

            // Assert
            verify(shadowUserRepository, never()).save(any(ShadowUser.class));
            verify(outboxService, never()).publishShadowUserCreated(any(), any(), any());
            verify(outboxService, never()).publishShadowUserUpdated(any(), any(), any(), any());
            assertThat(result).isSameAs(existingUser);
        }

        @Test
        @DisplayName("should return existing user when both email and displayName are null and existing are null")
        void shouldReturnExistingWhenBothNull() {
            // Arrange
            ShadowUser userWithNulls = ShadowUser.builder()
                    .id(existingUser.getId())
                    .authUserId(authUserId)
                    .organizationId(orgId)
                    .email(email)
                    .displayName(null)
                    .lastSyncedAt(LocalDateTime.of(2025, 1, 15, 10, 30, 0))
                    .status(Status.ACTIVE)
                    .build();

            when(shadowUserRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(userWithNulls));

            // Act
            ShadowUser result = service.upsert(authUserId, orgId, email, null);

            // Assert
            verify(shadowUserRepository, never()).save(any(ShadowUser.class));
            verify(outboxService, never()).publishShadowUserCreated(any(), any(), any());
            verify(outboxService, never()).publishShadowUserUpdated(any(), any(), any(), any());
            assertThat(result).isSameAs(userWithNulls);
        }
    }
}
