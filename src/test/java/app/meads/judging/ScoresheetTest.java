package app.meads.judging;

import app.meads.judging.internal.MjpScoringFieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScoresheetTest {

    @Test
    void shouldStartInBlankStatusAndPromoteToDraftOnFirstScoreUpdate() {
        var sheet = new Scoresheet(UUID.randomUUID(), UUID.randomUUID());
        assertThat(sheet.getStatus())
                .as("freshly-created scoresheet should be BLANK, not DRAFT")
                .isEqualTo(ScoresheetStatus.BLANK);

        sheet.updateScore(MjpScoringFieldDefinition.APPEARANCE, 4, null);

        assertThat(sheet.getStatus())
                .as("first updateScore should promote BLANK → DRAFT")
                .isEqualTo(ScoresheetStatus.DRAFT);
    }
}
