package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.DepreciationSchedule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publishes asset domain events to the transactional outbox for the accounting module.
 *
 * <p>Events published (to be consumed by the accounting/finance module):
 * <ul>
 *   <li>{@code ASSET_PURCHASED} — triggers: Debit Fixed Assets, Credit Cash/AP</li>
 *   <li>{@code ASSET_DEPRECIATION_POSTED} — triggers: Debit Depreciation Expense, Credit Accumulated Depreciation</li>
 * </ul>
 * </p>
 *
 * <p>Note: The actual outbox table and polling publisher for asset events will be
 * added in a follow-up migration once the accounting module integration is designed.
 * For now this service logs the events and is a placeholder for the full outbox pattern.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetOutboxServiceImpl {

    private final ObjectMapper objectMapper;

    /**
     * Publishes an {@code ASSET_PURCHASED} event.
     * The accounting module listens and creates:
     *   Debit:  Fixed Assets - [Category]
     *   Credit: Cash / Accounts Payable
     */
    public void publishAssetPurchased(Asset asset) {
        Map<String, Object> payload = Map.of(
            "assetId",       asset.getId(),
            "category",      asset.getCategory().name(),
            "assetType",     asset.getAssetType(),
            "serialNumber",  asset.getSerialNumber(),
            "purchaseCost",  asset.getPurchaseCost(),
            "currencyCode",  asset.getCurrencyCode(),
            "purchaseDate",  asset.getPurchaseDate().toString(),
            "legalEntityId", asset.getLegalEntity().getId(),
            "organizationId",asset.getOrganizationId()
        );
        log.info("ASSET_PURCHASED event: {}", serialize(payload));
        // TODO: persist to ast_outbox_events table (follow-up migration)
    }

    /**
     * Publishes an {@code ASSET_DEPRECIATION_POSTED} event.
     * The accounting module listens and creates:
     *   Debit:  Depreciation Expense - [Category]
     *   Credit: Accumulated Depreciation - [Category]
     */
    public void publishDepreciationPosted(Asset asset, DepreciationSchedule schedule) {
        Map<String, Object> payload = Map.of(
            "assetId",               asset.getId(),
            "category",              asset.getCategory().name(),
            "fiscalYear",            schedule.getFiscalYear(),
            "annualDepreciation",    schedule.getAnnualDepreciation(),
            "accumulatedDepreciation", schedule.getAccumulatedDepreciation(),
            "netBookValue",          schedule.getNetBookValue(),
            "currencyCode",          asset.getCurrencyCode(),
            "legalEntityId",         asset.getLegalEntity().getId(),
            "organizationId",        asset.getOrganizationId()
        );
        log.info("ASSET_DEPRECIATION_POSTED event: {}", serialize(payload));
        // TODO: persist to ast_outbox_events table (follow-up migration)
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return payload.toString();
        }
    }
}
