package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.service.impl.FiscalYearTemplateServiceImpl;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.common.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FiscalYearTemplateService}.
 *
 * <p>Validates the switch-based {@code buildFromCountry} logic that returns
 * country-specific fiscal year settings (LLR-FIN-01.2).</p>
 *
 * @see FiscalYearTemplateService
 * @see FiscalYearSetting
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FiscalYearTemplateService")
class FiscalYearTemplateServiceTest {

    @InjectMocks
    private FiscalYearTemplateServiceImpl service;

    // =========================================================================
    // buildFromCountry — basic contract
    // =========================================================================

    @Nested
    @DisplayName("buildFromCountry")
    class BuildFromCountry {

        @Test
        @DisplayName("should return calendar-year fiscal setting for US (Jan 1 – Dec 31)")
        void shouldReturnTemplateForUS() {
            FiscalYearSetting result = service.buildFromCountry(CountryCode.US);

            assertThat(result).isNotNull();
            assertThat(result.getFiscalStartMonth()).isEqualTo(1);
            assertThat(result.getFiscalStartDay()).isEqualTo(1);
            assertThat(result.getFiscalEndMonth()).isEqualTo(12);
            assertThat(result.getFiscalEndDay()).isEqualTo(31);
            assertThat(result.getPeriodsPerYear()).isEqualTo(12);
            assertThat(result.getCurrentFiscalYear()).isPositive();
        }

        @Test
        @DisplayName("should return April–March fiscal setting for India")
        void shouldReturnTemplateForIndia() {
            FiscalYearSetting result = service.buildFromCountry(CountryCode.IN);

            assertThat(result).isNotNull();
            assertThat(result.getFiscalStartMonth()).isEqualTo(4);
            assertThat(result.getFiscalStartDay()).isEqualTo(1);
            assertThat(result.getFiscalEndMonth()).isEqualTo(3);
            assertThat(result.getFiscalEndDay()).isEqualTo(31);
            assertThat(result.getPeriodsPerYear()).isEqualTo(12);
        }

        @Test
        @DisplayName("should return mid-July fiscal setting for Nepal (Bikram Sambat)")
        void shouldReturnTemplateForNepal() {
            FiscalYearSetting result = service.buildFromCountry(CountryCode.NP);

            assertThat(result).isNotNull();
            assertThat(result.getFiscalStartMonth()).isEqualTo(7);
            assertThat(result.getFiscalStartDay()).isEqualTo(16);
            assertThat(result.getFiscalEndMonth()).isEqualTo(7);
            assertThat(result.getFiscalEndDay()).isEqualTo(15);
            assertThat(result.getPeriodsPerYear()).isEqualTo(12);
        }

        @Test
        @DisplayName("should throw NullPointerException when country is null")
        void shouldThrowWhenCountryNull() {
            assertThatThrownBy(() -> service.buildFromCountry(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Country must not be null");
        }

        @Test
        @DisplayName("should set currentFiscalYear to current year")
        void shouldSetCurrentFiscalYear() {
            FiscalYearSetting result = service.buildFromCountry(CountryCode.US);

            int currentYear = java.time.Year.now().getValue();
            assertThat(result.getCurrentFiscalYear()).isEqualTo(currentYear);
        }
    }
}
