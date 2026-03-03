package app.meads.entry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Strength {
    HYDROMEL("Hydromel"),
    STANDARD("Standard"),
    SACK("Sack");

    private final String displayName;
}
