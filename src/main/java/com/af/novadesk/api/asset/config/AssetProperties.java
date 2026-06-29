package com.af.novadesk.api.asset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Asset module configuration properties.
 * Bound from {@code app.asset.*} in application.yml.
 */
@ConfigurationProperties(prefix = "app.asset")
public record AssetProperties(
        /** Cron expression for the fiscal-year-end depreciation scheduler. Default: 23:59 on Dec 31. */
        String depreciationCron
) {}
