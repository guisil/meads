package app.meads;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CountryDisplayTest {

    @Test
    void shouldLocalizeCountryNameToTheGivenLocale() {
        assertThat(CountryDisplay.name("DE", Locale.ENGLISH)).isEqualTo("Germany");
        assertThat(CountryDisplay.name("DE", Locale.ITALIAN)).isEqualTo("Germania");
        assertThat(CountryDisplay.name("DE", Locale.of("pt"))).isEqualTo("Alemanha");
        assertThat(CountryDisplay.name("DE", Locale.of("es"))).isEqualTo("Alemania");
    }
}
