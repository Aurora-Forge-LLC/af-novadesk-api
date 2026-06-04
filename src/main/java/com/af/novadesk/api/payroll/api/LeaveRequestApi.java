package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.payroll.dto.LeaveBalanceDto;
import com.af.novadesk.api.payroll.dto.LeaveRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payroll - Leave Requests", description = "Leave request management — LLR-PAY-01.2–01.5")
@RequestMapping("/api/v1/payroll/leaves")
@SecurityRequirement(name = "bearerAuth")
public interface LeaveRequestApi {

    @Operation(summary = "Submit leave request", description = "Employee submits a new leave request with balance check")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Leave request submitted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid dates"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Insufficient balance")
    })
    @PostMapping("/requests")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<LeaveRequestDto>> submitLeaveRequest(@Valid @RequestBody LeaveRequestDto request);

    @Operation(summary = "Get leave request")
    @GetMapping("/requests/{id}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<LeaveRequestDto>> getLeaveRequest(@PathVariable UUID id);

    @Operation(summary = "My leave requests",
               description = "Returns leave requests for an employee. If employeeId is omitted, " +
                             "returns leave requests scoped to the caller's organization.")
    @GetMapping("/requests")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listMyRequests(
            @Parameter(description = "Optional employee ID to filter by. " +
                    "If omitted, returns leave requests scoped to the caller's organization.")
            @RequestParam(required = false) UUID employeeId);

    @Operation(summary = "Pending requests for approver")
    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listPending(
            @Parameter(description = "Approver employee ID") @RequestParam UUID approverId);

    @Operation(summary = "Approve leave request")
    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<LeaveRequestDto>> approveRequest(
            @PathVariable UUID id, @Valid @RequestBody LeaveRequestDto approval);

    @Operation(summary = "Reject leave request")
    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<LeaveRequestDto>> rejectRequest(
            @PathVariable UUID id, @Valid @RequestBody LeaveRequestDto rejection);

    @Operation(summary = "Request modification")
    @PostMapping("/requests/{id}/modify")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<LeaveRequestDto>> requestModification(
            @PathVariable UUID id, @Valid @RequestBody LeaveRequestDto modification);

    @Operation(summary = "Cancel leave request")
    @PostMapping("/requests/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<LeaveRequestDto>> cancelRequest(@PathVariable UUID id);

    @Operation(summary = "Get leave balances")
    @GetMapping("/balances")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getBalances(
            @Parameter(description = "Employee ID") @RequestParam UUID employeeId);
}
