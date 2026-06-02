package com.af.novadesk.api.finance.config;

import org.springframework.context.annotation.Configuration;

/**
 * Finance-module Spring configuration.
 *
 * <p>Global infrastructure (JPA auditing, transaction management, auditor
 * resolution, ObjectMapper) has been moved to
 * {@link com.af.novadesk.api.common.config.CommonModuleConfig} to avoid
 * bean-definition conflicts across modules.
 *
 * <p>Use this class for finance-specific beans and overrides only.
 * </p>
 */
@Configuration
public class FinanceModuleConfig {
    // Finance-specific beans and overrides go here.
    // Global infrastructure is in CommonModuleConfig.
}
