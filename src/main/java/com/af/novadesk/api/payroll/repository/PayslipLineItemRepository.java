package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.constants.LineItemType;
import com.af.novadesk.api.payroll.entity.PayslipLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayslipLineItemRepository extends JpaRepository<PayslipLineItem, UUID> {
    List<PayslipLineItem> findByPayslipIdOrderByDisplayOrderAsc(UUID payslipId);
    List<PayslipLineItem> findByPayslipIdAndLineItemType(UUID payslipId, LineItemType lineItemType);
}
