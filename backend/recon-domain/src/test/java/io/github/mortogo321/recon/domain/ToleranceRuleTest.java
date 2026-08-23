package io.github.mortogo321.recon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.mortogo321.recon.domain.match.ToleranceRule;
import io.github.mortogo321.recon.domain.money.Money;

class ToleranceRuleTest {

    private static final ToleranceRule RULE = new ToleranceRule(Money.of("1.00", "THB"), 10); // 1 THB or 10 bps

    @Test
    void absoluteFloorWinsOnSmallTickets() {
        // 10 bps of 50 THB is 0.05 — the 1.00 floor must dominate.
        assertThat(RULE.allowanceFor(Money.of("50.00", "THB"))).isEqualTo(Money.of("1.00", "THB"));
    }

    @Test
    void relativeAllowanceWinsOnLargeTickets() {
        // 10 bps of 100,000 THB is 100.00, far above the 1.00 floor.
        assertThat(RULE.allowanceFor(Money.of("100000.00", "THB"))).isEqualTo(Money.of("100.00", "THB"));
    }

    @ParameterizedTest
    @CsvSource({
        "100.00, 100.00, true",   // exact
        "100.00,  99.50, true",   // inside the 1.00 floor
        "100.00, 101.00, true",   // boundary, inclusive
        "100.00, 101.01, false",  // just outside
        "100.00,  98.99, false",
    })
    void acceptsOnlyWithinAllowance(String expected, String actual, boolean accepted) {
        assertThat(RULE.accepts(Money.of(expected, "THB"), Money.of(actual, "THB"))).isEqualTo(accepted);
    }

    @Test
    void exactRuleAcceptsNothingButAnExactMatch() {
        ToleranceRule exact = ToleranceRule.exact("THB");
        assertThat(exact.accepts(Money.of("10.00", "THB"), Money.of("10.00", "THB"))).isTrue();
        assertThat(exact.accepts(Money.of("10.00", "THB"), Money.of("10.01", "THB"))).isFalse();
    }

    @Test
    void neverAcceptsAcrossCurrencies() {
        assertThat(RULE.accepts(Money.of("10.00", "THB"), Money.of("10.00", "USD"))).isFalse();
    }

    @Test
    void fallsBackToRelativeOnlyWhenConfiguredInAnotherCurrency() {
        ToleranceRule usdRule = new ToleranceRule(Money.of("5.00", "USD"), 100); // 1%
        assertThat(usdRule.allowanceFor(Money.of("200.00", "THB"))).isEqualTo(Money.of("2.00", "THB"));
    }

    @Test
    void rejectsNegativeConfiguration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToleranceRule(Money.of("-1.00", "THB"), 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToleranceRule(Money.of("1.00", "THB"), -1));
    }
}
