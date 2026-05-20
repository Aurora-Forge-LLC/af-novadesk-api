package com.af.novadesk.api.identity.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ShadowUser (outbound only — created internally from JWT claims).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShadowUserDto {
    private UUID id;
    private UUID authUserId;
    private UUID organizationId;
    private String email;
    private String displayName;
    private LocalDateTime lastSyncedAt;
}