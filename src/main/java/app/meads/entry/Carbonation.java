package app.meads.entry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Carbonation {
    STILL("Still"),
    PETILLANT("Petillant"),
    SPARKLING("Sparkling");

    private final String displayName;
}
