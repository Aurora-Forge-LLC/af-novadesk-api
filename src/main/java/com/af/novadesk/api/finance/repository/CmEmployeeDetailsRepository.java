package com.af.novadesk.api.common.repository;

import com.af.novadesk.api.common.entity.CmEmployeeDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmEmployeeDetailsRepository extends JpaRepository<CmEmployeeDetails, UUID> {

    Optional<CmEmployeeDetails> findByEmployeeId(UUID employeeId);

    boolean existsByEmployeeId(UUID employeeId);
}
