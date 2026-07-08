package com.af.novadesk.api.department.controller;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.department.api.DepartmentApi;
import com.af.novadesk.api.department.constants.DepartmentScope;
import com.af.novadesk.api.department.dto.DepartmentCreateRequest;
import com.af.novadesk.api.department.dto.DepartmentDto;
import com.af.novadesk.api.department.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DepartmentController implements DepartmentApi {

    private final DepartmentService departmentService;

    // =========================================================================
    // Read
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> listDepartments(DepartmentScope scope, UUID legalEntityId) {
        List<DepartmentDto> result;
        if (scope == DepartmentScope.ENTITY) {
            if (legalEntityId == null) {
                throw new IllegalArgumentException("legalEntityId is required when scope=ENTITY");
            }
            result = departmentService.listEntityDepartments(legalEntityId);
        } else {
            result = departmentService.listOrgDepartments();
        }
        return ResponseBuilder.ok(result, "Departments retrieved successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<DepartmentDto>> getDepartment(UUID id) {
        DepartmentDto result = departmentService.getDepartment(id);
        return ResponseBuilder.ok(result, "Department retrieved successfully");
    }

    // =========================================================================
    // Create
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<DepartmentDto>> createDepartment(@Valid DepartmentCreateRequest request) {
        DepartmentDto result = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Department created successfully", result));
    }

    // =========================================================================
    // Update
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<DepartmentDto>> updateDepartment(UUID id, @Valid DepartmentCreateRequest request) {
        DepartmentDto result = departmentService.updateDepartment(id, request);
        return ResponseBuilder.ok(result, "Department updated successfully");
    }

    // =========================================================================
    // Delete
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(UUID id) {
        departmentService.deleteDepartment(id);
        return ResponseBuilder.ok(null, "Department deleted successfully");
    }
}
