package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.constants.LeaveRequestEventType;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import com.af.novadesk.api.payroll.entity.LeaveRequestOutboxEvent;

import java.util.UUID;

/**
 * Outbox service for LeaveRequest aggregate events (Transactional Outbox Pattern).
 */
public interface LeaveRequestOutboxService {
    LeaveRequestOutboxEvent createEvent(LeaveRequest leaveRequest,
                                         LeaveRequestEventType eventType,
                                         String payload,
                                         UUID triggeredByAuthUserId);
}
