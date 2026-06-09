package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.PayrollDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollDetailsRepository extends JpaRepository<PayrollDetails, UUID> {

    Optional<PayrollDetails> findByEmployeeId(UUID employeeId);

    boolean existsByEmployeeId(UUID employeeId);
}
