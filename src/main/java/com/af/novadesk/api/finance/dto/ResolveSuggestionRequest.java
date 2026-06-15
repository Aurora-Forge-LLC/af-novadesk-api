package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to accept or reject a suggested match")
public class ResolveSuggestionRequest {

    @NotNull(message = "Action is required")
    @Schema(description = "Action: ACCEPT or REJECT", example = "ACCEPT")
    private String action;

    public boolean isAccept() {
        return "ACCEPT".equalsIgnoreCase(action);
    }

    public boolean isReject() {
        return "REJECT".equalsIgnoreCase(action);
    }
}
