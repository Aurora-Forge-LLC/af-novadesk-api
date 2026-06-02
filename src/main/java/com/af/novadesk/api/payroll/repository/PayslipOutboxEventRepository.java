package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.payroll.entity.PayslipOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayslipOutboxEventRepository extends JpaRepository<PayslipOutboxEvent, UUID> {
    List<PayslipOutboxEvent> findByOutboxEventStatusOrderByCreatedAtAsc(OutboxEventStatus status);
}
