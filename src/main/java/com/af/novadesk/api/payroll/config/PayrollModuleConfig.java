package com.af.novadesk.api.payroll.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Payroll module configuration.
 * Enables JPA auditing for the module's entities.
 */
@Configuration
@EnableJpaAuditing
public class PayrollModuleConfig {
}
