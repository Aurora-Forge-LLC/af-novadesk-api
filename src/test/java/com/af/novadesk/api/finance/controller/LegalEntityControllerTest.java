package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.*;
import com.af.novadesk.api.finance.service.EntityUserAccessService;
import com.af.novadesk.api.finance.service.LegalEntityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LegalEntityController}.
 *
 * <p>Validates that each endpoint delegates to the correct service method,
 * returns the expected HTTP status, and wraps responses in the proper
 * {@link ApiResponse} envelope.</p>
 *
 * @see LegalEntityController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LegalEntityController")
class LegalEntityControllerTest {

    @Mock
    private LegalEntityService legalEntityService;

    @Mock
    private EntityUserAccessService accessService;

    @InjectMocks
    private LegalEntityController controller;

    private UUID entityId;
    private UUID accessId;
    private LegalEntityDto legalEntityDto;
    private UpdateEntityStatusRequest statusUpdateRequest;
    private LegalEntityPageDto pageDto;
    private LegalEntitySummaryDto summaryDto;
    private EntityUserAccessDto accessDto;
    private EntityContextDto contextDto;

    @BeforeEach
    void setUp() {
        entityId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        accessId = UUID.fromString("00000000-0000-0000-0000-000000000020");

        legalEntityDto = new LegalEntityDto();
        legalEntityDto.setId(entityId);
        legalEntityDto.setEntityName("Test Entity");

        statusUpdateRequest = new UpdateEntityStatusRequest();
        statusUpdateRequest.setStatus(Status.INACTIVE);

        pageDto = new LegalEntityPageDto();

        summaryDto = new LegalEntitySummaryDto();
        summaryDto.setId(entityId);
        summaryDto.setEntityName("Test Entity");

        accessDto = new EntityUserAccessDto();
        accessDto.setId(accessId);
        accessDto.setAuthUserId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
        accessDto.setEntityRole("VIEWER");

        contextDto = new EntityContextDto();
        contextDto.setLegalEntityId(entityId);
        contextDto.setEntityName("Test Entity");
    }

    // =========================================================================
    // LLR-FIN-01.1: Entity CRUD
    // =========================================================================

    @Nested
    @DisplayName("createEntity")
    class CreateEntity {

        @Test
        @DisplayName("should create entity and return 201 CREATED")
        void shouldCreateEntity() {
            // Arrange
            when(legalEntityService.createLegalEntity(legalEntityDto)).thenReturn(legalEntityDto);

            // Act
            ResponseEntity<ApiResponse<LegalEntityDto>> response = controller.createEntity(legalEntityDto);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(legalEntityDto);
            assertThat(response.getBody().getMessage()).contains("created");
            verify(legalEntityService).createLegalEntity(legalEntityDto);
        }
    }

    @Nested
    @DisplayName("listEntities")
    class ListEntities {

        @Test
        @DisplayName("should return paginated list with 200 OK")
        void shouldListEntities() {
            // Arrange
            when(legalEntityService.list(any(), any(), any(), any(), any(), any())).thenReturn(pageDto);

            // Act
            ResponseEntity<ApiResponse<LegalEntityPageDto>> response =
                    controller.listEntities(0, 20, "entityName", "ASC", null, null, null, null, null);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(pageDto);
            verify(legalEntityService).list(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getEntity")
    class GetEntity {

        @Test
        @DisplayName("should return entity by ID with 200 OK")
        void shouldGetEntity() {
            // Arrange
            when(legalEntityService.getById(entityId)).thenReturn(legalEntityDto);

            // Act
            ResponseEntity<ApiResponse<LegalEntityDto>> response = controller.getEntity(entityId);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(legalEntityDto);
            verify(legalEntityService).getById(entityId);
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("should update entity status and return 200 OK")
        void shouldUpdateStatus() {
            // Arrange
            when(legalEntityService.updateStatus(eq(entityId), eq(statusUpdateRequest))).thenReturn(legalEntityDto);

            // Act
            ResponseEntity<ApiResponse<LegalEntityDto>> response =
                    controller.updateStatus(entityId, statusUpdateRequest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("updated");
            verify(legalEntityService).updateStatus(entityId, statusUpdateRequest);
        }
    }

    // =========================================================================
    // LLR-FIN-01.2: Approval Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("approveEntity")
    class ApproveEntity {

        @Test
        @DisplayName("should approve entity and return 200 OK")
        void shouldApproveEntity() {
            // Arrange
            ApproveEntityDto approveDto = new ApproveEntityDto();
            when(legalEntityService.approveEntity(entityId, approveDto)).thenReturn(legalEntityDto);

            // Act
            ResponseEntity<ApiResponse<LegalEntityDto>> response =
                    controller.approveEntity(entityId, approveDto);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("approved");
            verify(legalEntityService).approveEntity(entityId, approveDto);
        }
    }

    @Nested
    @DisplayName("rejectEntity")
    class RejectEntity {

        @Test
        @DisplayName("should reject entity and return 200 OK")
        void shouldRejectEntity() {
            // Arrange
            RejectEntityDto rejectDto = new RejectEntityDto();
            rejectDto.setReason("Incomplete documentation");
            when(legalEntityService.rejectEntity(entityId, rejectDto)).thenReturn(legalEntityDto);

            // Act
            ResponseEntity<ApiResponse<LegalEntityDto>> response =
                    controller.rejectEntity(entityId, rejectDto);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("rejected");
            verify(legalEntityService).rejectEntity(entityId, rejectDto);
        }
    }

    // =========================================================================
    // LLR-FIN-01.3: User Access Management
    // =========================================================================

    @Nested
    @DisplayName("listAccessibleEntities")
    class ListAccessibleEntities {

        @Test
        @DisplayName("should return accessible entities with 200 OK")
        void shouldListAccessibleEntities() {
            // Arrange
            List<LegalEntitySummaryDto> summaries = List.of(summaryDto);
            when(accessService.listAccessibleEntities()).thenReturn(summaries);

            // Act
            ResponseEntity<ApiResponse<List<LegalEntitySummaryDto>>> response =
                    controller.listAccessibleEntities();

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(summaries);
            verify(accessService).listAccessibleEntities();
        }
    }

    @Nested
    @DisplayName("listAccess")
    class ListAccess {

        @Test
        @DisplayName("should return access list for entity with 200 OK")
        void shouldListAccess() {
            // Arrange
            List<EntityUserAccessDto> accessList = List.of(accessDto);
            when(accessService.listAccessForEntity(entityId)).thenReturn(accessList);

            // Act
            ResponseEntity<ApiResponse<List<EntityUserAccessDto>>> response =
                    controller.listAccess(entityId);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(accessList);
            verify(accessService).listAccessForEntity(entityId);
        }
    }

    @Nested
    @DisplayName("grantAccess")
    class GrantAccess {

        @Test
        @DisplayName("should grant access and return 201 CREATED")
        void shouldGrantAccess() {
            // Arrange
            when(accessService.grantAccess(entityId, accessDto)).thenReturn(accessDto);

            // Act
            ResponseEntity<ApiResponse<EntityUserAccessDto>> response =
                    controller.grantAccess(entityId, accessDto);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("granted");
            verify(accessService).grantAccess(entityId, accessDto);
        }
    }

    @Nested
    @DisplayName("updateRole")
    class UpdateRole {

        @Test
        @DisplayName("should update role and return 200 OK")
        void shouldUpdateRole() {
            // Arrange
            when(accessService.updateRole(entityId, accessId, accessDto)).thenReturn(accessDto);

            // Act
            ResponseEntity<ApiResponse<EntityUserAccessDto>> response =
                    controller.updateRole(entityId, accessId, accessDto);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("updated");
            verify(accessService).updateRole(entityId, accessId, accessDto);
        }
    }

    @Nested
    @DisplayName("revokeAccess")
    class RevokeAccess {

        @Test
        @DisplayName("should revoke access and return 200 OK")
        void shouldRevokeAccess() {
            // Act
            ResponseEntity<ApiResponse<Void>> response =
                    controller.revokeAccess(entityId, accessId);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("revoked");
            verify(accessService).revokeAccess(entityId, accessId);
        }
    }

    @Nested
    @DisplayName("selectContext")
    class SelectContext {

        @Test
        @DisplayName("should select entity context and return 200 OK")
        void shouldSelectContext() {
            // Arrange
            when(accessService.selectEntityContext(contextDto)).thenReturn(contextDto);

            // Act
            ResponseEntity<ApiResponse<EntityContextDto>> response =
                    controller.selectContext(contextDto);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("selected");
            verify(accessService).selectEntityContext(contextDto);
        }
    }
}
