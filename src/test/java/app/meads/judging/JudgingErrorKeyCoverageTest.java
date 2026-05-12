package app.meads.judging;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lint test: ensures every error key the judging module throws via
 * {@code BusinessRuleException} is translated in both {@code messages.properties}
 * (EN) and {@code messages_pt.properties} (PT). Without this guard a new
 * {@code error.judging-*} key would silently fall back to the raw key string
 * in the UI for non-English speakers.
 */
class JudgingErrorKeyCoverageTest {

    private static final Pattern KEY_PATTERN =
            Pattern.compile("new\\s+BusinessRuleException\\(\\s*\"(error\\.[a-z0-9.\\-]+)\"");

    private static final Path JUDGING_SRC = Path.of("src/main/java/app/meads/judging");
    private static final Path EN = Path.of("src/main/resources/messages.properties");
    private static final Path PT = Path.of("src/main/resources/messages_pt.properties");

    @Test
    void everyJudgingErrorKeyHasTranslationInEnAndPt() throws Exception {
        Set<String> thrownKeys = collectThrownKeys();
        Properties en = load(EN);
        Properties pt = load(PT);

        Set<String> missingInEn = new TreeSet<>();
        Set<String> missingInPt = new TreeSet<>();
        for (String key : thrownKeys) {
            if (!en.containsKey(key)) {
                missingInEn.add(key);
            }
            if (!pt.containsKey(key)) {
                missingInPt.add(key);
            }
        }

        assertThat(missingInEn)
                .as("error keys thrown by judging module but missing from messages.properties")
                .isEmpty();
        assertThat(missingInPt)
                .as("error keys thrown by judging module but missing from messages_pt.properties")
                .isEmpty();
    }

    private Set<String> collectThrownKeys() throws Exception {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(JUDGING_SRC)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> appendKeysFromFile(p, keys));
        }
        return keys;
    }

    private void appendKeysFromFile(Path file, Set<String> keys) {
        try {
            String content = Files.readString(file);
            Matcher matcher = KEY_PATTERN.matcher(content);
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }

    private Properties load(Path path) throws Exception {
        var props = new Properties();
        try (var in = Files.newBufferedReader(path)) {
            props.load(in);
        }
        return props;
    }
}
