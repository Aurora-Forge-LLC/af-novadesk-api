package com.af.novadesk.api.finance.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/** Paginated list wrapper. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegalEntityPageDto {
    private List<LegalEntitySummaryDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}