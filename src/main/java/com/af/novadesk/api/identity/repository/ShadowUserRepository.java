package com.af.novadesk.api.identity.repository;

import com.af.novadesk.api.identity.entity.ShadowUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link ShadowUser} entity.
 */
@Repository
public interface ShadowUserRepository extends JpaRepository<ShadowUser, UUID> {

    Optional<ShadowUser> findByAuthUserId(UUID authUserId);

    boolean existsByAuthUserId(UUID authUserId);

    List<ShadowUser> findAllByOrganizationId(UUID organizationId);

    Optional<ShadowUser> findByEmail(String email);
}