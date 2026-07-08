package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.constants.LeaveType;
import com.af.novadesk.api.payroll.dto.LeaveActionDto;
import com.af.novadesk.api.payroll.dto.LeaveBalanceDto;
import com.af.novadesk.api.payroll.dto.LeaveRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    @Operation(summary = "List leave requests with optional filters and pagination",
               description = "Returns leave requests scoped to the caller's org. " +
                             "EMPLOYEE role callers are automatically scoped to their own requests.")
    @GetMapping("/requests")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<PageResponse<LeaveRequestDto>>> listMyRequests(
            @Parameter(description = "Filter by employee ID")                        @RequestParam(required = false) UUID employeeId,
            @Parameter(description = "Filter by legal entity ID")                    @RequestParam(required = false) UUID legalEntityId,
            @Parameter(description = "Filter by leave type")                         @RequestParam(required = false) LeaveType leaveType,
            @Parameter(description = "Filter by request status")                     @RequestParam(required = false) LeaveRequestStatus status,
            @Parameter(description = "Leave start date on or after this date")       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Leave start date on or before this date")      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "Filter by approver employee ID")               @RequestParam(required = false) UUID approverId,
            @Parameter(description = "Page number (0-based)")                        @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")                                    @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "Pending requests for approver or admin/manager",
               description = "Returns pending leave requests. For SUPER_ADMIN or MANAGER users, " +
                             "approverId is optional — provide legalEntityId to see all pending for an entity. " +
                             "For regular approvers, provide approverId.")
    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listPending(
            @Parameter(description = "Approver employee ID — optional for SUPER_ADMIN/MANAGER")
            @RequestParam(required = false) UUID approverId,
            @Parameter(description = "Legal entity ID — required when approverId is omitted for SUPER_ADMIN/MANAGER")
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
               description = "Returns leave balances. EMPLOYEE role users are scoped to their own balances automatically.")
    @GetMapping("/balances")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getBalances(
            @Parameter(description = "Employee ID. EMPLOYEE role users are forced to their own ID.") @RequestParam UUID employeeId);

    @Operation(summary = "Get leave balances (path-variable alias)",
               description = "Same as GET /balances?employeeId= but accepts the UUID as a path segment. " +
                             "EMPLOYEE role users are scoped to their own balances automatically.")
    @GetMapping("/balances/{employeeId}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getBalancesByPath(
            @Parameter(description = "Employee ID. EMPLOYEE role users are forced to their own ID.") @PathVariable UUID employeeId);

    @Operation(summary = "Get leave requests with unpaid days",
               description = "Returns leave requests where unpaidDaysUsed > 0 for the given employee")
    @GetMapping("/requests/unpaid")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listUnpaidRequests(
            @Parameter(description = "Employee ID") @RequestParam UUID employeeId);
}
