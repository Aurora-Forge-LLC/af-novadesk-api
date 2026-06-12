package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated bank transaction list (unmatched queue)")
public class BankTransactionPageDto {

    @Schema(description = "Transaction list for current page")
    private List<BankTransactionDto> content;

    @Schema(description = "Zero-based page number")
    private int page;

    @Schema(description = "Page size")
    private int size;

    @Schema(description = "Total transactions matching filter")
    private long totalElements;

    @Schema(description = "Total pages")
    private int totalPages;
}
