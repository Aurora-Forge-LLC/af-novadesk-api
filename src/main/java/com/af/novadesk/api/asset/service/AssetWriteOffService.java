package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.dto.AssetDto;
import com.af.novadesk.api.asset.dto.WriteOffRequest;
import com.af.novadesk.api.asset.entity.AssetWriteOff;

import java.util.UUID;

public interface AssetWriteOffService {

    /** IT admin raises a write-off request for a lost/unrecoverable asset. */
    AssetWriteOff requestWriteOff(UUID assetId, WriteOffRequest request);

    /** Executive approves the write-off with a chosen action. */
    AssetDto approveWriteOff(UUID writeOffId, WriteOffRequest request);

    /** Executive rejects the write-off request. */
    AssetWriteOff rejectWriteOff(UUID writeOffId, String reason);
}
