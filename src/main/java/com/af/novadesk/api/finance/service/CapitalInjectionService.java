package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CapitalInjectionStatus;
import com.af.novadesk.api.finance.dto.CapitalInjectionDetailDto;
import com.af.novadesk.api.finance.dto.CapitalInjectionPageDto;
import com.af.novadesk.api.finance.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.dto.CapitalInjectionResponse;
import com.af.novadesk.api.finance.dto.CapitalInjectionStatusRequest;
import com.af.novadesk.api.finance.dto.InterEntityTransferDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service contract for Decentralised Funding & Capital Injection (LLR-FIN-02).
 */
public interface CapitalInjectionService {

    /**
     * Records a new capital injection with double-entry ledger postings (LLR-FIN-02.1).
     *
     * @param request validated capital injection request from the REST layer
     * @return response containing the created injection details and journal IDs
     */
    CapitalInjectionResponse createCapitalInjection(CapitalInjectionRequest request);

    /**
     * Lists capital injections for a given entity, paginated and ordered by funding date descending.
     *
     * @param entityCode legal entity code to scope the query
     * @param page       zero-based page index
     * @param size       page size
     * @return paginated list of capital injection summaries
     */
    CapitalInjectionPageDto listCapitalInjections(
            String entityCode,
            CapitalInjectionStatus injectionStatus,
            LocalDate fromDate, LocalDate toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String currencyLocal,
            int page, int size, String sortBy, String sortDir);

    /**
     * Returns full detail for a single capital injection, including ledger entries (LLR-FIN-02.2).
     *
     * @param id capital injection UUID
     * @return full detail DTO with associated ledger entries
     */
    CapitalInjectionDetailDto getCapitalInjectionDetail(UUID id);

    /**
     * Updates the lifecycle status of a capital injection (LLR-FIN-02).
     *
     * @param id      capital injection UUID
     * @param request status update request
     */
    void updateCapitalInjectionStatus(UUID id, CapitalInjectionStatusRequest request);

    /**
     * Returns inter-entity transfer correlation details for reconciliation (LLR-FIN-02.4).
     *
     * @param transferId UUID shared by the four ledger legs of the transfer
     * @return correlation details linking both entity ledgers
     */
    InterEntityTransferDto getInterEntityTransfer(UUID transferId);
}
