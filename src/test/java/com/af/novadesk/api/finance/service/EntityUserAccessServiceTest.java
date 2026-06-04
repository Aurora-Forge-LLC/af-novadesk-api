package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.service.impl.EntityUserAccessServiceImpl;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.dto.EntityContextDto;
import com.af.novadesk.api.finance.dto.EntityUserAccessDto;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryDto;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.DuplicateUserAccessException;
import com.af.novadesk.api.finance.exception.EntityAccessDeniedException;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.exception.ShadowUserNotFoundException;
import com.af.novadesk.api.finance.exception.UserAccessNotFoundException;
import com.af.novadesk.api.finance.mapper.EntityUserAccessMapper;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EntityUserAccessService}.
 *
 * <p>Validates the full access lifecycle: granting access, revoking access,
 * updating roles, entity context switching, and queries.</p>
 *
 * @see EntityUserAccessService
 * @see EntityUserAccess
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EntityUserAccessService")
class EntityUserAccessServiceTest {

    // -------------------------------------------------------------------------
    // Mocks & Test Fixtures
    // -------------------------------------------------------------------------

    @Mock
    private EntityUserAccessRepository accessRepository;

    @Mock
    private LegalEntityRepository legalEntityRepository;

    @Mock
    private ShadowUserRepository shadowUserRepository;

    @Mock
    private EntityUserAccessMapper mapper;

    @Mock
    private EntityUserAccessOutboxService outboxService;

    @Mock
    private FinanceSecurityContext securityContext;

    @InjectMocks
    private EntityUserAccessServiceImpl service;

    @Captor
    private ArgumentCaptor<EntityUserAccess> accessCaptor;

    private UUID orgId;
    private UUID authUserId;
    private UUID entityId;
    private UUID accessId;
    private LegalEntity testEntity;
    private ShadowUser testShadowUser;
    private EntityUserAccess testAccess;
    private EntityUserAccessDto requestDto;
    private EntityUserAccessDto responseDto;

    @BeforeEach
    void setUp() {
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        entityId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        accessId = UUID.fromString("00000000-0000-0000-0000-000000000020");

        testEntity = LegalEntity.builder()
                .id(entityId)
                .entityName("Test Entity")
                .entityCode("TEST01")
                .country(CountryCode.US)
                .baseCurrency("USD")
                .incorporationDate(LocalDate.of(2020, 1, 15))
                .approvalStatus(ApprovalStatus.APPROVED)
                .status(Status.ACTIVE)
                .organizationId(orgId)
                .build();

        testShadowUser = ShadowUser.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000030"))
                .authUserId(authUserId)
                .organizationId(orgId)
                .email("john.doe@example.com")
                .displayName("John Doe")
                .lastSyncedAt(LocalDateTime.now())
                .status(Status.ACTIVE)
                .build();

        testAccess = EntityUserAccess.builder()
                .id(accessId)
                .shadowUser(testShadowUser)
                .legalEntity(testEntity)
                .entityRole("VIEWER")
                .status(Status.ACTIVE)
                .build();

        requestDto = new EntityUserAccessDto();
        requestDto.setAuthUserId(authUserId);
        requestDto.setEntityRole("VIEWER");

        responseDto = new EntityUserAccessDto();
        responseDto.setId(accessId);
        responseDto.setAuthUserId(authUserId);
        responseDto.setEntityRole("VIEWER");
        responseDto.setLegalEntityId(entityId);
        responseDto.setEntityName("Test Entity");
        responseDto.setEmail("john.doe@example.com");
        responseDto.setDisplayName("John Doe");
        responseDto.setStatus(Status.ACTIVE);
    }

    // =========================================================================
    // grantAccess
    // =========================================================================

    @Nested
    @DisplayName("grantAccess")
    class GrantAccess {

        @Test
        @DisplayName("should grant access and publish granted event")
        void shouldGrantAccess() {
            // Arrange
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(shadowUserRepository.findByAuthUserId(authUserId))
                    .thenReturn(Optional.of(testShadowUser));
            when(accessRepository.existsByShadowUserAuthUserIdAndLegalEntityId(authUserId, entityId))
                    .thenReturn(false);
            when(accessRepository.save(any(EntityUserAccess.class))).thenReturn(testAccess);
            when(mapper.toDto(testAccess)).thenReturn(responseDto);

            // Act
            EntityUserAccessDto result = service.grantAccess(entityId, requestDto);

            // Assert
            verify(accessRepository).save(accessCaptor.capture());
            EntityUserAccess saved = accessCaptor.getValue();
            assertThat(saved.getShadowUser()).isEqualTo(testShadowUser);
            assertThat(saved.getLegalEntity()).isEqualTo(testEntity);
            assertThat(saved.getEntityRole()).isEqualTo("VIEWER");
            assertThat(saved.getStatus()).isEqualTo(Status.ACTIVE);

            verify(outboxService).publishAccessGranted(testAccess, authUserId, orgId);
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when entity does not exist")
        void shouldThrowWhenEntityNotFound() {
            // Arrange
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.grantAccess(entityId, requestDto))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(entityId.toString());

            verify(accessRepository, never()).save(any());
            verify(outboxService, never()).publishAccessGranted(any(), any(), any());
        }

        @Test
        @DisplayName("should throw ShadowUserNotFoundException when shadow user not found")
        void shouldThrowWhenShadowUserNotFound() {
            // Arrange
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(shadowUserRepository.findByAuthUserId(authUserId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.grantAccess(entityId, requestDto))
                    .isInstanceOf(ShadowUserNotFoundException.class)
                    .hasMessageContaining(authUserId.toString());

            verify(accessRepository, never()).save(any());
            verify(outboxService, never()).publishAccessGranted(any(), any(), any());
        }

        @Test
        @DisplayName("should throw DuplicateUserAccessException when access already exists")
        void shouldThrowWhenDuplicateAccess() {
            // Arrange
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(shadowUserRepository.findByAuthUserId(authUserId))
                    .thenReturn(Optional.of(testShadowUser));
            when(accessRepository.existsByShadowUserAuthUserIdAndLegalEntityId(authUserId, entityId))
                    .thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.grantAccess(entityId, requestDto))
                    .isInstanceOf(DuplicateUserAccessException.class)
                    .hasMessageContaining(authUserId.toString())
                    .hasMessageContaining(entityId.toString());

            verify(accessRepository, never()).save(any());
            verify(outboxService, never()).publishAccessGranted(any(), any(), any());
        }
    }

    // =========================================================================
    // revokeAccess
    // =========================================================================

    @Nested
    @DisplayName("revokeAccess")
    class RevokeAccess {

        @Test
        @DisplayName("should revoke access and publish revoked event")
        void shouldRevokeAccess() {
            // Arrange
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(accessRepository.findById(accessId)).thenReturn(Optional.of(testAccess));
            when(accessRepository.save(any(EntityUserAccess.class))).thenReturn(testAccess);

            // Act
            service.revokeAccess(entityId, accessId);

            // Assert
            verify(accessRepository).save(accessCaptor.capture());
            assertThat(accessCaptor.getValue().getStatus()).isEqualTo(Status.INACTIVE);
            verify(outboxService).publishAccessRevoked(testAccess, authUserId, orgId);
        }

        @Test
        @DisplayName("should throw UserAccessNotFoundException when access not found")
        void shouldThrowWhenAccessNotFound() {
            // Arrange — entity exists in org, but the access record does not
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(accessRepository.findById(accessId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.revokeAccess(entityId, accessId))
                    .isInstanceOf(UserAccessNotFoundException.class)
                    .hasMessageContaining(accessId.toString());

            verify(accessRepository, never()).save(any());
            verify(outboxService, never()).publishAccessRevoked(any(), any(), any());
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when entityId is not in caller's org")
        void shouldThrowWhenAccessEntityMismatch() {
            // Arrange — caller passes an entityId that doesn't belong to their org.
            // The org gate (requireEntityInOrg) now fires BEFORE the access lookup,
            // so we get EntityNotFoundException rather than UserAccessNotFoundException.
            // This is intentional: refusing early prevents cross-org enumeration.
            UUID differentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000999");
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(differentEntityId, orgId))
                    .thenReturn(Optional.empty());   // entity 999 not in this org

            // Act & Assert
            assertThatThrownBy(() -> service.revokeAccess(differentEntityId, accessId))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(accessRepository, never()).save(any());
            verify(outboxService, never()).publishAccessRevoked(any(), any(), any());
        }
    }

    // =========================================================================
    // updateRole
    // =========================================================================

    @Nested
    @DisplayName("updateRole")
    class UpdateRole {

        @Test
        @DisplayName("should update role and publish role changed event")
        void shouldUpdateRole() {
            // Arrange
            EntityUserAccessDto roleUpdateDto = new EntityUserAccessDto();
            roleUpdateDto.setEntityRole("ADMIN");

            EntityUserAccess updatedAccess = EntityUserAccess.builder()
                    .id(accessId)
                    .shadowUser(testShadowUser)
                    .legalEntity(testEntity)
                    .entityRole("ADMIN")
                    .status(Status.ACTIVE)
                    .build();

            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(accessRepository.findById(accessId)).thenReturn(Optional.of(testAccess));
            when(accessRepository.save(any(EntityUserAccess.class))).thenReturn(updatedAccess);
            when(mapper.toDto(updatedAccess)).thenReturn(responseDto);

            // Act
            EntityUserAccessDto result = service.updateRole(entityId, accessId, roleUpdateDto);

            // Assert
            verify(accessRepository).save(accessCaptor.capture());
            assertThat(accessCaptor.getValue().getEntityRole()).isEqualTo("ADMIN");
            verify(outboxService).publishRoleChanged(updatedAccess, "VIEWER", "ADMIN", authUserId, orgId);
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("should throw UserAccessNotFoundException when access not found")
        void shouldThrowWhenAccessNotFound() {
            // Arrange
            EntityUserAccessDto roleUpdateDto = new EntityUserAccessDto();
            roleUpdateDto.setEntityRole("ADMIN");

            when(accessRepository.findById(accessId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.updateRole(entityId, accessId, roleUpdateDto))
                    .isInstanceOf(UserAccessNotFoundException.class)
                    .hasMessageContaining(accessId.toString());

            verify(accessRepository, never()).save(any());
            verify(outboxService, never()).publishRoleChanged(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw UserAccessNotFoundException when access belongs to different entity")
        void shouldThrowWhenAccessEntityMismatch() {
            // Arrange
            UUID differentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000999");
            EntityUserAccessDto roleUpdateDto = new EntityUserAccessDto();
            roleUpdateDto.setEntityRole("ADMIN");

            when(accessRepository.findById(accessId)).thenReturn(Optional.of(testAccess));

            // Act & Assert
            assertThatThrownBy(() -> service.updateRole(differentEntityId, accessId, roleUpdateDto))
                    .isInstanceOf(UserAccessNotFoundException.class)
                    .hasMessageContaining(accessId.toString());

            verify(accessRepository, never()).save(any());
            verify(outboxService, never()).publishRoleChanged(any(), any(), any(), any(), any());
        }
    }

    // =========================================================================
    // selectEntityContext
    // =========================================================================

    @Nested
    @DisplayName("selectEntityContext")
    class SelectEntityContext {

        @Test
        @DisplayName("should validate access, update lastAccessedAt, and return context")
        void shouldSelectEntityContext() {
            // Arrange
            EntityContextDto request = new EntityContextDto();
            request.setLegalEntityId(entityId);

            EntityContextDto response = new EntityContextDto();
            response.setLegalEntityId(entityId);
            response.setEntityName("Test Entity");
            response.setEntityCode("TEST01");
            response.setBaseCurrency("USD");

            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(accessRepository.existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
                    Status.ACTIVE, authUserId, entityId))
                    .thenReturn(true);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(mapper.toContextDto(testEntity)).thenReturn(response);

            // Act
            EntityContextDto result = service.selectEntityContext(request);

            // Assert
            verify(accessRepository).updateLastAccessedAt(eq(authUserId), eq(entityId), any(LocalDateTime.class));
            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("should throw EntityAccessDeniedException when user has no access")
        void shouldThrowWhenNoAccess() {
            // Arrange
            EntityContextDto request = new EntityContextDto();
            request.setLegalEntityId(entityId);

            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(accessRepository.existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
                    Status.ACTIVE, authUserId, entityId))
                    .thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.selectEntityContext(request))
                    .isInstanceOf(EntityAccessDeniedException.class)
                    .hasMessageContaining(authUserId.toString())
                    .hasMessageContaining(entityId.toString());

            verify(accessRepository, never()).updateLastAccessedAt(any(), any(), any());
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when entity does not exist")
        void shouldThrowWhenEntityNotFound() {
            // Arrange
            EntityContextDto request = new EntityContextDto();
            request.setLegalEntityId(entityId);

            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(accessRepository.existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
                    Status.ACTIVE, authUserId, entityId))
                    .thenReturn(true);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.selectEntityContext(request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(entityId.toString());
        }
    }

    // =========================================================================
    // Queries
    // =========================================================================

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("listAccessForEntity should return access list for entity")
        void shouldListAccessForEntity() {
            // Arrange
            List<EntityUserAccess> accessList = List.of(testAccess);
            List<EntityUserAccessDto> dtoList = List.of(responseDto);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(accessRepository.findAllByLegalEntityId(entityId)).thenReturn(accessList);
            when(mapper.toAccessDtoList(accessList)).thenReturn(dtoList);

            // Act
            List<EntityUserAccessDto> result = service.listAccessForEntity(entityId);

            // Assert
            assertThat(result).isEqualTo(dtoList);
            verify(mapper).toAccessDtoList(accessList);
        }

        @Test
        @DisplayName("listAccessForEntity should throw EntityNotFoundException when entity not found")
        void shouldThrowWhenEntityNotFound() {
            // Arrange
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.listAccessForEntity(entityId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(entityId.toString());

            verify(accessRepository, never()).findAllByLegalEntityId(any());
        }

        @Test
        @DisplayName("listAccessibleEntities should return entities accessible by user")
        void shouldListAccessibleEntities() {
            // Arrange
            List<LegalEntity> entities = List.of(testEntity);
            List<LegalEntitySummaryDto> dtoList = List.of(new LegalEntitySummaryDto());

            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.findAccessibleByAuthUserId(authUserId)).thenReturn(entities);
            when(mapper.toSummaryDtoList(entities)).thenReturn(dtoList);

            // Act
            List<LegalEntitySummaryDto> result = service.listAccessibleEntities();

            // Assert
            assertThat(result).isEqualTo(dtoList);
            verify(mapper).toSummaryDtoList(entities);
        }

        @Test
        @DisplayName("listAccessibleEntities should return empty list when no accessible entities")
        void shouldReturnEmptyList() {
            // Arrange
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.findAccessibleByAuthUserId(authUserId)).thenReturn(List.of());
            when(mapper.toSummaryDtoList(List.of())).thenReturn(List.of());

            // Act
            List<LegalEntitySummaryDto> result = service.listAccessibleEntities();

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
