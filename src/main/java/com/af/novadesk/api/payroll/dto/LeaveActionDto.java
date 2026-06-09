package com.af.novadesk.api.payroll.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for leave request state-change actions (approve, reject, modify).
 * Only carries the approver comment — the leave request ID is supplied as a path variable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payload for leave request state-change actions (approve, reject, request modification)")
public class LeaveActionDto {

    @Size(max = 500, message = "Comment must be at most 500 characters")
    @Schema(description = "Optional comment from the approver", example = "Approved as planned")
    private String approverComment;
}
