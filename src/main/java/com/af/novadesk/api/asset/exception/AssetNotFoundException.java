package com.af.novadesk.api.asset.exception;

import com.af.novadesk.api.finance.exception.NotFoundException;
import java.util.UUID;

public class AssetNotFoundException extends NotFoundException {
    public AssetNotFoundException(UUID id) {
        super("Asset not found: " + id);
    }
    public AssetNotFoundException(String serialNumber) {
        super("Asset not found with serial number: " + serialNumber);
    }
}
