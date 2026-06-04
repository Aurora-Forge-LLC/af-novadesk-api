package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.entity.AssetReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetReturnRepository extends JpaRepository<AssetReturn, UUID> {

    Optional<AssetReturn> findByAssignmentId(UUID assignmentId);
}
