package com.af.novadesk.api.asset.exception;

import com.af.novadesk.api.finance.exception.FinanceBaseException;
import java.util.UUID;

public class InvalidAssetStateException extends FinanceBaseException {
    public InvalidAssetStateException(UUID assetId, String currentStatus, String operation) {
        super("AST_003",
              "Cannot '" + operation + "' asset " + assetId +
              " — current status is '" + currentStatus + "'");
    }
}
