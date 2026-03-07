package app.meads.internal;

import app.meads.competition.*;
import app.meads.entry.*;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@Profile("dev")
class DevDataInitializer {

    private final UserService userService;
    private final CompetitionService competitionService;
    private final EntryService entryService;

    DevDataInitializer(UserService userService,
                       CompetitionService competitionService,
                       EntryService entryService) {
        this.userService = userService;
        this.competitionService = competitionService;
        this.entryService = entryService;
    }

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    void initializeDevData() {
        // Idempotency: skip if CHIP 2026 already exists
        var existing = competitionService.findAllCompetitions();
        if (existing.stream().anyMatch(c -> "CHIP 2026".equals(c.getName()))) {
            log.info("Dev data already exists, skipping initialization");
            return;
        }

        var admin = userService.findByEmail("admin@example.com");
        var adminId = admin.getId();

        seedChip2026(adminId);
        seedTestCompetition(adminId);

        log.info("Dev data initialization complete");
    }

    private void seedChip2026(UUID adminId) {
        // 1. Create competition
        var chip = competitionService.createCompetition(
                "CHIP 2026",
                java.time.LocalDate.of(2026, 6, 1),
                java.time.LocalDate.of(2026, 6, 30),
                "Lisbon, Portugal",
                adminId);
        log.info("Created competition: {}", chip.getName());

        // 2. Create divisions
        var amadora = competitionService.createDivision(
                chip.getId(), "Amadora", ScoringSystem.MJP, adminId);
        var profissional = competitionService.createDivision(
                chip.getId(), "Profissional", ScoringSystem.MJP, adminId);
        log.info("Created divisions: Amadora ({}), Profissional ({})",
                amadora.getId(), profissional.getId());

        // 3. Remove excluded categories (M4B, M4D) from both divisions
        removeCategory(amadora.getId(), "M4B", adminId);
        removeCategory(amadora.getId(), "M4D", adminId);
        removeCategory(profissional.getId(), "M4B", adminId);
        removeCategory(profissional.getId(), "M4D", adminId);
        log.info("Removed M4B and M4D categories from CHIP divisions");

        // 4. Set entry limits
        competitionService.updateDivisionEntryLimits(
                amadora.getId(), 3, 5, adminId);
        competitionService.updateDivisionEntryLimits(
                profissional.getId(), 3, 5, adminId);
        log.info("Set entry limits: 3 per subcategory, 5 per main category");

        // 5. Advance CHIP divisions: DRAFT → REGISTRATION_OPEN
        competitionService.advanceDivisionStatus(amadora.getId(), adminId);
        competitionService.advanceDivisionStatus(profissional.getId(), adminId);
        log.info("Advanced CHIP divisions to REGISTRATION_OPEN");

        // 6. Add participants (non-ENTRANT roles only — ENTRANT is added by addCredits)
        competitionService.addParticipantByEmail(
                chip.getId(), "admin@example.com", CompetitionRole.ADMIN, adminId);
        competitionService.addParticipantByEmail(
                chip.getId(), "judge@example.com", CompetitionRole.JUDGE, adminId);
        competitionService.addParticipantByEmail(
                chip.getId(), "steward@example.com", CompetitionRole.STEWARD, adminId);
        log.info("Added participants to CHIP 2026");

        // 7. Create product mappings
        entryService.createProductMapping(
                amadora.getId(), "1001", "CHIP-AMA",
                "CHIP Amadora Entry", 1, adminId);
        entryService.createProductMapping(
                profissional.getId(), "1002", "CHIP-PRO",
                "CHIP Profissional Entry", 1, adminId);
        log.info("Created product mappings for CHIP divisions");

        // 8. Add credits (also adds ENTRANT participant role automatically)
        var devUser = userService.findByEmail("user@example.com");
        var devEntrant = userService.findByEmail("entrant@example.com");
        entryService.addCredits(amadora.getId(), "user@example.com", 5, adminId);
        entryService.addCredits(amadora.getId(), "entrant@example.com", 3, adminId);
        log.info("Added credits: user@example.com=5, entrant@example.com=3");

        // 9. Create entries
        var categories = competitionService.findDivisionCategories(amadora.getId());
        var m1a = findCategoryByCode(categories, "M1A"); // Traditional Mead
        var m2c = findCategoryByCode(categories, "M2C"); // Berry Melomel
        var m3b = findCategoryByCode(categories, "M3B"); // Metheglin

        // Entry 1 for user@example.com: Traditional Mead (DRAFT)
        entryService.createEntry(
                amadora.getId(), devUser.getId(),
                "Wildflower Traditional",
                m1a.getId(),
                Sweetness.DRY, Strength.STANDARD,
                BigDecimal.valueOf(12.5), Carbonation.STILL,
                "Wildflower honey",
                null, false, null, null);

        // Entry 2 for user@example.com: Berry Melomel — submit it
        entryService.createEntry(
                amadora.getId(), devUser.getId(),
                "Blueberry Bliss",
                m2c.getId(),
                Sweetness.MEDIUM, Strength.STANDARD,
                BigDecimal.valueOf(13.0), Carbonation.STILL,
                "Acacia honey",
                "Fresh blueberries", false, null, null);
        entryService.submitAllDrafts(amadora.getId(), devUser.getId());

        // Entry 3 for user@example.com: another draft after submitting
        entryService.createEntry(
                amadora.getId(), devUser.getId(),
                "Oak-Aged Bochet",
                m1a.getId(),
                Sweetness.SWEET, Strength.SACK,
                BigDecimal.valueOf(16.0), Carbonation.STILL,
                "Caramelized wildflower honey",
                null, true, "French oak, 6 months", null);

        // Entry 1 for entrant@example.com: Metheglin (DRAFT)
        entryService.createEntry(
                amadora.getId(), devEntrant.getId(),
                "Lavender Metheglin",
                m3b.getId(),
                Sweetness.MEDIUM, Strength.STANDARD,
                BigDecimal.valueOf(11.5), Carbonation.PETILLANT,
                "Lavender honey",
                "Lavender, chamomile", false, null, null);

        log.info("Created entries for CHIP Amadora");
    }

    private void seedTestCompetition(UUID adminId) {
        var test = competitionService.createCompetition(
                "Test Competition 2026",
                java.time.LocalDate.of(2026, 9, 1),
                java.time.LocalDate.of(2026, 9, 30),
                "Porto, Portugal",
                adminId);

        competitionService.createDivision(
                test.getId(), "Open", ScoringSystem.MJP, adminId);

        competitionService.addParticipantByEmail(
                test.getId(), "admin@example.com", CompetitionRole.ADMIN, adminId);

        log.info("Created competition: {} with Open division", test.getName());
    }

    private void removeCategory(UUID divisionId, String code, UUID adminId) {
        var categories = competitionService.findDivisionCategories(divisionId);
        categories.stream()
                .filter(c -> code.equals(c.getCode()))
                .findFirst()
                .ifPresent(c -> competitionService.removeDivisionCategory(
                        divisionId, c.getId(), adminId));
    }

    private DivisionCategory findCategoryByCode(
            java.util.List<DivisionCategory> categories, String code) {
        return categories.stream()
                .filter(c -> code.equals(c.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Category not found: " + code));
    }
}
