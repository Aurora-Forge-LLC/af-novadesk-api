package com.af.novadesk.api.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response for bank transaction list queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransactionPageDto {

    private List<BankTransactionDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
