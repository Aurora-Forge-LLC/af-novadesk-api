package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.TaxSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaxSlabRepository extends JpaRepository<TaxSlab, UUID> {
    List<TaxSlab> findByTaxConfigurationIdOrderBySlabOrderAsc(UUID taxConfigurationId);
}
