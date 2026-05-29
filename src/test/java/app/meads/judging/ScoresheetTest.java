package app.meads.judging;

import app.meads.judging.internal.MjpScoringFieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldPromoteDraftToFilledWhenAllFieldsScoredAndMarkedFilled() {
        var sheet = filledDraft();

        sheet.markFilled();

        assertThat(sheet.getStatus())
                .as("markFilled should promote a fully-scored DRAFT → FILLED")
                .isEqualTo(ScoresheetStatus.FILLED);
        assertThat(sheet.getTotalScore())
                .as("markFilled must not compute the total — that happens at submit")
                .isNull();
    }

    @Test
    void shouldRejectMarkFilledWhenAnyFieldUnscored() {
        var sheet = new Scoresheet(UUID.randomUUID(), UUID.randomUUID());
        sheet.updateScore(MjpScoringFieldDefinition.APPEARANCE, 4, "clear and bright");
        // the remaining four fields are still unscored

        assertThatThrownBy(sheet::markFilled)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNoOpMarkFilledWhenAlreadyFilled() {
        var sheet = filledDraft();
        sheet.markFilled();

        // Re-saving an unchanged FILLED sheet must not throw — it is already
        // validated. Idempotent so a judge re-opening + re-clicking Save is
        // graceful rather than an "incomplete" error.
        sheet.markFilled();

        assertThat(sheet.getStatus()).isEqualTo(ScoresheetStatus.FILLED);
    }

    @Test
    void shouldRejectMarkFilledWhenSheetIsBlank() {
        var sheet = new Scoresheet(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(sheet::markFilled)
                .as("a BLANK sheet has not been touched — markFilled requires DRAFT")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldDemoteFilledToDraftWhenScoreEdited() {
        var sheet = filledDraft();
        sheet.markFilled();

        sheet.updateScore(MjpScoringFieldDefinition.FINISH, 11, "lingering warmth");

        assertThat(sheet.getStatus())
                .as("editing a scored field on a FILLED sheet demotes it back to DRAFT")
                .isEqualTo(ScoresheetStatus.DRAFT);
    }

    @Test
    void shouldDemoteFilledToDraftWhenOverallCommentsEdited() {
        var sheet = filledDraft();
        sheet.markFilled();

        sheet.updateOverallComments("Revised additional notes for the entrant");

        assertThat(sheet.getStatus())
                .as("editing the additional comments on a FILLED sheet demotes it to DRAFT")
                .isEqualTo(ScoresheetStatus.DRAFT);
    }

    @Test
    void shouldKeepFilledWhenAdvanceToMedalRoundToggled() {
        var sheet = filledDraft();
        sheet.markFilled();

        sheet.setAdvancedToMedalRound(true);

        assertThat(sheet.getStatus())
                .as("toggling the advance-to-medal flag is not a content edit — stays FILLED")
                .isEqualTo(ScoresheetStatus.FILLED);
    }

    @Test
    void shouldSubmitFromFilledComputingTotalScore() {
        var sheet = filledDraft();
        sheet.markFilled();

        sheet.submit();

        assertThat(sheet.getStatus()).isEqualTo(ScoresheetStatus.SUBMITTED);
        assertThat(sheet.getTotalScore()).isEqualTo(4 + 20 + 25 + 10 + 8);
        assertThat(sheet.getSubmittedAt()).isNotNull();
    }

    @Test
    void shouldRejectSubmitWhenStillDraft() {
        var sheet = filledDraft();
        // never marked FILLED — submit now requires FILLED, not DRAFT

        assertThatThrownBy(sheet::submit)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRevertSubmittedSheetBackToFilledClearingTotal() {
        var sheet = filledDraft();
        sheet.markFilled();
        sheet.submit();
        assertThat(sheet.getStatus()).isEqualTo(ScoresheetStatus.SUBMITTED);

        // Round reopen drops sheets SUBMITTED -> FILLED (not DRAFT) so they stay
        // valid; the total is cleared and only a fresh edit demotes to DRAFT.
        sheet.revertToFilled();

        assertThat(sheet.getStatus()).isEqualTo(ScoresheetStatus.FILLED);
        assertThat(sheet.getTotalScore()).isNull();
        assertThat(sheet.getSubmittedAt()).isNull();
    }

    @Test
    void shouldRejectRevertToFilledWhenNotSubmitted() {
        var sheet = filledDraft();
        sheet.markFilled(); // FILLED, not SUBMITTED

        assertThatThrownBy(sheet::revertToFilled)
                .isInstanceOf(IllegalStateException.class);
    }

    /** A DRAFT sheet with every MJP field scored — the precondition for markFilled. */
    private static Scoresheet filledDraft() {
        var sheet = new Scoresheet(UUID.randomUUID(), UUID.randomUUID());
        sheet.updateScore(MjpScoringFieldDefinition.APPEARANCE, 4, "clear and bright");
        sheet.updateScore(MjpScoringFieldDefinition.AROMA_BOUQUET, 20, "floral honey nose");
        sheet.updateScore(MjpScoringFieldDefinition.FLAVOUR_AND_BODY, 25, "balanced and clean");
        sheet.updateScore(MjpScoringFieldDefinition.FINISH, 10, "crisp dry finish");
        sheet.updateScore(MjpScoringFieldDefinition.OVERALL_IMPRESSION, 8, "well made example");
        return sheet;
    }
}
