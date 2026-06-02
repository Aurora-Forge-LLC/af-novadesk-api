package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.constants.PayslipEventType;
import com.af.novadesk.api.payroll.entity.Payslip;
import com.af.novadesk.api.payroll.entity.PayslipOutboxEvent;

import java.util.UUID;

/**
 * Outbox service for Payslip aggregate events.
 */
public interface PayslipOutboxService {
    PayslipOutboxEvent createEvent(Payslip payslip,
                                    PayslipEventType eventType,
                                    String payload,
                                    UUID triggeredByAuthUserId);
}
