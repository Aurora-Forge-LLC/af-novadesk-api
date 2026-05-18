package com.af.novadesk.api.common.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
/**
 * Metadata container for API responses with pagination and error details.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional metadata for API responses")
public class ApiResponseMetadata {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Pagination information for list responses")
    private PaginationInfo pagination;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "List of validation or business logic errors")
    private List<ErrorDetail> errors;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Request ID for tracing")
    private String traceId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Execution time in milliseconds")
    private Long executionTimeMs;
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Pagination information")
    public static class PaginationInfo {
        @Schema(description = "0-based current page number")
        private int page;
        @Schema(description = "Number of items per page")
        private int size;
        @Schema(description = "Total number of items")
        private long total;
        @Schema(description = "Total number of pages")
        private int totalPages;
        @Schema(description = "Whether this is the first page")
        private boolean first;
        @Schema(description = "Whether this is the last page")
        private boolean last;
    }
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual error detail")
    public static class ErrorDetail {
        @Schema(description = "Field name that caused the error")
        private String field;
        @Schema(description = "Error message")
        private String message;
        @Schema(description = "Error code")
        private String code;
    }
}
