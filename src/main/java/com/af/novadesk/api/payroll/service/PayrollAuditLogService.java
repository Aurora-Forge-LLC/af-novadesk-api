package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.constants.PayrollAuditAction;
import com.af.novadesk.api.payroll.dto.PayrollAuditLogDto;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for cross-cutting payroll audit logging.
 */
public interface PayrollAuditLogService {

    void log(PayrollAuditAction action, String entityType, UUID entityId,
             UUID performedByEmployeeId, String details, String changeSnapshot);

    List<PayrollAuditLogDto> getAuditLogs(String entityType, UUID entityId);
}
