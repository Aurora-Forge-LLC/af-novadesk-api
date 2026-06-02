package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.FiscalYearTemplate;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.repository.FiscalYearTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FiscalYearTemplateService}.
 *
 * <p>Validates the database-driven {@code findByCountry} logic that loads
 * fiscal year templates from the {@code fiscal_year_templates} table.</p>
 *
 * @see FiscalYearTemplateService
 * @see FiscalYearTemplate
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FiscalYearTemplateService")
class FiscalYearTemplateServiceTest {

    @Mock
    private FiscalYearTemplateRepository templateRepository;

    @InjectMocks
    private FiscalYearTemplateService service;

    // =========================================================================
    // findByCountry — basic contract
    // =========================================================================

    @Nested
    @DisplayName("findByCountry")
    class FindByCountry {

        @Test
        @DisplayName("should return FiscalYearTemplate for US")
        void shouldReturnTemplateForUS() {
            // Arrange
            FiscalYearTemplate expected = FiscalYearTemplate.builder()
                    .countryCode("US")
                    .fiscalStartMonth(1).fiscalStartDay(1)
                    .fiscalEndMonth(12).fiscalEndDay(31)
                    .periodsPerYear(12)
                    .build();
            when(templateRepository.findByCountryCode("US")).thenReturn(Optional.of(expected));

            // Act
            FiscalYearTemplate result = service.findByCountry(CountryCode.US);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getCountryCode()).isEqualTo("US");
            assertThat(result.getFiscalStartMonth()).isEqualTo(1);
            assertThat(result.getPeriodsPerYear()).isEqualTo(12);
        }

        @Test
        @DisplayName("should return FiscalYearTemplate for India")
        void shouldReturnTemplateForIndia() {
            // Arrange
            FiscalYearTemplate expected = FiscalYearTemplate.builder()
                    .countryCode("IN")
                    .fiscalStartMonth(4).fiscalStartDay(1)
                    .fiscalEndMonth(3).fiscalEndDay(31)
                    .periodsPerYear(12)
                    .build();
            when(templateRepository.findByCountryCode("IN")).thenReturn(Optional.of(expected));

            // Act
            FiscalYearTemplate result = service.findByCountry(CountryCode.IN);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getCountryCode()).isEqualTo("IN");
        }

        @Test
        @DisplayName("should return FiscalYearTemplate for Nepal")
        void shouldReturnTemplateForNepal() {
            // Arrange
            FiscalYearTemplate expected = FiscalYearTemplate.builder()
                    .countryCode("NP")
                    .fiscalStartMonth(7).fiscalStartDay(16)
                    .fiscalEndMonth(6).fiscalEndDay(15)
                    .periodsPerYear(12)
                    .build();
            when(templateRepository.findByCountryCode("NP")).thenReturn(Optional.of(expected));

            // Act
            FiscalYearTemplate result = service.findByCountry(CountryCode.NP);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getCountryCode()).isEqualTo("NP");
        }

        @Test
        @DisplayName("should throw BadRequestException when country is null")
        void shouldThrowWhenCountryNull() {
            // Act & Assert
            assertThatThrownBy(() -> service.findByCountry(null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Country code must not be null");
        }

        @Test
        @DisplayName("should throw BadRequestException when no template found for country")
        void shouldThrowWhenTemplateNotFound() {
            // Arrange
            when(templateRepository.findByCountryCode("US")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.findByCountry(CountryCode.US))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("No fiscal year template found for country");
        }
    }
}
