package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FiscalYearTemplateService}.
 *
 * <p>Validates the country-specific fiscal year template building logic.
 * Currently the implementation is a placeholder that returns an empty
 * {@link FiscalYearSetting}. Once the template logic is implemented,
 * these tests should be updated to assert country-specific values.</p>
 *
 * @see FiscalYearTemplateService
 * @see FiscalYearSetting
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FiscalYearTemplateService")
class FiscalYearTemplateServiceTest {

    @InjectMocks
    private com.af.novadesk.api.finance.service.impl.FiscalYearTemplateServiceImpl service;

    // =========================================================================
    // buildFromCountry — basic contract
    // =========================================================================

    @Nested
    @DisplayName("buildFromCountry")
    class BuildFromCountry {

        @Test
        @DisplayName("should return a non-null FiscalYearSetting for US")
        void shouldReturnNonNullForUS() {
            // Act
            FiscalYearSetting setting = service.buildFromCountry(CountryCode.US);

            // Assert
            assertThat(setting).isNotNull();
        }

        @Test
        @DisplayName("should return a non-null FiscalYearSetting for India")
        void shouldReturnNonNullForIndia() {
            // Act
            FiscalYearSetting setting = service.buildFromCountry(CountryCode.IN);

            // Assert
            assertThat(setting).isNotNull();
        }

        @Test
        @DisplayName("should return a non-null FiscalYearSetting for Nepal")
        void shouldReturnNonNullForNepal() {
            // Act
            FiscalYearSetting setting = service.buildFromCountry(CountryCode.NP);

            // Assert
            assertThat(setting).isNotNull();
        }
    }
}
