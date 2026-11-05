package com.af.novadesk.api.maintenance.exception;

import com.af.novadesk.api.finance.exception.NotFoundException;

import java.util.UUID;

public class MaintenanceRequestNotFoundException extends NotFoundException {
    public MaintenanceRequestNotFoundException(UUID id) {
        super("MNT_001", "Maintenance request not found: " + id);
    }
}
