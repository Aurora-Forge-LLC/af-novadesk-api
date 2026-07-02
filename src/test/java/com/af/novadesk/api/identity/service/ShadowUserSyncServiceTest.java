package com.af.novadesk.api.identity.service;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShadowUserSyncService")
class ShadowUserSyncServiceTest {

    @Mock
    private ShadowUserRepository shadowUserRepository;

    @Mock
    private ShadowUserOutboxService outboxService;

    @InjectMocks
    private ShadowUserSyncService service;

    private static final UUID AUTH_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORG_ID       = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String EMAIL       = "john.doe@example.com";
    private static final String DISPLAY_NAME = "John Doe";
    private static final LocalDateTime TS   = LocalDateTime.of(2025, 1, 15, 10, 30, 0);
    private static final UUID USER_ID       = UUID.fromString("00000000-0000-0000-0000-000000000010");

    /**
     * Builds a base ShadowUser with all required non-null fields set.
     */
    private ShadowUser buildUser(String email, String displayName, LocalDateTime createdAt, LocalDateTime updatedAt) {
        ShadowUser user = ShadowUser.builder()
                .id(USER_ID)
                .authUserId(AUTH_USER_ID)
                .organizationId(ORG_ID)
                .email(email)
                .displayName(displayName)
                .lastSyncedAt(TS)
                .status(Status.ACTIVE)
                .build();
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(updatedAt);
        return user;
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
            // Arrange: no user exists before upsert, but appears after native upsert
            LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
            ShadowUser createdUser = buildUser(EMAIL, DISPLAY_NAME, now, now); // createdAt == updatedAt → isInsert

            when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                    .thenReturn(Optional.empty())       // pre-upsert lookup
                    .thenReturn(Optional.of(createdUser)); // post-upsert lookup

            // Act
            ShadowUser result = service.upsert(AUTH_USER_ID, ORG_ID, EMAIL, DISPLAY_NAME);

            // Assert
            verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(EMAIL), eq(DISPLAY_NAME));
            verify(outboxService).publishShadowUserCreated(any(ShadowUser.class), eq(ORG_ID), eq(AUTH_USER_ID));
            verify(outboxService, never()).publishShadowUserUpdated(any(), any(), any(), any());
            assertThat(result).isSameAs(createdUser);
        }

        @Test
        @DisplayName("should create a new ShadowUser with null displayName")
        void shouldCreateUserWithNullDisplayName() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
            ShadowUser createdUser = buildUser(EMAIL, null, now, now);

            when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(createdUser));

            ShadowUser result = service.upsert(AUTH_USER_ID, ORG_ID, EMAIL, null);

            verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(EMAIL), isNull());
            verify(outboxService).publishShadowUserCreated(any(ShadowUser.class), eq(ORG_ID), eq(AUTH_USER_ID));
            assertThat(result.getDisplayName()).isNull();
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
            String newEmail = "new.email@example.com";
            ShadowUser existingBefore = buildUser(EMAIL, DISPLAY_NAME, TS, TS.plusDays(1));
            ShadowUser afterUpsert   = buildUser(newEmail, DISPLAY_NAME, TS, LocalDateTime.of(2026, 1, 1, 12, 0, 0));

            when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                    .thenReturn(Optional.of(existingBefore))  // pre-upsert
                    .thenReturn(Optional.of(afterUpsert));    // post-upsert

            ShadowUser result = service.upsert(AUTH_USER_ID, ORG_ID, newEmail, DISPLAY_NAME);

            verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(newEmail), eq(DISPLAY_NAME));
            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(EMAIL), eq(ORG_ID), eq(AUTH_USER_ID));
            assertThat(result.getEmail()).isEqualTo(newEmail);
        }

        @Test
        @DisplayName("should update displayName and publish updated event when name changes")
        void shouldUpdateDisplayName() {
            String newDisplayName = "John Updated";
            ShadowUser existingBefore = buildUser(EMAIL, DISPLAY_NAME, TS, TS.plusDays(1));
            ShadowUser afterUpsert   = buildUser(EMAIL, newDisplayName, TS, LocalDateTime.of(2026, 1, 1, 12, 0, 0));

            when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                    .thenReturn(Optional.of(existingBefore))
                    .thenReturn(Optional.of(afterUpsert));

            ShadowUser result = service.upsert(AUTH_USER_ID, ORG_ID, EMAIL, newDisplayName);

            verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(EMAIL), eq(newDisplayName));
            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(EMAIL), eq(ORG_ID), eq(AUTH_USER_ID));
            assertThat(result.getDisplayName()).isEqualTo(newDisplayName);
        }

        @Test
        @DisplayName("should update when both email and displayName change")
        void shouldUpdateBothEmailAndDisplayName() {
            String newEmail = "new.email@example.com";
            String newDisplayName = "New Name";
            ShadowUser existingBefore = buildUser(EMAIL, DISPLAY_NAME, TS, TS.plusDays(1));
            ShadowUser afterUpsert   = buildUser(newEmail, newDisplayName, TS, LocalDateTime.of(2026, 1, 1, 12, 0, 0));

            when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                    .thenReturn(Optional.of(existingBefore))
                    .thenReturn(Optional.of(afterUpsert));

            ShadowUser result = service.upsert(AUTH_USER_ID, ORG_ID, newEmail, newDisplayName);

            verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(newEmail), eq(newDisplayName));
            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(EMAIL), eq(ORG_ID), eq(AUTH_USER_ID));
            assertThat(result.getEmail()).isEqualTo(newEmail);
            assertThat(result.getDisplayName()).isEqualTo(newDisplayName);
        }

        @Test
        @DisplayName("should handle email change with case difference as changed")
        void shouldDetectEmailCaseChange() {
            String sameEmailDifferentCase = "John.Doe@example.com";
            ShadowUser existingBefore = buildUser(EMAIL, DISPLAY_NAME, TS, TS.plusDays(1));
            ShadowUser afterUpsert   = buildUser(sameEmailDifferentCase, DISPLAY_NAME, TS, LocalDateTime.of(2026, 1, 1, 12, 0, 0));

            when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                    .thenReturn(Optional.of(existingBefore))
                    .thenReturn(Optional.of(afterUpsert));

            ShadowUser result = service.upsert(AUTH_USER_ID, ORG_ID, sameEmailDifferentCase, DISPLAY_NAME);

            verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(sameEmailDifferentCase), eq(DISPLAY_NAME));
            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(EMAIL), eq(ORG_ID), eq(AUTH_USER_ID));
            assertThat(result.getEmail()).isEqualTo(sameEmailDifferentCase);
        }

        @Test
        @DisplayName("should update when displayName changes from null to a value")
        void shouldUpdateWhenDisplayNameChangesFromNull() {
            ShadowUser existingBefore = buildUser(EMAIL, null, TS, TS.plusDays(1));
            ShadowUser afterUpsert   = buildUser(EMAIL, DISPLAY_NAME, TS, LocalDateTime.of(2026, 1, 1, 12, 0, 0));

            when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                    .thenReturn(Optional.of(existingBefore))
                    .thenReturn(Optional.of(afterUpsert));

            ShadowUser result = service.upsert(AUTH_USER_ID, ORG_ID, EMAIL, DISPLAY_NAME);

            verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(EMAIL), eq(DISPLAY_NAME));
            verify(outboxService).publishShadowUserUpdated(
                    any(ShadowUser.class), eq(EMAIL), eq(ORG_ID), eq(AUTH_USER_ID));
            assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        }
    }

    // -------------------------------------------------------------------------
    // upsert — existing user, no changes (fast path)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("upsert — existing user with no changes")
    class UpsertExistingNoChanges {

        @Test
        @DisplayName("should return existing user without publishing when nothing changed")
        void shouldReturnExistingWithoutChanges() {
            ShadowUser existingBefore = buildUser(EMAIL, DISPLAY_NAME, TS, TS.plusDays(1));
            ShadowUser afterUpsert   = buildUser(EMAIL, DISPLAY_NAME, TS, TS.plusDays(1));

            when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                    .thenReturn(Optional.of(existingBefore))
                    .thenReturn(Optional.of(afterUpsert));

            ShadowUser result = service.upsert(AUTH_USER_ID, ORG_ID, EMAIL, DISPLAY_NAME);

            verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(EMAIL), eq(DISPLAY_NAME));
            verify(outboxService, never()).publishShadowUserCreated(any(), any(), any());
            verify(outboxService, never()).publishShadowUserUpdated(any(), any(), any(), any());
            assertThat(result).isSameAs(afterUpsert);
        }

        @Test
        @DisplayName("should return existing user when both email and displayName are null and existing are null")
        void shouldReturnExistingWhenBothNull() {
            ShadowUser existingBefore = buildUser(EMAIL, null, TS, TS.plusDays(1));
            ShadowUser afterUpsert   = buildUser(EMAIL, null, TS, TS.plusDays(1));

            when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                    .thenReturn(Optional.of(existingBefore))
                    .thenReturn(Optional.of(afterUpsert));

            ShadowUser result = service.upsert(AUTH_USER_ID, ORG_ID, EMAIL, null);

            verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(EMAIL), isNull());
            verify(outboxService, never()).publishShadowUserCreated(any(), any(), any());
            verify(outboxService, never()).publishShadowUserUpdated(any(), any(), any(), any());
            assertThat(result).isSameAs(afterUpsert);
        }
    }

    // -------------------------------------------------------------------------
    // Edge case: upsert fails
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should throw if post-upsert lookup returns empty")
    void shouldThrowWhenPostUpsertLookupFails() {
        when(shadowUserRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.empty())  // pre-upsert
                .thenReturn(Optional.empty()); // post-upsert (should not happen)

        assertThrows(IllegalStateException.class,
                () -> service.upsert(AUTH_USER_ID, ORG_ID, EMAIL, DISPLAY_NAME));

        verify(shadowUserRepository).upsertShadowUser(any(UUID.class), eq(AUTH_USER_ID), eq(ORG_ID), eq(EMAIL), eq(DISPLAY_NAME));
    }
}
