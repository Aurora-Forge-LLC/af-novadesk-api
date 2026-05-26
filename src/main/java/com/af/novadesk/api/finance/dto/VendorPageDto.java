package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated list wrapper for {@link VendorDto}.
 *
 * <p>Returned by {@code GET /api/v1/expense/vendors}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated vendor list response")
public class VendorPageDto {

    @Schema(description = "Vendors on the current page")
    private List<VendorDto> content;

    @Schema(description = "Current page number (0-based)", example = "0")
    private int page;

    @Schema(description = "Number of records per page", example = "20")
    private int size;

    @Schema(description = "Total number of vendors matching the query", example = "42")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "3")
    private int totalPages;
}
