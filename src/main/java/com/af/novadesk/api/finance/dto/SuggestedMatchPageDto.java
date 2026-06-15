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
@Schema(description = "Paginated list of suggested matches")
public class SuggestedMatchPageDto {

    @Schema(description = "List of suggestions for the current page")
    private List<SuggestedMatchDto> content;

    @Schema(description = "Zero-based page number")
    private int page;

    @Schema(description = "Page size")
    private int size;

    @Schema(description = "Total number of suggestions across all pages")
    private long totalElements;

    @Schema(description = "Total number of pages")
    private int totalPages;
}
