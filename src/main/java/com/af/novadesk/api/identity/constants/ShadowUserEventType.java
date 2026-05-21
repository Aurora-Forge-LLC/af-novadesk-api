package com.af.novadesk.api.identity.constants;


import com.af.novadesk.api.identity.entity.ShadowUser;

/**
 * Domain events produced by the {@link ShadowUser} aggregate.
 */
public enum ShadowUserEventType {

    /**
     * Fired when a ShadowUser record is first created from a JWT claim
     * (i.e. the user's first authenticated request to the Finance module).
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code shadowUserId}   – UUID PK of the new ShadowUser record</li>
     *   <li>{@code authUserId}     – JWT {@code sub} claim</li>
     *   <li>{@code organizationId} – JWT {@code organizationId} claim</li>
     *   <li>{@code email}          – JWT {@code email} claim</li>
     *   <li>{@code displayName}    – JWT name claim, may be null</li>
     *   <li>{@code syncedAt}       – ISO-8601 datetime of the sync</li>
     * </ul>
     * </p>
     */
    SHADOW_USER_CREATED,

    /**
     * Fired when cached claims on an existing ShadowUser record change
     * (e.g. email update propagated through a re-issued JWT).
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code shadowUserId}   – UUID PK of the ShadowUser record</li>
     *   <li>{@code authUserId}     – JWT {@code sub} claim</li>
     *   <li>{@code organizationId} – JWT {@code organizationId} claim</li>
     *   <li>{@code previousEmail}  – email before the update</li>
     *   <li>{@code newEmail}       – email after the update</li>
     *   <li>{@code displayName}    – updated display name</li>
     *   <li>{@code syncedAt}       – ISO-8601 datetime of the sync</li>
     * </ul>
     * </p>
     */
    SHADOW_USER_UPDATED
}