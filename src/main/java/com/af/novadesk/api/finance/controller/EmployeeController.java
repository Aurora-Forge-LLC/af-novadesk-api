package com.af.novadesk.api.common.controller;

import com.af.novadesk.api.common.api.EmployeeApi;
import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.dto.EmployeeDto;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.common.service.EmployeeQueryService;
import com.af.novadesk.api.common.util.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("commonEmployeeController")
@RequiredArgsConstructor
public class EmployeeController implements EmployeeApi {

    private final EmployeeQueryService employeeQueryService;

    @Override
    public ResponseEntity<ApiResponse<PageResponse<EmployeeDto>>> list(
            String q, EmployeeStatus status, UUID legalEntityId, UUID managerId,
            int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        return ResponseBuilder.ok(
                employeeQueryService.listFiltered(q, status, legalEntityId, managerId,
                        PageRequest.of(page, size, sort)),
                ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
