package app.meads.entry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum Strength {
    HYDROMEL("Hydromel"),
    STANDARD("Standard"),
    SACK("Sack");

    private static final BigDecimal HYDROMEL_MAX = new BigDecimal("7.5");
    private static final BigDecimal STANDARD_MAX = new BigDecimal("14");

    private final String displayName;

    public static Strength fromAbv(BigDecimal abv) {
        if (abv == null) {
            throw new IllegalArgumentException("ABV must not be null");
        }
        if (abv.compareTo(HYDROMEL_MAX) <= 0) {
            return HYDROMEL;
        }
        if (abv.compareTo(STANDARD_MAX) <= 0) {
            return STANDARD;
        }
        return SACK;
    }
}
