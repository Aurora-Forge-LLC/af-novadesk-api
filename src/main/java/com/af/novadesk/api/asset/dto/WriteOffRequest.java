package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.WriteOffAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WriteOffRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 1000)
    private String reason;

    /** Populated only when approving — executive selects the action. */
    private WriteOffAction action;

    @Size(max = 1000)
    private String auditNotes;
}
