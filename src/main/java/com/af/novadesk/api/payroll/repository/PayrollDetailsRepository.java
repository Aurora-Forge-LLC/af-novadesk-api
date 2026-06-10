package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.PayrollDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PayrollDetails}.
 *
 * <p>One payroll-details record per employee, keyed by {@code employeeId}
 * (loose UUID reference to {@code cm_employees.id}).</p>
 */
@Repository
public interface PayrollDetailsRepository extends JpaRepository<PayrollDetails, UUID> {

    Optional<PayrollDetails> findByEmployeeId(UUID employeeId);

    boolean existsByEmployeeId(UUID employeeId);
}
