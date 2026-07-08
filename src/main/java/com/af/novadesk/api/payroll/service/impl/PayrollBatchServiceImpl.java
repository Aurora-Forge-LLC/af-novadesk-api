package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.CmEmployeeEntityAssignmentRepository;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.payroll.repository.LeaveRequestRepository;
import com.af.novadesk.api.payroll.repository.PayrollDetailsRepository;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.payroll.constants.*;
import com.af.novadesk.api.payroll.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.af.novadesk.api.payroll.entity.*;
import com.af.novadesk.api.payroll.exception.*;
import com.af.novadesk.api.payroll.mapper.LeaveRequestMapper;
import com.af.novadesk.api.payroll.mapper.PayrollBatchMapper;
import com.af.novadesk.api.payroll.repository.*;
import com.af.novadesk.api.payroll.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class PayrollBatchServiceImpl implements PayrollBatchService {

    private final PayrollBatchRepository batchRepository;
    private final CmEmployeeRepository cmEmployeeRepository;
    private final CmEmployeeEntityAssignmentRepository cmAssignmentRepository;
    private final PayrollFlaggedEmployeeRepository flaggedEmployeeRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipLineItemRepository payslipLineItemRepository;
    private final PayrollLedgerEntryRepository ledgerEntryRepository;
    private final TaxConfigurationRepository taxConfigRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestMapper leaveRequestMapper;
    private final PayrollBatchMapper mapper;
    private final PayrollBatchOutboxService outboxService;
    private final TaxCalculationStrategyFactory taxStrategyFactory;

    public PayrollBatchServiceImpl(PayrollBatchRepository batchRepository,
                                   CmEmployeeRepository cmEmployeeRepository,
                                   CmEmployeeEntityAssignmentRepository cmAssignmentRepository,
                                   PayrollFlaggedEmployeeRepository flaggedEmployeeRepository,
                                   PayslipRepository payslipRepository,
                                   PayslipLineItemRepository payslipLineItemRepository,
                                   PayrollLedgerEntryRepository ledgerEntryRepository,
                                   TaxConfigurationRepository taxConfigRepository,
                                   LegalEntityRepository legalEntityRepository,
                                   PayrollDetailsRepository payrollDetailsRepository,
                                   LeaveRequestRepository leaveRequestRepository,
                                   LeaveRequestMapper leaveRequestMapper,
                                   PayrollBatchMapper mapper,
                                   PayrollBatchOutboxService outboxService,
                                   TaxCalculationStrategyFactory taxStrategyFactory) {
        this.batchRepository = batchRepository;
        this.cmEmployeeRepository = cmEmployeeRepository;
        this.cmAssignmentRepository = cmAssignmentRepository;
        this.flaggedEmployeeRepository = flaggedEmployeeRepository;
        this.payslipRepository = payslipRepository;
        this.payslipLineItemRepository = payslipLineItemRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.taxConfigRepository = taxConfigRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveRequestMapper = leaveRequestMapper;
        this.mapper = mapper;
        this.outboxService = outboxService;
        this.taxStrategyFactory = taxStrategyFactory;
    }

    @Override
    public PayrollBatchDto initiatePayroll(PayrollBatchDto request) {
        LegalEntity entity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new RuntimeException("Legal entity not found: " + request.getLegalEntityId()));

        // Check no existing ACTIVE batch for same period (soft-deleted INACTIVE batches are allowed)
        batchRepository.findByLegalEntityIdAndPayPeriodStartAndPayPeriodEndAndStatus(
                entity.getId(), request.getPayPeriodStart(), request.getPayPeriodEnd(), Status.ACTIVE)
                .ifPresent(b -> { throw new PayrollBatchAlreadyExistsException(
                        entity.getId(), request.getPayPeriodStart(), request.getPayPeriodEnd()); });

        PayrollBatch batch = new PayrollBatch();
        batch.setLegalEntity(entity);
        batch.setPayPeriodStart(request.getPayPeriodStart());
        batch.setPayPeriodEnd(request.getPayPeriodEnd());
        batch.setPaymentDate(request.getPaymentDate());
        batch.setCurrencyCode(request.getCurrencyCode() != null ? request.getCurrencyCode() : entity.getBaseCurrency());
        batch.setBatchStatus(PayrollBatchStatus.INITIATED);
        batch.setTotalHeadcount(0);
        batch.setProcessedCount(0);
        batch.setFlaggedCount(0);
        batch.setTotalGrossSalary(BigDecimal.ZERO);
        batch.setTotalDeductions(BigDecimal.ZERO);
        batch.setTotalNetPayout(BigDecimal.ZERO);
        batch.setStatus(Status.ACTIVE);

        batch = batchRepository.save(batch);

        outboxService.createEvent(batch, PayrollBatchEventType.PAYROLL_INITIATED,
                "{\"batchId\":\"" + batch.getId() + "\"}", null);

        return mapper.toDto(batch);
    }

    @Override
    public PayrollBatchDto validateAttendanceAndFlag(UUID batchId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));

        if (batch.getBatchStatus() != PayrollBatchStatus.INITIATED) {
            throw new InvalidPayrollStateException(batchId, batch.getBatchStatus(), PayrollBatchStatus.UNDER_REVIEW);
        }

        // ── Fetch assignments whose tenure [hireDate, terminationDate] overlaps with the pay period ──
        // This captures: full-month employees, mid-month hires, mid-month terminations, and transfers.
        List<CmEmployeeEntityAssignment> assignments = cmAssignmentRepository
                .findAssignmentsOverlappingPeriod(
                        batch.getLegalEntity().getId(),
                        batch.getPayPeriodStart(), batch.getPayPeriodEnd());

        // Build assignment map: employeeId → assignment
        Map<UUID, CmEmployeeEntityAssignment> assignmentMap = new LinkedHashMap<>();
        Map<UUID, PayrollDetails> payrollDetailsMap = new HashMap<>();
        for (CmEmployeeEntityAssignment a : assignments) {
            CmEmployee emp = a.getEmployee();
            assignmentMap.put(emp.getId(), a);
            payrollDetailsRepository.findByEmployeeId(emp.getId())
                    .ifPresent(pd -> payrollDetailsMap.put(emp.getId(), pd));
        }

        int flagged = 0;
        int includedCount = 0;

        for (Map.Entry<UUID, CmEmployeeEntityAssignment> entry : assignmentMap.entrySet()) {
            CmEmployee emp = entry.getValue().getEmployee();
            CmEmployeeEntityAssignment assignment = entry.getValue();
            PayrollDetails pd = payrollDetailsMap.get(emp.getId());
            BigDecimal salary = pd != null ? pd.getBaseSalary() : BigDecimal.ZERO;

            // ── Compute employee-specific effective period within the pay period ──
            // Effective start: the later of hireDate or payPeriodStart
            LocalDate effectiveStart = assignment.getHireDate().isAfter(batch.getPayPeriodStart())
                    ? assignment.getHireDate() : batch.getPayPeriodStart();
            // Effective end: the earlier of terminationDate or payPeriodEnd
            LocalDate effectiveEnd = assignment.getTerminationDate() != null
                    && assignment.getTerminationDate().isBefore(batch.getPayPeriodEnd())
                    ? assignment.getTerminationDate() : batch.getPayPeriodEnd();

            // Skip if the employee's tenure doesn't overlap the period (defensive)
            if (effectiveStart.isAfter(effectiveEnd)) {
                continue;
            }
            includedCount++;

            int employeeWorkingDays = countWorkingDays(effectiveStart, effectiveEnd);

            // Query all approved unpaid leave requests for this employee within the pay period.
            // Covers both: Case A (UNPAID policy) and Case B (earned leave excess → unpaidDaysUsed > 0).
            List<LeaveRequest> unpaidLeaves = leaveRequestRepository
                    .findUnpaidLeaveRequestsForPeriod(emp.getId(),
                            batch.getPayPeriodStart(), batch.getPayPeriodEnd());

            BigDecimal unpaidDays = unpaidLeaves.stream()
                    .map(lr -> lr.getUnpaidDaysUsed().compareTo(BigDecimal.ZERO) > 0
                            ? lr.getUnpaidDaysUsed()
                            : lr.getNumberOfDays())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal unauthorizedDays = BigDecimal.ZERO;

            if (unpaidDays.compareTo(BigDecimal.ZERO) > 0 || unauthorizedDays.compareTo(BigDecimal.ZERO) > 0) {
                PayrollFlaggedEmployee pfe = new PayrollFlaggedEmployee();
                pfe.setPayrollBatch(batch);
                pfe.setEmployee(emp);
                pfe.setUnpaidLeaveDays(unpaidDays);
                pfe.setUnauthorizedAbsenceDays(unauthorizedDays);
                pfe.setFlagReason(unpaidDays.compareTo(BigDecimal.ZERO) > 0
                        ? "Unpaid leave: " + unpaidDays + " days"
                        : "Unauthorized absence: " + unauthorizedDays + " days");
                pfe.setBaseSalary(salary);
                // Use employee-specific working days (accounts for mid-month hire)
                pfe.setTotalWorkingDays(employeeWorkingDays);
                pfe.setDaysWorked(BigDecimal.valueOf(employeeWorkingDays).subtract(unpaidDays));
                pfe.setFlagAction(FlagAction.PENDING_REVIEW);
                pfe.setStatus(Status.ACTIVE);
                flaggedEmployeeRepository.save(pfe);
                flagged++;
            }
        }

        batch.setTotalHeadcount(includedCount);
        batch.setFlaggedCount(flagged);
        batch.setProcessedCount(includedCount - flagged);
        batch.setBatchStatus(PayrollBatchStatus.UNDER_REVIEW);
        batch = batchRepository.save(batch);

        return mapper.toDto(batch);
    }

    @Override
    public PayrollFlaggedEmployeeDto processFlaggedEmployee(UUID batchId, UUID flaggedId,
                                                              PayrollFlaggedEmployeeDto action) {
        PayrollFlaggedEmployee pfe = flaggedEmployeeRepository.findById(flaggedId)
                .orElseThrow(() -> new PayrollFlaggedEmployeeNotFoundException(flaggedId));

        if (action.getFlagAction() == FlagAction.WAIVED) {
            pfe.setFlagAction(FlagAction.WAIVED);
            pfe.setCalculatedSalary(pfe.getBaseSalary());
        } else if (action.getFlagAction() == FlagAction.PRORATED) {
            pfe.setFlagAction(FlagAction.PRORATED);
            BigDecimal adjusted = pfe.getBaseSalary()
                    .multiply(pfe.getDaysWorked())
                    .divide(BigDecimal.valueOf(pfe.getTotalWorkingDays()), 4, java.math.RoundingMode.HALF_UP);
            pfe.setCalculatedSalary(adjusted);
        }

        pfe.setActionAt(LocalDateTime.now());
        pfe.setActionReason(action.getActionReason());

        // actionById is derived server-side from the JWT in the controller.
        // It now holds the authUserId (JWT sub). Try to resolve to a CmEmployee
        // first (MANAGER case); if not found, store as actionByAuthUserId (SUPER_ADMIN case).
        if (action.getActionById() != null) {
            UUID authUserId = action.getActionById();
            UUID orgId = pfe.getPayrollBatch().getLegalEntity().getOrganizationId();
            CmEmployee actor = cmEmployeeRepository
                    .findByAuthUserIdAndOrganizationId(authUserId, orgId)
                    .orElse(null);
            if (actor != null) {
                pfe.setActionBy(actor);
                pfe.setActionByAuthUserId(null);
            } else {
                // SUPER_ADMIN not onboarded as employee — store authUserId directly
                pfe.setActionBy(null);
                pfe.setActionByAuthUserId(authUserId);
            }
        }

        pfe = flaggedEmployeeRepository.save(pfe);
        return mapper.toFlaggedDto(pfe);
    }

    @Override
    public PayrollBatchDto calculateSalaries(UUID batchId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));

        // Validate: no PENDING_REVIEW flagged employees remain
        List<PayrollFlaggedEmployee> pendingFlags = flaggedEmployeeRepository
                .findByPayrollBatchIdAndFlagAction(batchId, FlagAction.PENDING_REVIEW);
        if (!pendingFlags.isEmpty()) {
            throw new InvalidPayrollStateException(
                    "Cannot calculate salaries: " + pendingFlags.size()
                    + " employee(s) still pending review for batch " + batchId
                    + ". Process all flagged employees first.");
        }

        // Resolve jurisdiction from the legal entity's country
        Jurisdiction jurisdiction = TaxConfigurationServiceImpl.toJurisdiction(
                batch.getLegalEntity().getCountry());

        // Fetch tax configurations whose effective period overlaps with the pay period.
        // This ensures configs that expired before or start after the pay period are excluded.
        // Each config's tax is prorated by the overlap ratio (days config applies / total working days).
        List<TaxConfiguration> taxConfigs = taxConfigRepository
                .findActiveByEntityAndJurisdictionForPeriod(
                        batch.getLegalEntity().getId(), jurisdiction,
                        batch.getPayPeriodStart(), batch.getPayPeriodEnd());
        TaxCalculationStrategy taxStrategy = !taxConfigs.isEmpty()
                ? taxStrategyFactory.getStrategy(jurisdiction) : null;

        UUID batchOrgId = batch.getLegalEntity().getOrganizationId();

        // ── Fetch assignments whose tenure [hireDate, terminationDate] overlaps with the pay period ──
        List<CmEmployeeEntityAssignment> assignments = cmAssignmentRepository
                .findAssignmentsOverlappingPeriod(
                        batch.getLegalEntity().getId(),
                        batch.getPayPeriodStart(), batch.getPayPeriodEnd());

        // Build maps: employeeId → assignment, employeeId → PayrollDetails
        Map<UUID, CmEmployeeEntityAssignment> assignmentMap = new LinkedHashMap<>();
        Map<UUID, PayrollDetails> pdMap = new HashMap<>();
        for (CmEmployeeEntityAssignment a : assignments) {
            CmEmployee emp = a.getEmployee();
            assignmentMap.put(emp.getId(), a);
            payrollDetailsRepository.findByEmployeeId(emp.getId())
                    .ifPresent(pd -> pdMap.put(emp.getId(), pd));
        }

        // Build employee_id → PayrollFlaggedEmployee map for unpaid leave deductions
        List<PayrollFlaggedEmployee> allFlags = flaggedEmployeeRepository
                .findByPayrollBatchId(batchId);
        Map<UUID, PayrollFlaggedEmployee> flagMap = new HashMap<>();
        for (PayrollFlaggedEmployee pfe : allFlags) {
            if (pfe.getEmployee() != null) {
                flagMap.put(pfe.getEmployee().getId(), pfe);
            }
        }

        // Compute standard working days in the calendar month (used as the proration denominator)
        LocalDate monthStart = batch.getPayPeriodStart().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        int standardWorkingDays = countWorkingDays(monthStart, monthEnd);

        for (Map.Entry<UUID, CmEmployeeEntityAssignment> entry : assignmentMap.entrySet()) {
            CmEmployee emp = entry.getValue().getEmployee();
            CmEmployeeEntityAssignment assignment = entry.getValue();
            PayrollDetails pd = pdMap.get(emp.getId());
            BigDecimal baseSalary = pd != null ? pd.getBaseSalary() : BigDecimal.ZERO;
            String currency = pd != null ? pd.getSalaryCurrency() : "NPR";

            // ── Employee-specific proration based on tenure within the pay period ──
            // Effective start: the later of hireDate or payPeriodStart
            LocalDate effectiveStart = assignment.getHireDate().isAfter(batch.getPayPeriodStart())
                    ? assignment.getHireDate() : batch.getPayPeriodStart();
            // Effective end: the earlier of terminationDate or payPeriodEnd
            LocalDate effectiveEnd = assignment.getTerminationDate() != null
                    && assignment.getTerminationDate().isBefore(batch.getPayPeriodEnd())
                    ? assignment.getTerminationDate() : batch.getPayPeriodEnd();

            // Skip if no overlap (defensive)
            if (effectiveStart.isAfter(effectiveEnd)) {
                continue;
            }

            int employeeWorkingDays = countWorkingDays(effectiveStart, effectiveEnd);

            // Employee proration factor = employeeWorkingDays / standardWorkingDays.
            // For a full-month employee: employeeWorkingDays == standardWorkingDays → 1.0 (no change).
            // For a mid-month hire: employeeWorkingDays < standardWorkingDays → prorates salary.
            BigDecimal employeeProrationFactor = BigDecimal.valueOf(employeeWorkingDays)
                    .divide(BigDecimal.valueOf(standardWorkingDays), 4, RoundingMode.HALF_UP);

            BigDecimal proratedBase = baseSalary.multiply(employeeProrationFactor)
                    .setScale(4, RoundingMode.HALF_UP);

            // Resolve effective gross salary from flagged employee record (if any)
            PayrollFlaggedEmployee pfe = flagMap.get(emp.getId());
            // grossBeforeDeductions = full base (mid-month adjusted) displayed as the gross salary.
            // effectiveGross = taxable earnings after unpaid leave reduction (used for tax calc).
            BigDecimal grossBeforeDeductions = proratedBase;
            BigDecimal effectiveGross = proratedBase;
            BigDecimal unpaidDeduction = BigDecimal.ZERO;
            BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
            BigDecimal daysWorked = BigDecimal.valueOf(employeeWorkingDays);

            if (pfe != null) {
                unpaidLeaveDays = pfe.getUnpaidLeaveDays() != null
                        ? pfe.getUnpaidLeaveDays() : BigDecimal.ZERO;
                daysWorked = pfe.getDaysWorked() != null
                        ? pfe.getDaysWorked() : BigDecimal.valueOf(employeeWorkingDays);

                if (pfe.getFlagAction() == FlagAction.PRORATED) {
                    // calculatedSalary from processFlaggedEmployee = baseSalary × daysWorked / totalWorkingDays.
                    // Scale by employeeProrationFactor to express relative to the standard month.
                    BigDecimal calculatedSalaryScaled = pfe.getCalculatedSalary() != null
                            ? pfe.getCalculatedSalary().multiply(employeeProrationFactor)
                                    .setScale(4, RoundingMode.HALF_UP)
                            : proratedBase;
                    // effectiveGross = taxable earnings (reduced for unpaid leave)
                    effectiveGross = calculatedSalaryScaled;
                    // unpaidDeduction = the value of unpaid leave days shown as a separate deduction
                    unpaidDeduction = grossBeforeDeductions.subtract(calculatedSalaryScaled);
                }
                // WAIVED: effectiveGross stays as proratedBase (full), no deduction
            }

            Payslip payslip = new Payslip();
            payslip.setPayrollBatch(batch);
            payslip.setOrganizationId(batchOrgId);
            payslip.setEntityAssignmentId(pd != null ? pd.getEntityAssignmentId() : null);
            payslip.setEmployee(emp);
            payslip.setPayPeriodStart(batch.getPayPeriodStart());
            payslip.setPayPeriodEnd(batch.getPayPeriodEnd());
            payslip.setPaymentDate(batch.getPaymentDate());
            // Store employee-specific working days rather than batch-level
            payslip.setTotalWorkingDays(employeeWorkingDays);
            payslip.setDaysWorked(daysWorked);
            payslip.setPaidLeaveDays(BigDecimal.ZERO);
            payslip.setSickLeaveDays(BigDecimal.ZERO);
            payslip.setUnpaidLeaveDays(unpaidLeaveDays);
            // Gross salary = full base BEFORE unpaid leave deduction (deduction shown separately)
            payslip.setGrossSalary(grossBeforeDeductions);
            payslip.setCurrencyCode(currency);
            payslip.setStatus(Status.ACTIVE);

            // ── Tax calculation with date-aware config filtering ───────────────
            // Each TaxConfiguration has an effectiveFrom/effectiveTo range. The query
            // above already filtered configs whose effective period overlaps with the
            // pay period. Now we prorate each config's tax by its overlap ratio within
            // the employee's working days period.
            BigDecimal taxDeductions = BigDecimal.ZERO;
            List<PayslipLineItemDto> taxItems = new ArrayList<>();
            if (taxStrategy != null && !taxConfigs.isEmpty()) {
                // The strategy is called per-config so we can prorate results independently
                for (TaxConfiguration config : taxConfigs) {
                    BigDecimal overlapRatio = computeOverlapRatio(config,
                            effectiveStart, batch.getPayPeriodEnd(), employeeWorkingDays);

                    // Skip configs with no overlap
                    if (overlapRatio.compareTo(BigDecimal.ZERO) <= 0) continue;

                    // Call strategy with full effectiveGross (for correct annualization
                    // in PROGRESSIVE method), then prorate the resulting tax amounts
                    List<PayslipLineItemDto> configItems = taxStrategy.calculate(
                            emp, effectiveGross, List.of(config), BigDecimal.ZERO);

                    for (PayslipLineItemDto item : configItems) {
                        // Prorate the tax amount by the config's overlap ratio
                        BigDecimal proratedAmount = item.getAmount()
                                .multiply(overlapRatio)
                                .setScale(4, RoundingMode.HALF_UP);
                        item.setAmount(proratedAmount);

                        taxItems.add(item);
                        if (item.getLineItemType() == LineItemType.DEDUCTION) {
                            taxDeductions = taxDeductions.add(proratedAmount);
                        }
                    }
                }
            }

            // Total deductions = unpaid leave deduction + tax deductions
            BigDecimal totalDeductions = unpaidDeduction.add(taxDeductions);

            // Net = grossBeforeDeductions - all deductions
            BigDecimal netSalary = grossBeforeDeductions.subtract(totalDeductions);
            payslip.setTotalDeductions(totalDeductions);
            payslip.setNetSalary(netSalary);

            // Persist payslip with all NOT NULL fields populated
            payslip = payslipRepository.save(payslip);

            // Create earning line item (full base salary before deductions)
            PayslipLineItem baseSalaryItem = PayslipLineItem.builder()
                    .payslip(payslip)
                    .lineItemType(LineItemType.EARNING)
                    .lineItemCode("BASE_SALARY")
                    .lineItemDescription("Base Salary")
                    .amount(grossBeforeDeductions)
                    .currencyCode(currency)
                    .displayOrder(1)
                    .status(Status.ACTIVE)
                    .build();
            payslipLineItemRepository.save(baseSalaryItem);

            int order = 5;

            // Create unpaid leave deduction line item (if applicable)
            if (unpaidDeduction.compareTo(BigDecimal.ZERO) > 0) {
                PayslipLineItem unpaidItem = PayslipLineItem.builder()
                        .payslip(payslip)
                        .lineItemType(LineItemType.DEDUCTION)
                        .lineItemCode("UNPAID_LEAVE_DEDUCTION")
                        .lineItemDescription("Unpaid Leave Deduction (" + unpaidLeaveDays + " days)")
                        .amount(unpaidDeduction)
                        .currencyCode(currency)
                        .displayOrder(order++)
                        .status(Status.ACTIVE)
                        .build();
                payslipLineItemRepository.save(unpaidItem);
            }

            // Create tax deduction line items
            if (!taxItems.isEmpty()) {
                for (PayslipLineItemDto item : taxItems) {
                    PayslipLineItem lineItem = PayslipLineItem.builder()
                            .payslip(payslip)
                            .lineItemType(item.getLineItemType())
                            .lineItemCode(item.getLineItemCode())
                            .lineItemDescription(item.getLineItemDescription())
                            .amount(item.getAmount())
                            .currencyCode(item.getCurrencyCode())
                            .displayOrder(order++)
                            .status(Status.ACTIVE)
                            .build();
                    payslipLineItemRepository.save(lineItem);
                }
            }
        }

        // Update batch summary
        List<Payslip> payslips = payslipRepository.findByPayrollBatchId(batchId);
        BigDecimal totalGross = payslips.stream().map(Payslip::getGrossSalary).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDed = payslips.stream().map(Payslip::getTotalDeductions).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNet = payslips.stream().map(Payslip::getNetSalary).reduce(BigDecimal.ZERO, BigDecimal::add);

        batch.setTotalGrossSalary(totalGross);
        batch.setTotalDeductions(totalDed);
        batch.setTotalNetPayout(totalNet);
        batch.setProcessedCount(payslips.size());
        batch = batchRepository.save(batch);

        return mapper.toDto(batch, true);
    }

    @Override
    public PayrollBatchDto generatePayslips(UUID batchId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));
        // PDF generation handled by PayslipService
        return mapper.toDto(batch, true);
    }

    @Override
    public PayrollBatchDto approvePayroll(UUID batchId, ApprovePayrollRequest approval) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));

        if (batch.getBatchStatus() != PayrollBatchStatus.UNDER_REVIEW) {
            throw new InvalidPayrollStateException(batchId, batch.getBatchStatus(), PayrollBatchStatus.APPROVED);
        }

        if (batch.getPayslips() == null || batch.getPayslips().isEmpty()) {
            throw new PayslipsNotGeneratedException(
                    "Cannot approve payroll batch: payslips have not been generated. " +
                    "Please generate payslips before approval.");
        }

        UUID journalId = UUID.randomUUID();
        batch.setBatchStatus(PayrollBatchStatus.APPROVED);
        batch.setApprovedAt(LocalDateTime.now());
        batch.setJournalId(journalId);
        batch = batchRepository.save(batch);

        // Create ledger entries (PAY-03)
        createLedgerEntries(batch, journalId);

        outboxService.createEvent(batch, PayrollBatchEventType.PAYROLL_APPROVED,
                "{\"batchId\":\"" + batchId + "\",\"journalId\":\"" + journalId + "\"}", null);

        return mapper.toDto(batch, true);
    }

    @Override
    public PayrollBatchDto rejectPayroll(UUID batchId, RejectPayrollRequest rejection) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));

        if (batch.getPayslips() == null || batch.getPayslips().isEmpty()) {
            throw new PayslipsNotGeneratedException(
                    "Cannot reject payroll batch: payslips have not been generated. " +
                    "Please generate payslips before rejecting.");
        }

        batch.setBatchStatus(PayrollBatchStatus.REJECTED);
        batch.setRejectionReason(rejection.getRejectionReason());
        batch = batchRepository.save(batch);

        outboxService.createEvent(batch, PayrollBatchEventType.PAYROLL_REJECTED,
                "{\"batchId\":\"" + batchId + "\"}", null);

        return mapper.toDto(batch);
    }

    @Override
    public void deletePayrollBatch(UUID batchId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));

        PayrollBatchStatus currentStatus = batch.getBatchStatus();
        if (currentStatus != PayrollBatchStatus.INITIATED && currentStatus != PayrollBatchStatus.REJECTED) {
            throw new InvalidPayrollStateException(batchId, currentStatus, PayrollBatchStatus.INITIATED);
        }

        batch.setStatus(Status.INACTIVE);
        batch.setBatchStatus(PayrollBatchStatus.REJECTED);
        batchRepository.save(batch);

        outboxService.createEvent(batch, PayrollBatchEventType.PAYROLL_DELETED,
                "{\"batchId\":\"" + batchId + "\"}", null);
    }

    @Override
    public PayrollBatchDto voidPayroll(UUID batchId, VoidPayrollRequest voidRequest) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));

        if (batch.getBatchStatus() != PayrollBatchStatus.APPROVED
                && batch.getBatchStatus() != PayrollBatchStatus.APPROVED_PENDING_PAYMENT) {
            throw new InvalidPayrollStateException(batchId, batch.getBatchStatus(), PayrollBatchStatus.VOIDED);
        }

        UUID reversalJournalId = UUID.randomUUID();
        // Create reversing entries
        List<PayrollLedgerEntry> originals = ledgerEntryRepository.findByJournalId(batch.getJournalId());
        for (PayrollLedgerEntry orig : originals) {
            PayrollLedgerEntry reversal = new PayrollLedgerEntry();
            reversal.setJournalId(reversalJournalId);
            reversal.setPayrollBatch(batch);
            reversal.setLegalEntity(batch.getLegalEntity());
            reversal.setAccountCode(orig.getAccountCode());
            reversal.setAccountDescription(orig.getAccountDescription());
            reversal.setEntrySide("DEBIT".equals(orig.getEntrySide()) ? "CREDIT" : "DEBIT");
            reversal.setAmount(orig.getAmount());
            reversal.setCurrencyCode(orig.getCurrencyCode());
            reversal.setIsReversal(true);
            reversal.setOriginalEntryId(orig.getId());
            reversal.setDescription("REVERSAL: " + orig.getDescription());
            reversal.setReferenceType("PAYROLL_REVERSAL");
            reversal.setReferenceId(batch.getId());
            reversal.setStatus(Status.ACTIVE);
            ledgerEntryRepository.save(reversal);
        }

        batch.setBatchStatus(PayrollBatchStatus.VOIDED);
        batch = batchRepository.save(batch);

        outboxService.createEvent(batch, PayrollBatchEventType.PAYROLL_VOIDED,
                "{\"batchId\":\"" + batchId + "\",\"reversalJournalId\":\"" + reversalJournalId + "\"}", null);

        return mapper.toDto(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollBatchDto getPayrollBatch(UUID batchId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));
        return mapper.toDto(batch, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollBatchDto> listAllPayrollBatches() {
        return batchRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(b -> mapper.toDto(b, false)).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollBatchDto> listPayrollBatchesByEntity(UUID legalEntityId) {
        return batchRepository.findByLegalEntityIdOrderByCreatedAtDesc(legalEntityId).stream()
                .map(b -> mapper.toDto(b, false)).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayrollBatchDto> listBatchesFiltered(
            UUID orgId,
            UUID legalEntityId,
            PayrollBatchStatus batchStatus,
            String currencyCode,
            LocalDate payPeriodFrom,
            LocalDate payPeriodTo,
            LocalDate paymentDateFrom,
            LocalDate paymentDateTo,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        Sort.Direction dir = "DESC".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        PageRequest pageable = PageRequest.of(page, size, Sort.by(dir, field));

        Page<PayrollBatch> resultPage = batchRepository.findAll(
                PayrollBatchRepository.filterSpec(
                        orgId, legalEntityId, batchStatus, currencyCode,
                        payPeriodFrom, payPeriodTo, paymentDateFrom, paymentDateTo),
                pageable);

        return PageResponse.of(resultPage.map(b -> mapper.toDto(b, false)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollFlaggedEmployeeDto> listFlaggedEmployees(UUID batchId) {
        return flaggedEmployeeRepository.findByPayrollBatchId(batchId).stream()
                .map(mapper::toFlaggedDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getFlaggedEmployeeLeaveRequests(UUID batchId, UUID flaggedId) {
        PayrollFlaggedEmployee pfe = flaggedEmployeeRepository.findById(flaggedId)
                .orElseThrow(() -> new PayrollFlaggedEmployeeNotFoundException(flaggedId));

        PayrollBatch batch = pfe.getPayrollBatch();
        List<LeaveRequest> unpaidLeaves = leaveRequestRepository
                .findUnpaidLeaveRequestsForPeriod(pfe.getEmployee().getId(),
                        batch.getPayPeriodStart(), batch.getPayPeriodEnd());

        return unpaidLeaves.stream()
                .map(leaveRequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollLedgerEntryDto> getLedgerEntries(UUID batchId) {
        return ledgerEntryRepository.findByPayrollBatchId(batchId).stream()
                .map(mapper::toLedgerDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollLedgerEntryDto> getLedgerEntriesByJournal(UUID journalId) {
        return ledgerEntryRepository.findByJournalId(journalId).stream()
                .map(mapper::toLedgerDto).collect(Collectors.toList());
    }

    // --- Helpers ---

    /**
     * Builds a map of cmEmployeeId → CmEmployeeEntityAssignment for the given
     * employees and legal entity, so hire dates can be resolved for
     * mid-month hire proration.
     */
    private Map<UUID, CmEmployeeEntityAssignment> buildAssignmentMap(
            List<CmEmployee> employees, UUID legalEntityId) {
        List<UUID> employeeIds = employees.stream()
                .map(CmEmployee::getId)
                .collect(Collectors.toList());
        if (employeeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return cmAssignmentRepository
                .findByEmployeeIdInAndLegalEntityId(employeeIds, legalEntityId)
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getEmployee().getId(),
                        Function.identity(),
                        (a, b) -> a)); // prefer first if duplicate (should not occur)
    }

    private void createLedgerEntries(PayrollBatch batch, UUID journalId) {
        List<Payslip> payslips = payslipRepository.findByPayrollBatchId(batch.getId());
        BigDecimal totalGross = payslips.stream().map(Payslip::getGrossSalary).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeductions = payslips.stream().map(Payslip::getTotalDeductions).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNet = payslips.stream().map(Payslip::getNetSalary).reduce(BigDecimal.ZERO, BigDecimal::add);

        // DEBIT: Salary Expense
        createLedger(batch, journalId, "SALARY_EXPENSE", "Salary Expense", "DEBIT", totalGross,
                "Salary expense for period " + batch.getPayPeriodStart() + " to " + batch.getPayPeriodEnd());

        // CREDIT: Employee Payable
        createLedger(batch, journalId, "EMPLOYEE_PAYABLE", "Employee Payable", "CREDIT", totalNet,
                "Net salaries payable for period " + batch.getPayPeriodStart());

        // CREDIT: Tax Withholding
        createLedger(batch, journalId, "TAX_WITHHOLDING_PAYABLE", "Tax Withholding Payable", "CREDIT",
                totalDeductions, "Tax withholdings for period " + batch.getPayPeriodStart());
    }

    private void createLedger(PayrollBatch batch, UUID journalId, String code, String desc,
                               String side, BigDecimal amount, String narrative) {
        PayrollLedgerEntry entry = new PayrollLedgerEntry();
        entry.setJournalId(journalId);
        entry.setPayrollBatch(batch);
        entry.setLegalEntity(batch.getLegalEntity());
        entry.setAccountCode(code);
        entry.setAccountDescription(desc);
        entry.setEntrySide(side);
        entry.setAmount(amount);
        entry.setCurrencyCode(batch.getCurrencyCode());
        entry.setIsReversal(false);
        entry.setDescription(narrative);
        entry.setReferenceType("PAYROLL");
        entry.setReferenceId(batch.getId());
        entry.setStatus(Status.ACTIVE);
        ledgerEntryRepository.save(entry);
    }

    /**
     * Computes how much of a tax configuration's effective period overlaps with
     * the employee's working period, expressed as a ratio of working days.
     *
     * <p>For example, a config effective June 1-15 with an employee working the
     * full month (22 working days) would return 15/22 ≈ 0.6818. The tax
     * calculated using this config is then multiplied by this ratio.</p>
     *
     * @param config      the tax configuration with effectiveFrom/effectiveTo
     * @param periodStart the employee's effective start date
     * @param periodEnd   the pay period end date
     * @param totalWorkingDays total working days for this employee in the period
     * @return overlap ratio between 0 and 1 (inclusive)
     */
    private BigDecimal computeOverlapRatio(TaxConfiguration config,
                                            LocalDate periodStart, LocalDate periodEnd,
                                            int totalWorkingDays) {
        if (totalWorkingDays <= 0) return BigDecimal.ZERO;

        LocalDate overlapStart = config.getEffectiveFrom().isAfter(periodStart)
                ? config.getEffectiveFrom() : periodStart;
        LocalDate overlapEnd = config.getEffectiveTo() != null
                && config.getEffectiveTo().isBefore(periodEnd)
                ? config.getEffectiveTo() : periodEnd;

        if (overlapStart.isAfter(overlapEnd)) {
            return BigDecimal.ZERO; // no overlap
        }

        int overlapWorkingDays = countWorkingDays(overlapStart, overlapEnd);
        return BigDecimal.valueOf(overlapWorkingDays)
                .divide(BigDecimal.valueOf(totalWorkingDays), 4, RoundingMode.HALF_UP);
    }

    private int countWorkingDays(LocalDate start, LocalDate end) {
        int days = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (current.getDayOfWeek().getValue() < 6) days++;
            current = current.plusDays(1);
        }
        return days;
    }
}
