package app.meads.entry;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryTest {

    private static final UUID DIVISION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private Entry createDefaultEntry() {
        return new Entry(DIVISION_ID, USER_ID, 1, "ABC123",
                "My Mead", CATEGORY_ID, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
    }

    // --- Cycle 1: Constructor ---

    @Test
    void shouldCreateEntryWithDraftStatus() {
        var entry = createDefaultEntry();

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getDivisionId()).isEqualTo(DIVISION_ID);
        assertThat(entry.getUserId()).isEqualTo(USER_ID);
        assertThat(entry.getEntryNumber()).isEqualTo(1);
        assertThat(entry.getEntryCode()).isEqualTo("ABC123");
        assertThat(entry.getMeadName()).isEqualTo("My Mead");
        assertThat(entry.getInitialCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(entry.getFinalCategoryId()).isNull();
        assertThat(entry.getSweetness()).isEqualTo(Sweetness.DRY);
        assertThat(entry.getStrength()).isEqualTo(Strength.STANDARD);
        assertThat(entry.getAbv()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(entry.getCarbonation()).isEqualTo(Carbonation.STILL);
        assertThat(entry.getHoneyVarieties()).isEqualTo("Wildflower honey");
        assertThat(entry.getOtherIngredients()).isNull();
        assertThat(entry.isWoodAged()).isFalse();
        assertThat(entry.getWoodAgeingDetails()).isNull();
        assertThat(entry.getAdditionalInformation()).isNull();
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.DRAFT);
    }

    @Test
    void shouldRejectWoodAgedWithoutDetails() {
        assertThatThrownBy(() -> new Entry(DIVISION_ID, USER_ID, 1, "ABC123",
                "My Mead", CATEGORY_ID, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, true, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wood ageing details");
    }

    @Test
    void shouldAllowWoodAgedWithDetails() {
        var entry = new Entry(DIVISION_ID, USER_ID, 1, "ABC123",
                "My Mead", CATEGORY_ID, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, true, "Oak barrel, 6 months", null);

        assertThat(entry.isWoodAged()).isTrue();
        assertThat(entry.getWoodAgeingDetails()).isEqualTo("Oak barrel, 6 months");
    }

    // --- Cycle 2: submit() ---

    @Test
    void shouldSubmitDraftEntry() {
        var entry = createDefaultEntry();

        entry.submit();

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
    }

    // --- Cycle 3: submit() rejects non-DRAFT ---

    @Test
    void shouldRejectSubmitWhenNotDraft() {
        var entry = createDefaultEntry();
        entry.submit(); // DRAFT → SUBMITTED

        assertThatThrownBy(entry::submit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot submit");
    }

    // --- Cycle 4: markReceived() ---

    @Test
    void shouldMarkSubmittedEntryAsReceived() {
        var entry = createDefaultEntry();
        entry.submit();

        entry.markReceived();

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.RECEIVED);
    }

    @Test
    void shouldRejectMarkReceivedWhenNotSubmitted() {
        var entry = createDefaultEntry(); // DRAFT

        assertThatThrownBy(entry::markReceived)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot mark as received");
    }

    // --- Cycle 5: withdraw() ---

    @Test
    void shouldWithdrawDraftEntry() {
        var entry = createDefaultEntry();

        entry.withdraw();

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.WITHDRAWN);
    }

    @Test
    void shouldWithdrawSubmittedEntry() {
        var entry = createDefaultEntry();
        entry.submit();

        entry.withdraw();

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.WITHDRAWN);
    }

    @Test
    void shouldWithdrawReceivedEntry() {
        var entry = createDefaultEntry();
        entry.submit();
        entry.markReceived();

        entry.withdraw();

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.WITHDRAWN);
    }

    @Test
    void shouldRejectWithdrawWhenAlreadyWithdrawn() {
        var entry = createDefaultEntry();
        entry.withdraw();

        assertThatThrownBy(entry::withdraw)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot withdraw");
    }

    // --- Cycle 6: updateDetails() ---

    @Test
    void shouldUpdateDetailsWhenDraft() {
        var entry = createDefaultEntry();
        var newCategoryId = UUID.randomUUID();

        entry.updateDetails("Updated Mead", newCategoryId, Sweetness.SWEET,
                Strength.SACK, new BigDecimal("18.0"), Carbonation.SPARKLING,
                "Orange blossom", "Spices", true, "Cherry wood, 3 months",
                "Special batch");

        assertThat(entry.getMeadName()).isEqualTo("Updated Mead");
        assertThat(entry.getInitialCategoryId()).isEqualTo(newCategoryId);
        assertThat(entry.getSweetness()).isEqualTo(Sweetness.SWEET);
        assertThat(entry.getStrength()).isEqualTo(Strength.SACK);
        assertThat(entry.getAbv()).isEqualByComparingTo(new BigDecimal("18.0"));
        assertThat(entry.getCarbonation()).isEqualTo(Carbonation.SPARKLING);
        assertThat(entry.getHoneyVarieties()).isEqualTo("Orange blossom");
        assertThat(entry.getOtherIngredients()).isEqualTo("Spices");
        assertThat(entry.isWoodAged()).isTrue();
        assertThat(entry.getWoodAgeingDetails()).isEqualTo("Cherry wood, 3 months");
        assertThat(entry.getAdditionalInformation()).isEqualTo("Special batch");
    }

    @Test
    void shouldRejectUpdateDetailsWhenNotDraft() {
        var entry = createDefaultEntry();
        entry.submit();

        assertThatThrownBy(() -> entry.updateDetails("Updated Mead", CATEGORY_ID,
                Sweetness.DRY, Strength.STANDARD, new BigDecimal("12.5"),
                Carbonation.STILL, "Wildflower honey", null, false, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot update details");
    }

    @Test
    void shouldRejectUpdateDetailsWithWoodAgedButNoDetails() {
        var entry = createDefaultEntry();

        assertThatThrownBy(() -> entry.updateDetails("Updated Mead", CATEGORY_ID,
                Sweetness.DRY, Strength.STANDARD, new BigDecimal("12.5"),
                Carbonation.STILL, "Wildflower honey", null, true, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wood ageing details");
    }

    // --- Cycle 7: assignFinalCategory() ---

    @Test
    void shouldAssignFinalCategory() {
        var entry = createDefaultEntry();
        entry.submit();
        var finalCategoryId = UUID.randomUUID();

        entry.assignFinalCategory(finalCategoryId);

        assertThat(entry.getFinalCategoryId()).isEqualTo(finalCategoryId);
    }

    @Test
    void shouldAssignFinalCategoryOnDraft() {
        var entry = createDefaultEntry();
        var finalCategoryId = UUID.randomUUID();

        entry.assignFinalCategory(finalCategoryId);

        assertThat(entry.getFinalCategoryId()).isEqualTo(finalCategoryId);
    }

    @Test
    void shouldRejectAssignFinalCategoryWhenWithdrawn() {
        var entry = createDefaultEntry();
        entry.withdraw();

        assertThatThrownBy(() -> entry.assignFinalCategory(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot assign final category");
    }

    // --- Cycle 8: getEffectiveCategoryId() ---

    @Test
    void shouldReturnInitialCategoryWhenNoFinalCategory() {
        var entry = createDefaultEntry();

        assertThat(entry.getEffectiveCategoryId()).isEqualTo(CATEGORY_ID);
    }

    @Test
    void shouldReturnFinalCategoryWhenSet() {
        var entry = createDefaultEntry();
        var finalCategoryId = UUID.randomUUID();
        entry.assignFinalCategory(finalCategoryId);

        assertThat(entry.getEffectiveCategoryId()).isEqualTo(finalCategoryId);
    }
}
