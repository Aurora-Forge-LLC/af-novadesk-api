package com.af.novadesk.api.finance.config;

import com.af.novadesk.api.finance.service.StatementParser;
import com.af.novadesk.api.finance.service.StatementParserFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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

    /**
     * Creates a {@link StatementParserFactory} with all available
     * {@link StatementParser} implementations injected automatically.
     */
    @Bean
    public StatementParserFactory statementParserFactory(List<StatementParser> parsers) {
        return new StatementParserFactory(parsers);
    }
}
