package io.github.mortogo321.recon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.mortogo321.recon.domain.money.Money;

class MoneyTest {

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        void scalesToCurrencyFractionDigitsSoEqualityIsValueBased() {
            assertThat(Money.of("100.5", "THB")).isEqualTo(Money.of("100.50", "THB"));
            assertThat(Money.of("100.500", "THB")).hasToString("THB 100.50");
        }

        @Test
        void usesHalfEvenRoundingToAvoidSystematicDriftOverLargeFiles() {
            assertThat(Money.of("1.005", "THB")).isEqualTo(Money.of("1.00", "THB"));
            assertThat(Money.of("1.015", "THB")).isEqualTo(Money.of("1.02", "THB"));
        }

        @Test
        void supportsZeroDecimalCurrencies() {
            Money yen = Money.of("1200", "JPY");
            assertThat(yen.amount().scale()).isZero();
            assertThat(yen.toMinorUnits()).isEqualTo(1200L);
        }

        @Test
        void rejectsNulls() {
            assertThatNullPointerException().isThrownBy(() -> new Money(null, Currency.getInstance("THB")));
            assertThatNullPointerException().isThrownBy(() -> new Money(BigDecimal.ONE, null));
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void addsAndSubtractsWithinTheSameCurrency() {
            assertThat(Money.of("10.00", "THB").add(Money.of("2.50", "THB"))).isEqualTo(Money.of("12.50", "THB"));
            assertThat(Money.of("10.00", "THB").subtract(Money.of("12.50", "THB")))
                    .isEqualTo(Money.of("-2.50", "THB"));
        }

        @Test
        void refusesCrossCurrencyArithmeticRatherThanGuessingARate() {
            assertThatExceptionOfType(Money.CurrencyMismatchException.class)
                    .isThrownBy(() -> Money.of("10.00", "THB").add(Money.of("10.00", "USD")))
                    .satisfies(ex -> {
                        assertThat(ex.left()).isEqualTo("THB");
                        assertThat(ex.right()).isEqualTo("USD");
                    });
        }

        @ParameterizedTest
        @CsvSource({"-5.00,5.00", "0.00,0.00", "7.25,7.25"})
        void absIsAlwaysNonNegative(String input, String expected) {
            assertThat(Money.of(input, "THB").abs()).isEqualTo(Money.of(expected, "THB"));
        }

        @Test
        void negateAndSignHelpers() {
            assertThat(Money.of("5.00", "THB").negate()).isEqualTo(Money.of("-5.00", "THB"));
            assertThat(Money.of("-0.01", "THB").isNegative()).isTrue();
            assertThat(Money.zero(Currency.getInstance("THB")).isZero()).isTrue();
        }

        @Test
        void comparesByAmount() {
            assertThat(Money.of("1.00", "THB")).isLessThan(Money.of("2.00", "THB"));
            assertThat(Money.of("2.00", "THB")).isGreaterThan(Money.of("1.00", "THB"));
        }

        @Test
        void minorUnitsRoundTrip() {
            assertThat(Money.of("123.45", "THB").toMinorUnits()).isEqualTo(12345L);
        }
    }
}
