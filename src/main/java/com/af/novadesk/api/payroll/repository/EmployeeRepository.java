package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByAuthUserIdAndLegalEntityId(UUID authUserId, UUID legalEntityId);
    List<Employee> findByLegalEntityId(UUID legalEntityId);
    List<Employee> findByManagerId(UUID managerId);
}
