package com.af.novadesk.api.common.controller;

import com.af.novadesk.api.common.api.EmployeeApi;
import com.af.novadesk.api.common.dto.EmployeeDto;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.service.EmployeeQueryService;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.common.constants.ApiMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController("commonEmployeeController")
@RequiredArgsConstructor
public class EmployeeController implements EmployeeApi {

    private final EmployeeQueryService employeeQueryService;

    @Override
    public ResponseEntity<ApiResponse<List<EmployeeDto>>> list(UUID legalEntityId) {
        return ResponseBuilder.ok(
                employeeQueryService.listByEntity(legalEntityId),
                ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
