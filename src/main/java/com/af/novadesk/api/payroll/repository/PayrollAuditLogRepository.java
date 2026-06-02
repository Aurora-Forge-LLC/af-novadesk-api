package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.PayrollAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayrollAuditLogRepository extends JpaRepository<PayrollAuditLog, UUID> {
    List<PayrollAuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId);
    List<PayrollAuditLog> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
