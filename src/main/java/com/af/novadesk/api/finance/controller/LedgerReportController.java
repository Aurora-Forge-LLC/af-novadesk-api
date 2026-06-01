package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.api.LedgerReportApi;
import com.af.novadesk.api.finance.dto.EntityBalanceResponse;
import com.af.novadesk.api.finance.dto.LedgerReportResponse;
import com.af.novadesk.api.finance.dto.MultiEntityConsolidatedReport;
import com.af.novadesk.api.finance.service.EntityBalanceService;
import com.af.novadesk.api.finance.service.LedgerReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller implementing the {@link LedgerReportApi} contract (LLR-FIN-04.5).
 */
@RestController
public class LedgerReportController implements LedgerReportApi {

    private final LedgerReportService   ledgerReportService;
    private final EntityBalanceService   entityBalanceService;

    public LedgerReportController(
            LedgerReportService ledgerReportService,
            EntityBalanceService entityBalanceService
    ) {
        this.ledgerReportService   = ledgerReportService;
        this.entityBalanceService  = entityBalanceService;
    }

    @Override
    public ResponseEntity<ApiResponse<LedgerReportResponse>> getLedgerReport(
            UUID entityId,
            LocalDate startDate,
            LocalDate endDate,
            String currency,
            UUID accountId,
            int page,
            int size
    ) {
        LedgerReportResponse report = ledgerReportService.generateLedgerReport(
                entityId, startDate, endDate, currency, accountId, page, size);
        return ResponseBuilder.ok(report, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<MultiEntityConsolidatedReport>> getConsolidatedReport(
            List<UUID> entityIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        MultiEntityConsolidatedReport report = ledgerReportService.generateConsolidatedReport(
                entityIds, startDate, endDate);
        return ResponseBuilder.ok(report, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<EntityBalanceResponse>> getEntityBalance(String entityCode) {
        EntityBalanceResponse balance = entityBalanceService.getEntityBalance(entityCode);
        return ResponseBuilder.ok(balance, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
