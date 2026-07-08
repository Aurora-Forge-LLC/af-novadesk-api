package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.payroll.dto.LeaveActionDto;
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
               description = "Returns leave requests for an employee. Non-HR callers are always scoped to " +
                             "their own employee ID regardless of the employeeId/legalEntityId params. " +
                             "HR (leave:approve/leave:manage) may omit employeeId to see results scoped by " +
                             "legalEntityId (if provided) or the caller's organization.")
    @GetMapping("/requests")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listMyRequests(
            @Parameter(description = "Optional employee ID to filter by. Ignored (forced to caller's own " +
                    "ID) for non-HR callers. If omitted, results are scoped by legalEntityId or organization.")
            @RequestParam(required = false) UUID employeeId,
            @Parameter(description = "Optional legal entity ID to filter by when employeeId is omitted.")
            @RequestParam(required = false) UUID legalEntityId);

    @Operation(summary = "Pending requests for approver or HR",
               description = "Returns pending leave requests. Only HR (leave:approve/leave:manage) may omit " +
                             "approverId — provide legalEntityId to see all pending for an entity. Non-HR " +
                             "callers may only pass their own employee ID as approverId.")
    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listPending(
            @Parameter(description = "Approver employee ID — optional for HR only")
            @RequestParam(required = false) UUID approverId,
            @Parameter(description = "Legal entity ID — required when approverId is omitted (HR only)")
            @RequestParam(required = false) UUID legalEntityId);

    @Operation(summary = "Approve leave request",
               description = "Requires leave:approve permission")
    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("hasAuthority('leave:approve')")
    ResponseEntity<ApiResponse<LeaveRequestDto>> approveRequest(
            @PathVariable UUID id, @Valid @RequestBody LeaveActionDto approval);

    @Operation(summary = "Reject leave request",
               description = "Requires leave:approve permission")
    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasAuthority('leave:approve')")
    ResponseEntity<ApiResponse<LeaveRequestDto>> rejectRequest(
            @PathVariable UUID id, @Valid @RequestBody LeaveActionDto rejection);

    @Operation(summary = "Request modification",
               description = "Requires leave:approve permission")
    @PostMapping("/requests/{id}/modify")
    @PreAuthorize("hasAuthority('leave:approve')")
    ResponseEntity<ApiResponse<LeaveRequestDto>> requestModification(
            @PathVariable UUID id, @Valid @RequestBody LeaveActionDto modification);

    @Operation(summary = "Cancel leave request")
    @PostMapping("/requests/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<LeaveRequestDto>> cancelRequest(@PathVariable UUID id);

    @Operation(summary = "Get leave balances",
               description = "Returns leave balances. Non-HR callers are scoped to their own balances automatically.")
    @GetMapping("/balances")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getBalances(
            @Parameter(description = "Employee ID. Non-HR callers are forced to their own ID.") @RequestParam UUID employeeId);

    @Operation(summary = "Get leave balances (path-variable alias)",
               description = "Same as GET /balances?employeeId= but accepts the UUID as a path segment. " +
                             "Non-HR callers are scoped to their own balances automatically.")
    @GetMapping("/balances/{employeeId}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getBalancesByPath(
            @Parameter(description = "Employee ID. Non-HR callers are forced to their own ID.") @PathVariable UUID employeeId);

    @Operation(summary = "Get leave requests with unpaid days",
               description = "Returns leave requests where unpaidDaysUsed > 0 for the given employee")
    @GetMapping("/requests/unpaid")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listUnpaidRequests(
            @Parameter(description = "Employee ID") @RequestParam UUID employeeId);
}
