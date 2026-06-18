package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.WriteOffReason;
import com.af.novadesk.api.asset.constants.WriteOffStatus;
import com.af.novadesk.api.asset.entity.AssetWriteOff;
import lombok.Value;

import java.util.UUID;

/** Lightweight response returned when a write-off is requested. */
@Value
public class WriteOffSummaryDto {
    UUID          id;
    UUID          assetId;
    WriteOffStatus writeOffStatus;
    WriteOffReason reason;
    String        auditNotes;

    public static WriteOffSummaryDto from(AssetWriteOff w) {
        return new WriteOffSummaryDto(
                w.getId(),
                w.getAsset().getId(),
                w.getWriteOffStatus(),
                w.getReason(),
                w.getAuditNotes()
        );
    }
}
