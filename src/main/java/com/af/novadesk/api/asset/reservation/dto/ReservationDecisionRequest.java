package com.af.novadesk.api.asset.reservation.dto;

import com.af.novadesk.api.asset.reservation.constants.ReservationConstants;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ReservationDecisionRequest {

    /** cm_employees.id of the ops approver on record for this decision. */
    private UUID approverId;

    @Size(max = ReservationConstants.MAX_DECISION_NOTES_LENGTH,
          message = "Decision notes must not exceed 500 characters")
    private String decisionNotes;
}
