package app.meads.entry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Sweetness {
    DRY("Dry"),
    MEDIUM("Medium"),
    SWEET("Sweet");

    private final String displayName;
}
