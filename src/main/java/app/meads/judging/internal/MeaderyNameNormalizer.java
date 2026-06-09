package app.meads.judging.internal;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Country-aware meadery-name normalization + similarity check used by COI detection.
 * See §2.E of docs/plans/2026-05-05-judging-module-design.md.
 */
public final class MeaderyNameNormalizer {

    private static final int LEVENSHTEIN_THRESHOLD = 2;

    private static final Map<String, Set<String>> SUFFIXES_BY_COUNTRY = Map.ofEntries(
            Map.entry("GLOBAL", Set.of(
                    "llc", "inc", "ltd", "co", "corp", "plc",
                    "meadery", "mead", "meads", "meadworks",
                    "cellars", "farm", "brewery")),
            Map.entry("PT", Set.of("lda", "sa", "ldª", "hidromelaria", "hidromelina")),
            Map.entry("BR", Set.of("lda", "sa", "ldª", "hidromelaria", "hidromelina")),
            Map.entry("ES", Set.of("sl", "sa", "srl", "hidromielería",
                    "hidromelería", "hidromiel")),
            Map.entry("MX", Set.of("sl", "sa", "srl", "hidromielería",
                    "hidromelería", "hidromiel")),
            Map.entry("AR", Set.of("sl", "sa", "srl", "hidromielería",
                    "hidromelería", "hidromiel")),
            Map.entry("IT", Set.of("srl", "spa", "sas", "sapa",
                    "idromeleria", "idromele")),
            Map.entry("PL", Set.of("sp z o o", "sa", "sk", "miodosytnia",
                    "pasieka", "miód")),
            Map.entry("FR", Set.of("sarl", "sas", "eurl", "sa",
                    "hydromellerie", "hydromel")),
            Map.entry("DE", Set.of("gmbh", "ag", "ohg", "kg", "metherei",
                    "metmacherei", "metbrauerei")),
            Map.entry("AT", Set.of("gmbh", "ag", "ohg", "kg", "metherei",
                    "metmacherei", "metbrauerei")),
            Map.entry("CH", Set.of("gmbh", "ag", "ohg", "kg", "metherei",
                    "metmacherei", "metbrauerei")),
            Map.entry("NL", Set.of("bv", "nv", "meddrijf", "mede")),
            Map.entry("BE", Set.of("bv", "nv", "meddrijf", "mede")));

    private MeaderyNameNormalizer() {
    }

    public static String normalize(String meaderyName, String countryCode) {
        if (!StringUtils.hasText(meaderyName)) {
            return "";
        }
        var lower = meaderyName.toLowerCase();
        var spaced = lower.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (spaced.isEmpty()) {
            return "";
        }
        var suffixes = combinedSuffixes(countryCode, null);
        var sortedSuffixes = suffixes.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (var suffix : sortedSuffixes) {
            spaced = stripWholeWord(spaced, suffix);
        }
        return spaced.replaceAll("\\s+", " ").trim();
    }

    public static boolean areSimilar(String name1, String country1,
                                     String name2, String country2) {
        if (!StringUtils.hasText(name1) || !StringUtils.hasText(name2)) {
            return false;
        }
        if (country1 != null && country2 != null && !country1.equalsIgnoreCase(country2)) {
            return false;
        }
        var combinedSuffixes = combinedSuffixes(country1, country2);
        var n1 = normalizeWithSuffixes(name1, combinedSuffixes);
        var n2 = normalizeWithSuffixes(name2, combinedSuffixes);
        if (n1.isEmpty() || n2.isEmpty()) {
            return false;
        }
        if (n1.equals(n2)) {
            return true;
        }
        return levenshtein(n1, n2) <= LEVENSHTEIN_THRESHOLD;
    }

    private static String normalizeWithSuffixes(String name, Set<String> suffixes) {
        var lower = name.toLowerCase();
        var spaced = lower.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (spaced.isEmpty()) {
            return "";
        }
        var sortedSuffixes = suffixes.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (var suffix : sortedSuffixes) {
            spaced = stripWholeWord(spaced, suffix);
        }
        return spaced.replaceAll("\\s+", " ").trim();
    }

    private static Set<String> combinedSuffixes(String country1, String country2) {
        var result = new LinkedHashSet<>(SUFFIXES_BY_COUNTRY.get("GLOBAL"));
        addCountrySuffixes(result, country1);
        addCountrySuffixes(result, country2);
        return result;
    }

    private static void addCountrySuffixes(Set<String> target, String countryCode) {
        if (countryCode == null) {
            return;
        }
        var suffixes = SUFFIXES_BY_COUNTRY.get(countryCode.toUpperCase());
        if (suffixes != null) {
            target.addAll(suffixes);
        }
    }

    private static String stripWholeWord(String text, String suffix) {
        // Whole-word match: surrounded by start/end of string or whitespace.
        // Treat suffix's internal whitespace as flexible — match by tokens.
        var tokens = text.split("\\s+");
        var suffixTokens = suffix.split("\\s+");
        if (suffixTokens.length == 0) {
            return text;
        }
        var keep = new java.util.ArrayList<String>();
        int i = 0;
        while (i < tokens.length) {
            if (i + suffixTokens.length <= tokens.length
                    && matchesAt(tokens, i, suffixTokens)) {
                i += suffixTokens.length;
            } else {
                keep.add(tokens[i]);
                i++;
            }
        }
        return String.join(" ", keep);
    }

    private static boolean matchesAt(String[] tokens, int start, String[] suffixTokens) {
        for (int j = 0; j < suffixTokens.length; j++) {
            if (!tokens[start + j].equals(suffixTokens[j])) {
                return false;
            }
        }
        return true;
    }

    private static int levenshtein(String a, String b) {
        var prev = new int[b.length() + 1];
        var curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            var tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    // unused but documented for clarity (not on call paths)
    @SuppressWarnings("unused")
    private static Set<String> defensiveCopy(Set<String> set) {
        return new HashSet<>(Arrays.asList(set.toArray(new String[0])));
    }
}
