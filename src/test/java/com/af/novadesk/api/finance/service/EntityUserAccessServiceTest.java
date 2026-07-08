package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.service.impl.EntityUserAccessServiceImpl;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.common.service.AuthHubClientService;
import com.af.novadesk.api.finance.dto.EntityContextDto;
import com.af.novadesk.api.finance.dto.EntityUserAccessDto;
import com.af.novadesk.api.finance.dto.EntityUserInviteRequest;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryDto;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.common.entity.Department;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.department.repository.DepartmentRepository;
import com.af.novadesk.api.finance.exception.DuplicateUserAccessException;
import com.af.novadesk.api.finance.exception.EntityAccessDeniedException;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.exception.ShadowUserNotFoundException;
import com.af.novadesk.api.finance.exception.UserAccessNotFoundException;
import com.af.novadesk.api.finance.mapper.EntityUserAccessMapper;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
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

    @Mock
    private EntityAccessEmailPublisher emailPublisher;

    @Mock
    private AuthHubClientService authHubClientService;

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private EntityUserAccessServiceImpl service;

    @Captor
    private ArgumentCaptor<EntityUserAccess> accessCaptor;

    private UUID orgId;
    private UUID authUserId;
    private UUID entityId;
    private UUID accessId;
    private UUID departmentId;
    private LegalEntity testEntity;
    private ShadowUser testShadowUser;
    private EntityUserAccess testAccess;
    private EntityUserAccessDto requestDto;
    private EntityUserAccessDto responseDto;
    private Department testDepartment;

    @BeforeEach
    void setUp() {
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        entityId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        accessId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        departmentId = UUID.fromString("00000000-0000-0000-0000-000000000040");

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
                .entityRole("ENTITY_ADMIN")
                .status(Status.ACTIVE)
                .build();

        testDepartment = Department.builder()
                .id(departmentId)
                .organizationId(orgId)
                .legalEntityId(entityId)
                .name("IT")
                .status(Status.ACTIVE)
                .build();

        requestDto = new EntityUserAccessDto();
        requestDto.setAuthUserId(authUserId);
        requestDto.setEntityRole("ENTITY_ADMIN");
        requestDto.setDepartmentId(departmentId);

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

    /** Stubs the caller as an org-tier admin (JWT roles claim). */
    private void stubOrgAdminCaller() {
        when(securityContext.getRoles()).thenReturn(List.of("ORG_ADMIN"));
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
            stubOrgAdminCaller();
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(testDepartment));
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
            assertThat(saved.getEntityRole()).isEqualTo("ENTITY_ADMIN");
            // New grants are saved with PENDING status; activated on first context switch.
            assertThat(saved.getStatus()).isEqualTo(Status.PENDING);

            verify(outboxService).publishAccessGranted(testAccess, authUserId, orgId);
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when entity does not exist")
        void shouldThrowWhenEntityNotFound() {
            // Arrange
            stubOrgAdminCaller();
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
            stubOrgAdminCaller();
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(testDepartment));
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
            stubOrgAdminCaller();
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(testDepartment));
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
            stubOrgAdminCaller();
            // Use a different authUserId for the security context (the revoker) so
            // assertNotSelfEntityAdminRemoval does not fire — the access record's
            // shadowUser (testShadowUser) has authUserId == authUserId, so the
            // revoker must be someone else.
            UUID revokerUserId = UUID.fromString("00000000-0000-0000-0000-000000000099");
            when(securityContext.getAuthUserId()).thenReturn(revokerUserId);
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
            verify(outboxService).publishAccessRevoked(testAccess, revokerUserId, orgId);
        }

        @Test
        @DisplayName("should throw UserAccessNotFoundException when access not found")
        void shouldThrowWhenAccessNotFound() {
            // Arrange — entity exists in org, but the access record does not
            stubOrgAdminCaller();
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
            stubOrgAdminCaller();
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
            stubOrgAdminCaller();
            EntityUserAccessDto roleUpdateDto = new EntityUserAccessDto();
            roleUpdateDto.setEntityRole("ENTITY_ADMIN");

            EntityUserAccess updatedAccess = EntityUserAccess.builder()
                    .id(accessId)
                    .shadowUser(testShadowUser)
                    .legalEntity(testEntity)
                    .entityRole("ENTITY_ADMIN")
                    .status(Status.ACTIVE)
                    .build();

            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(accessRepository.findById(accessId)).thenReturn(Optional.of(testAccess));
            when(accessRepository.save(any(EntityUserAccess.class))).thenReturn(updatedAccess);
            when(mapper.toDto(updatedAccess)).thenReturn(responseDto);

            // Act
            EntityUserAccessDto result = service.updateRole(entityId, accessId, roleUpdateDto);

            // Assert
            verify(accessRepository).save(accessCaptor.capture());
            assertThat(accessCaptor.getValue().getEntityRole()).isEqualTo("ENTITY_ADMIN");
            verify(outboxService).publishRoleChanged(updatedAccess, "ENTITY_ADMIN", "ENTITY_ADMIN", authUserId, orgId);
            assertThat(result).isEqualTo(responseDto);
        }

        @Test
        @DisplayName("should throw UserAccessNotFoundException when access not found")
        void shouldThrowWhenAccessNotFound() {
            // Arrange
            stubOrgAdminCaller();
            EntityUserAccessDto roleUpdateDto = new EntityUserAccessDto();
            roleUpdateDto.setEntityRole("ADMIN");

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
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
            stubOrgAdminCaller();
            UUID differentEntityId = UUID.fromString("00000000-0000-0000-0000-000000000999");
            EntityUserAccessDto roleUpdateDto = new EntityUserAccessDto();
            roleUpdateDto.setEntityRole("ADMIN");

            LegalEntity otherEntity = LegalEntity.builder()
                    .id(differentEntityId).organizationId(orgId).build();
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(differentEntityId, orgId))
                    .thenReturn(Optional.of(otherEntity));
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
    @DisplayName("inviteUser")
    class InviteUser {

        @Test
        @DisplayName("should provision AuthHub user then grant entity access")
        void shouldInviteNewUserAndGrantAccess() {
            // Arrange
            stubOrgAdminCaller();
            EntityUserInviteRequest invite = new EntityUserInviteRequest(
                    "new.user@example.com", "New", "User", "ENTITY_ADMIN", departmentId);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(testDepartment));
            // AuthHub echoes back the pre-generated UUID
            when(authHubClientService.createEntityUser(any(UUID.class), eq("new.user@example.com"),
                    eq("New"), eq("User"), eq("ENTITY_ADMIN"), eq(orgId)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(shadowUserRepository.findByAuthUserId(any(UUID.class)))
                    .thenReturn(Optional.empty());
            when(shadowUserRepository.save(any(ShadowUser.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(accessRepository.existsByShadowUserAuthUserIdAndLegalEntityId(any(UUID.class), eq(entityId)))
                    .thenReturn(false);
            when(accessRepository.save(any(EntityUserAccess.class))).thenReturn(testAccess);
            when(mapper.toDto(testAccess)).thenReturn(responseDto);

            // Act
            EntityUserAccessDto result = service.inviteUser(entityId, invite);

            // Assert
            assertThat(result).isEqualTo(responseDto);
            verify(authHubClientService).createEntityUser(any(UUID.class), eq("new.user@example.com"),
                    eq("New"), eq("User"), eq("ENTITY_ADMIN"), eq(orgId));
            verify(accessRepository).save(accessCaptor.capture());
            assertThat(accessCaptor.getValue().getEntityRole()).isEqualTo("ENTITY_ADMIN");
        }

        @Test
        @DisplayName("should not call AuthHub when entity is not in caller's org")
        void shouldFailFastWhenEntityNotFound() {
            // Arrange
            stubOrgAdminCaller();
            EntityUserInviteRequest invite = new EntityUserInviteRequest(
                    "new.user@example.com", "New", "User", "ENTITY_ADMIN", departmentId);

            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.inviteUser(entityId, invite))
                    .isInstanceOf(EntityNotFoundException.class);
            verify(authHubClientService, never()).createEntityUser(any(), any(), any(), any(), any(), any());
        }
    }

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

        @Test
        @DisplayName("should allow org-tier role to select context without an access grant")
        void shouldSelectContextForOrgTierRoleWithoutGrant() {
            // Arrange
            EntityContextDto request = new EntityContextDto();
            request.setLegalEntityId(entityId);

            EntityContextDto response = new EntityContextDto();
            response.setLegalEntityId(entityId);

            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(securityContext.getRoles()).thenReturn(List.of("ORG_HR"));
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findByIdAndOrganizationId(entityId, orgId))
                    .thenReturn(Optional.of(testEntity));
            when(mapper.toContextDto(testEntity)).thenReturn(response);

            // Act
            EntityContextDto result = service.selectEntityContext(request);

            // Assert
            assertThat(result).isEqualTo(response);
            verify(accessRepository, never()).existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
                    any(), any(), any());
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
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findAccessibleByAuthUserIdAndOrganizationId(authUserId, orgId))
                    .thenReturn(entities);
            when(mapper.toSummaryDtoList(entities)).thenReturn(dtoList);

            // Act
            List<LegalEntitySummaryDto> result = service.listAccessibleEntities();

            // Assert
            assertThat(result).isEqualTo(dtoList);
            verify(mapper).toSummaryDtoList(entities);
        }

        @Test
        @DisplayName("listAccessibleEntities should return all org entities for org-tier roles")
        void shouldListAllOrgEntitiesForOrgTierRole() {
            // Arrange
            List<LegalEntity> entities = List.of(testEntity);
            List<LegalEntitySummaryDto> dtoList = List.of(new LegalEntitySummaryDto());

            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(securityContext.getRoles()).thenReturn(List.of("ORG_HR"));
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findAllByOrganizationId(orgId)).thenReturn(entities);
            when(mapper.toSummaryDtoList(entities)).thenReturn(dtoList);

            // Act
            List<LegalEntitySummaryDto> result = service.listAccessibleEntities();

            // Assert
            assertThat(result).isEqualTo(dtoList);
            verify(legalEntityRepository, never()).findAccessibleByAuthUserIdAndOrganizationId(any(), any());
        }

        @Test
        @DisplayName("listAccessibleEntities should return empty list when no accessible entities")
        void shouldReturnEmptyList() {
            // Arrange
            when(securityContext.getAuthUserId()).thenReturn(authUserId);
            when(securityContext.getOrganizationId()).thenReturn(orgId);
            when(legalEntityRepository.findAccessibleByAuthUserIdAndOrganizationId(authUserId, orgId))
                    .thenReturn(List.of());
            when(mapper.toSummaryDtoList(List.of())).thenReturn(List.of());

            // Act
            List<LegalEntitySummaryDto> result = service.listAccessibleEntities();

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
