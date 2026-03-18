package app.meads.entry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class StrengthTest {

    @ParameterizedTest
    @CsvSource({
            "0.0, HYDROMEL",
            "5.0, HYDROMEL",
            "7.5, HYDROMEL",
            "7.6, STANDARD",
            "10.0, STANDARD",
            "14.0, STANDARD",
            "14.1, SACK",
            "18.0, SACK",
            "25.0, SACK"
    })
    void shouldDeriveStrengthFromAbv(String abv, Strength expected) {
        assertThat(Strength.fromAbv(new BigDecimal(abv))).isEqualTo(expected);
    }

    @Test
    void shouldThrowWhenAbvIsNull() {
        assertThatThrownBy(() -> Strength.fromAbv(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
