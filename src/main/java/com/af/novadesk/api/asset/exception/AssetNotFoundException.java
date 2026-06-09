package com.af.novadesk.api.asset.exception;

import com.af.novadesk.api.finance.exception.NotFoundException;
import java.util.UUID;

public class AssetNotFoundException extends NotFoundException {
    public AssetNotFoundException(UUID id) {
        super("AST_001", "Asset not found: " + id);
    }
    public AssetNotFoundException(String serialNumber) {
        super("AST_001", "Asset not found with serial number: " + serialNumber);
    }
}
