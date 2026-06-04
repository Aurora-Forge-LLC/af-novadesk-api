package com.af.novadesk.api.asset.service;

import com.af.novadesk.api.asset.dto.AssetDto;
import com.af.novadesk.api.asset.dto.AssetReturnRequest;
import com.af.novadesk.api.asset.dto.CustodyTransferDto;

import java.util.List;
import java.util.UUID;

public interface AssetReturnService {

    /** Record the physical return of an asset from an employee. Updates asset status to RETURNED. */
    AssetDto recordReturn(UUID assetId, AssetReturnRequest request);

    /** Full chain-of-custody history for an asset. */
    List<CustodyTransferDto> getCustodyHistory(UUID assetId);
}
