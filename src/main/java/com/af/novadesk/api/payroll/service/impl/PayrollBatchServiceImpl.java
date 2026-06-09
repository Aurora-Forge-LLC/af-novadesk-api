package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.payroll.repository.PayrollDetailsRepository;
import com.af.novadesk.api.payroll.constants.*;
import com.af.novadesk.api.payroll.dto.*;
import com.af.novadesk.api.payroll.entity.*;
import com.af.novadesk.api.payroll.exception.*;
import com.af.novadesk.api.payroll.mapper.PayrollBatchMapper;
import com.af.novadesk.api.payroll.repository.*;
import com.af.novadesk.api.payroll.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PayrollBatchServiceImpl implements PayrollBatchService {

    private final PayrollBatchRepository batchRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollFlaggedEmployeeRepository flaggedEmployeeRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipLineItemRepository payslipLineItemRepository;
    private final PayrollLedgerEntryRepository ledgerEntryRepository;
    private final TaxConfigurationRepository taxConfigRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final CmEmployeeRepository cmEmployeeRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final PayrollBatchMapper mapper;
    private final PayrollBatchOutboxService outboxService;
    private final TaxCalculationStrategyFactory taxStrategyFactory;

    public PayrollBatchServiceImpl(PayrollBatchRepository batchRepository,
                                   EmployeeRepository employeeRepository,
                                   PayrollFlaggedEmployeeRepository flaggedEmployeeRepository,
                                   PayslipRepository payslipRepository,
                                   PayslipLineItemRepository payslipLineItemRepository,
                                   PayrollLedgerEntryRepository ledgerEntryRepository,
                                   TaxConfigurationRepository taxConfigRepository,
                                   LegalEntityRepository legalEntityRepository,
                                   CmEmployeeRepository cmEmployeeRepository,
                                   PayrollDetailsRepository payrollDetailsRepository,
                                   PayrollBatchMapper mapper,
                                   PayrollBatchOutboxService outboxService,
                                   TaxCalculationStrategyFactory taxStrategyFactory) {
        this.batchRepository = batchRepository;
        this.employeeRepository = employeeRepository;
        this.flaggedEmployeeRepository = flaggedEmployeeRepository;
        this.payslipRepository = payslipRepository;
        this.payslipLineItemRepository = payslipLineItemRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.taxConfigRepository = taxConfigRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.cmEmployeeRepository = cmEmployeeRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.mapper = mapper;
        this.outboxService = outboxService;
        this.taxStrategyFactory = taxStrategyFactory;
    }

    @Override
    public PayrollBatchDto initiatePayroll(PayrollBatchDto request) {
        LegalEntity entity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new RuntimeException("Legal entity not found: " + request.getLegalEntityId()));

        // Check no existing batch for same period
        batchRepository.findByLegalEntityIdAndPayPeriodStartAndPayPeriodEnd(
                entity.getId(), request.getPayPeriodStart(), request.getPayPeriodEnd())
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

        List<Employee> employees = employeeRepository.findByLegalEntityId(batch.getLegalEntity().getId());
        int totalWorkingDays = countWorkingDays(batch.getPayPeriodStart(), batch.getPayPeriodEnd());
        int flagged = 0;

        // Build salary map: Employee.id → PayrollDetails (via cm_employee_id)
        Map<UUID, PayrollDetails> payrollDetailsMap = new HashMap<>();
        for (Employee emp : employees) {
            if (emp.getCmEmployeeId() != null) {
                payrollDetailsRepository.findByEmployeeId(emp.getCmEmployeeId())
                        .ifPresent(pd -> payrollDetailsMap.put(emp.getId(), pd));
            }
        }

        for (Employee emp : employees) {
            PayrollDetails pd = payrollDetailsMap.get(emp.getId());
            BigDecimal salary = pd != null ? pd.getBaseSalary() : BigDecimal.ZERO;

            BigDecimal unpaidDays = BigDecimal.ZERO;
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
                pfe.setTotalWorkingDays(totalWorkingDays);
                pfe.setDaysWorked(BigDecimal.valueOf(totalWorkingDays).subtract(unpaidDays));
                pfe.setFlagAction(FlagAction.PENDING_REVIEW);
                pfe.setStatus(Status.ACTIVE);
                flaggedEmployeeRepository.save(pfe);
                flagged++;
            }
        }

        batch.setTotalHeadcount(employees.size());
        batch.setFlaggedCount(flagged);
        batch.setProcessedCount(employees.size() - flagged);
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
        if (action.getActionById() != null) {
            Employee actor = employeeRepository.findById(action.getActionById()).orElse(null);
            pfe.setActionBy(actor);
        }

        pfe = flaggedEmployeeRepository.save(pfe);
        return mapper.toFlaggedDto(pfe);
    }

    @Override
    public PayrollBatchDto calculateSalaries(UUID batchId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));

        TaxConfiguration taxConfig = taxConfigRepository.findByLegalEntityIdAndJurisdiction(
                batch.getLegalEntity().getId(), Jurisdiction.NEPAL).stream().findFirst().orElse(null);
        TaxCalculationStrategy taxStrategy = taxConfig != null
                ? taxStrategyFactory.getStrategy(taxConfig.getJurisdiction()) : null;

        List<Employee> employees = employeeRepository.findByLegalEntityId(batch.getLegalEntity().getId());
        UUID batchOrgId = batch.getLegalEntity().getOrganizationId();

        // Build Employee.id → PayrollDetails map for salary and entity assignment lookup
        Map<UUID, PayrollDetails> pdMap = new HashMap<>();
        for (Employee emp : employees) {
            if (emp.getCmEmployeeId() != null) {
                payrollDetailsRepository.findByEmployeeId(emp.getCmEmployeeId())
                        .ifPresent(pd -> pdMap.put(emp.getId(), pd));
            }
        }

        for (Employee emp : employees) {
            PayrollDetails pd = pdMap.get(emp.getId());
            BigDecimal grossSalary = pd != null ? pd.getBaseSalary() : BigDecimal.ZERO;
            Payslip payslip = new Payslip();
            payslip.setPayrollBatch(batch);
            payslip.setOrganizationId(batchOrgId);
            payslip.setEntityAssignmentId(pd != null ? pd.getEntityAssignmentId() : null);
            payslip.setEmployee(emp);
            payslip.setPayPeriodStart(batch.getPayPeriodStart());
            payslip.setPayPeriodEnd(batch.getPayPeriodEnd());
            payslip.setPaymentDate(batch.getPaymentDate());
            payslip.setTotalWorkingDays(countWorkingDays(batch.getPayPeriodStart(), batch.getPayPeriodEnd()));
            payslip.setDaysWorked(BigDecimal.valueOf(payslip.getTotalWorkingDays()));
            payslip.setPaidLeaveDays(BigDecimal.ZERO);
            payslip.setSickLeaveDays(BigDecimal.ZERO);
            payslip.setUnpaidLeaveDays(BigDecimal.ZERO);
            payslip.setGrossSalary(grossSalary);
            payslip.setCurrencyCode(pd != null ? pd.getSalaryCurrency() : "NPR");
            payslip.setStatus(Status.ACTIVE);

            // Create earning line item
            payslip = payslipRepository.save(payslip);
            PayslipLineItem baseSalaryItem = PayslipLineItem.builder()
                    .payslip(payslip)
                    .lineItemType(LineItemType.EARNING)
                    .lineItemCode("BASE_SALARY")
                    .lineItemDescription("Base Salary")
                    .amount(grossSalary)
                    .currencyCode(pd != null ? pd.getSalaryCurrency() : "NPR")
                    .displayOrder(1)
                    .status(Status.ACTIVE)
                    .build();
            payslipLineItemRepository.save(baseSalaryItem);

            // Calculate taxes
            BigDecimal totalDeductions = BigDecimal.ZERO;
            if (taxStrategy != null && taxConfig != null) {
                List<PayslipLineItemDto> taxItems = taxStrategy.calculate(emp, grossSalary, taxConfig, BigDecimal.ZERO);
                int order = 10;
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
                    if (item.getLineItemType() == LineItemType.DEDUCTION) {
                        totalDeductions = totalDeductions.add(item.getAmount());
                    }
                }
            }

            BigDecimal netSalary = grossSalary.subtract(totalDeductions);
            payslip.setTotalDeductions(totalDeductions);
            payslip.setNetSalary(netSalary);
            payslipRepository.save(payslip);
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
    public PayrollBatchDto approvePayroll(UUID batchId, PayrollBatchDto approval) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));

        if (batch.getBatchStatus() != PayrollBatchStatus.UNDER_REVIEW) {
            throw new InvalidPayrollStateException(batchId, batch.getBatchStatus(), PayrollBatchStatus.APPROVED);
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
    public PayrollBatchDto rejectPayroll(UUID batchId, PayrollBatchDto rejection) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PayrollBatchNotFoundException(batchId));

        batch.setBatchStatus(PayrollBatchStatus.REJECTED);
        batch.setRejectionReason(rejection.getRejectionReason());
        batch = batchRepository.save(batch);

        outboxService.createEvent(batch, PayrollBatchEventType.PAYROLL_REJECTED,
                "{\"batchId\":\"" + batchId + "\"}", null);

        return mapper.toDto(batch);
    }

    @Override
    public PayrollBatchDto voidPayroll(UUID batchId, PayrollBatchDto voidRequest) {
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
    public List<PayrollFlaggedEmployeeDto> listFlaggedEmployees(UUID batchId) {
        return flaggedEmployeeRepository.findByPayrollBatchId(batchId).stream()
                .map(mapper::toFlaggedDto).collect(Collectors.toList());
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
