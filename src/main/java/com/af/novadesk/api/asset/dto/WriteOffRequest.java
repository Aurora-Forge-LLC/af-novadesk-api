package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.WriteOffAction;
import com.af.novadesk.api.asset.constants.WriteOffReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WriteOffRequest {

    @NotNull(message = "Reason is required")
    private WriteOffReason reason;

    /** Populated only when approving — executive selects the action. */
    private WriteOffAction action;

    @Size(max = 1000)
    private String auditNotes;
}
