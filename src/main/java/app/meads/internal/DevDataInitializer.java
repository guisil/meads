package app.meads.internal;

import app.meads.competition.*;
import app.meads.entry.*;
import app.meads.identity.UserService;
import app.meads.judging.Certification;
import app.meads.awards.AwardsService;
import app.meads.judging.JudgeProfileService;
import app.meads.judging.JudgingService;
import app.meads.judging.Medal;
import app.meads.judging.MedalRoundMode;
import app.meads.judging.RoundType;
import app.meads.judging.ScoresheetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@Profile("dev")
class DevDataInitializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Long free-text values for the "fully-filled, verbose entry" demo entries
    // seeded into each CHIP 2026 division (one per division) — used to check how
    // admin and judge views render long mead descriptions. Kept under the entry
    // form field caps (honey/other/wood ≤ 255 in the admin form, additional info
    // ≤ 1000) so the entries remain editable without truncation.
    private static final String LONG_HONEY =
            "A blend of single-origin orange-blossom honey from the Algarve, high-mountain heather honey "
            + "from the Serra da Estrela, and a small proportion of raw wildflower honey from the maker's own "
            + "hives — each from a different harvest to build floral complexity and depth.";
    private static final String LONG_OTHER =
            "Hand-zested bergamot and Seville orange peel, one split Madagascar vanilla pod, lightly toasted "
            + "coriander seed, a pinch of pink peppercorn, and a measured late addition of tartaric acid to "
            + "balance the residual sweetness of the finished mead.";
    private static final String LONG_WOOD =
            "Aged eleven months in a lightly charred French oak barrel that previously held a botrytised "
            + "dessert wine, then rested six further weeks on medium-plus toast oak spirals to lift the "
            + "vanilla and baking-spice notes without over-extracting tannin.";
    private static final String LONG_ADDITIONAL =
            "Brewing notes: original gravity 1.110, final gravity 1.020, fermented with a Champagne yeast "
            + "stepped up over three days and kept at a steady 16-18 °C for a slow, clean ferment. Staggered "
            + "nutrient additions were made over the first third of fermentation, followed by extended lees "
            + "contact for mouthfeel. Back-sweetened to taste after cold-crashing and stabilising. Bottle-"
            + "conditioned for a gentle sparkle and cellared for fourteen months before entry. Contains "
            + "naturally occurring sulphites; suitable for vegetarians but not vegans (honey). Best served "
            + "lightly chilled at 8-10 °C in a tulip glass to showcase the aromatics. An earlier vintage of "
            + "this mead placed second in its category at a regional show; this batch is the maker's most "
            + "refined expression of the recipe to date and is intended as a flagship dessert-style mead.";

    private final UserService userService;
    private final CompetitionService competitionService;
    private final EntryService entryService;
    private final WebhookService webhookService;
    private final JudgeProfileService judgeProfileService;
    private final JudgingService judgingService;
    private final ScoresheetService scoresheetService;
    private final AwardsService awardsService;

    DevDataInitializer(UserService userService,
                       CompetitionService competitionService,
                       EntryService entryService,
                       WebhookService webhookService,
                       JudgeProfileService judgeProfileService,
                       JudgingService judgingService,
                       ScoresheetService scoresheetService,
                       AwardsService awardsService) {
        this.userService = userService;
        this.competitionService = competitionService;
        this.entryService = entryService;
        this.webhookService = webhookService;
        this.judgeProfileService = judgeProfileService;
        this.judgingService = judgingService;
        this.scoresheetService = scoresheetService;
        this.awardsService = awardsService;
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

        var sysAdmin = userService.findByEmail("admin@example.com");
        var sysAdminId = sysAdmin.getId();
        var compAdmin = userService.findByEmail("compadmin@example.com");
        var compAdminId = compAdmin.getId();

        seedUserProfiles();
        seedChip2026(sysAdminId, compAdminId);
        seedTestCompetition(sysAdminId, compAdminId);
        seedFastTrackPublished(sysAdminId, compAdminId);

        log.info("Dev data initialization complete");
    }

    private void seedUserProfiles() {
        var compAdmin = userService.findByEmail("compadmin@example.com");
        userService.updateProfile(compAdmin.getId(), compAdmin.getName(), "Hidroméis do Minho", "PT", "pt");

        var devUser = userService.findByEmail("user@example.com");
        // user@'s meadery + country deliberately match judge2's "Hidroméis do Minho" /
        // PT so the soft-COI badge fires in §12.6.3 (Assign Judges) on user@'s Amadora
        // entries. (MeaderyNameNormalizer.areSimilar bails when both countries are set
        // and differ — same country is required for soft-COI to trigger.)
        userService.updateProfile(devUser.getId(), devUser.getName(), "Hidroméis do Minho", "PT", null);

        var entrant = userService.findByEmail("entrant@example.com");
        userService.updateProfile(entrant.getId(), entrant.getName(), null, "DE", null);

        // Judges — varied meadery names + countries + preferred languages so §12.6.3,
        // §12.11, and §12.14 can be exercised against realistic profiles.
        userService.updateProfile(userService.findByEmail("judge@example.com").getId(),
                "Dev Judge", null, "PT", "pt");
        userService.updateProfile(userService.findByEmail("judge2@example.com").getId(),
                "Dev Judge 2", "Hidroméis do Minho", "PT", "pt");
        userService.updateProfile(userService.findByEmail("judge3@example.com").getId(),
                "Dev Judge 3", "Pereira & Pereira (private)", "PT", "pt");
        userService.updateProfile(userService.findByEmail("judge4@example.com").getId(),
                "Dev Judge 4", null, "ES", "es");
        userService.updateProfile(userService.findByEmail("judge5@example.com").getId(),
                "Dev Judge 5", null, "IT", "it");
        userService.updateProfile(userService.findByEmail("judge6@example.com").getId(),
                "Dev Judge 6", null, "GB", "en");

        // Pro entrants need a meadery name set — Profissional has meaderyNameRequired=true.
        userService.updateProfile(userService.findByEmail("proentrant1@example.com").getId(),
                "Pro Entrant 1", "Hidroméis do Douro", "PT", "pt");
        userService.updateProfile(userService.findByEmail("proentrant2@example.com").getId(),
                "Pro Entrant 2", "Honey Lab Lisboa", "PT", "pt");
        userService.updateProfile(userService.findByEmail("proentrant3@example.com").getId(),
                "Pro Entrant 3", "Mead House Madrid", "ES", "es");
        userService.updateProfile(userService.findByEmail("proentrant4@example.com").getId(),
                "Pro Entrant 4", "Cantina del Miele", "IT", "it");

        log.info("Set meadery names and countries for dev users");
    }

    private void seedChip2026(UUID sysAdminId, UUID compAdminId) {
        // 1. Create competition (requires SYSTEM_ADMIN)
        var chip = competitionService.createCompetition(
                "CHIP 2026",
                "chip-2026",
                java.time.LocalDate.of(2026, 6, 11),
                java.time.LocalDate.of(2026, 6, 14),
                "Amarante, Portugal",
                sysAdminId);
        log.info("Created competition: {}", chip.getName());

        // 2. Add competition admin (requires SYSTEM_ADMIN since no comp admin yet)
        competitionService.addParticipantByEmail(
                chip.getId(), "compadmin@example.com", CompetitionRole.ADMIN, sysAdminId);

        // 3. Create divisions (competition admin can do this)
        var deadline = LocalDateTime.of(2026, 6, 30, 23, 59);
        var amadora = competitionService.createDivision(
                chip.getId(), "Amadora", "amadora", ScoringSystem.MJP,
                deadline, "Europe/Lisbon", compAdminId);
        var profissional = competitionService.createDivision(
                chip.getId(), "Profissional", "profissional", ScoringSystem.MJP,
                deadline, "Europe/Lisbon", compAdminId);
        log.info("Created divisions: Amadora ({}), Profissional ({})",
                amadora.getId(), profissional.getId());

        // 4. Remove excluded categories (M4B, M4D) from both divisions
        removeCategory(amadora.getId(), "M4B", compAdminId);
        removeCategory(amadora.getId(), "M4D", compAdminId);
        removeCategory(profissional.getId(), "M4B", compAdminId);
        removeCategory(profissional.getId(), "M4D", compAdminId);
        log.info("Removed M4B and M4D categories from CHIP divisions");

        // 5. Set entry limits
        competitionService.updateDivisionEntryLimits(
                amadora.getId(), 3, 5, 10, compAdminId);
        competitionService.updateDivisionEntryLimits(
                profissional.getId(), 3, 5, 10, compAdminId);
        log.info("Set entry limits: 3 per subcategory, 5 per main category, 10 total");

        // 5b. Set entry prefixes
        competitionService.updateDivision(amadora.getId(),
                amadora.getName(), amadora.getShortName(), amadora.getScoringSystem(),
                "AMA", compAdminId);
        competitionService.updateDivision(profissional.getId(),
                profissional.getName(), profissional.getShortName(), profissional.getScoringSystem(),
                "PRO", compAdminId);

        // 5c. Profissional requires meadery name
        competitionService.updateDivisionMeaderyNameRequired(
                profissional.getId(), true, compAdminId);

        // 6. Advance CHIP divisions: DRAFT → REGISTRATION_OPEN
        competitionService.advanceDivisionStatus(amadora.getId(), compAdminId);
        competitionService.advanceDivisionStatus(profissional.getId(), compAdminId);
        log.info("Advanced CHIP divisions to REGISTRATION_OPEN");

        // 7. Add participants (non-ENTRANT roles only — ENTRANT is added by addCredits)
        competitionService.addParticipantByEmail(
                chip.getId(), "judge@example.com", CompetitionRole.JUDGE, compAdminId);
        competitionService.addParticipantByEmail(
                chip.getId(), "judge2@example.com", CompetitionRole.JUDGE, compAdminId);
        competitionService.addParticipantByEmail(
                chip.getId(), "judge3@example.com", CompetitionRole.JUDGE, compAdminId);
        competitionService.addParticipantByEmail(
                chip.getId(), "judge4@example.com", CompetitionRole.JUDGE, compAdminId);
        competitionService.addParticipantByEmail(
                chip.getId(), "judge5@example.com", CompetitionRole.JUDGE, compAdminId);
        competitionService.addParticipantByEmail(
                chip.getId(), "judge6@example.com", CompetitionRole.JUDGE, compAdminId);
        competitionService.addParticipantByEmail(
                chip.getId(), "steward@example.com", CompetitionRole.STEWARD, compAdminId);
        log.info("Added participants to CHIP 2026 (6 judges, 1 steward, 1 comp admin)");

        // 7b. JudgeProfile records — certifications + qualification details for §12.14.
        //     createOrUpdate requires self-or-SYSTEM_ADMIN, so pass the system admin id.
        seedJudgeProfile("judge@example.com", java.util.Set.of(Certification.MJP), "Judging since 2018", sysAdminId);
        seedJudgeProfile("judge2@example.com", java.util.Set.of(Certification.MJP), "Active in Iberian competitions", sysAdminId);
        seedJudgeProfile("judge3@example.com", java.util.Set.of(Certification.BJCP), "BJCP Recognized", sysAdminId);
        seedJudgeProfile("judge4@example.com", java.util.Set.of(Certification.MJP, Certification.BJCP), null, sysAdminId);
        seedJudgeProfile("judge5@example.com", java.util.Set.of(Certification.OTHER), "WSET Level 3 (Wine)", sysAdminId);
        seedJudgeProfile("judge6@example.com", java.util.Set.of(), null, sysAdminId);
        log.info("Created JudgeProfile records for all 6 CHIP judges");

        // 8. Create product mappings
        entryService.createProductMapping(
                amadora.getId(), "1001", "CHIP-AMA",
                "CHIP Amadora Entry", 1, compAdminId);
        entryService.createProductMapping(
                profissional.getId(), "1002", "CHIP-PRO",
                "CHIP Profissional Entry", 1, compAdminId);
        log.info("Created product mappings for CHIP divisions");

        // 9. Add credits (also adds ENTRANT participant role automatically)
        var devUser = userService.findByEmail("user@example.com");
        var devEntrant = userService.findByEmail("entrant@example.com");
        entryService.addCredits(amadora.getId(), "user@example.com", 5, compAdminId);
        entryService.addCredits(amadora.getId(), "entrant@example.com", 3, compAdminId);
        log.info("Added credits: user@example.com=5, entrant@example.com=3");

        // 10. Create entries for Amadora — 10 entries with status variety so the
        //     judging walkthrough has enough data to populate medal rounds.
        var amadoraCategories = competitionService.findDivisionCategories(amadora.getId());
        var amaM1A = findCategoryByCode(amadoraCategories, "M1A"); // Traditional Mead (Dry)
        var amaM1B = findCategoryByCode(amadoraCategories, "M1B"); // Traditional Mead (Medium)
        var amaM2C = findCategoryByCode(amadoraCategories, "M2C"); // Berry Melomel
        var amaM3B = findCategoryByCode(amadoraCategories, "M3B"); // Metheglin
        var amaM4A = findCategoryByCode(amadoraCategories, "M4A"); // Cyser

        // user@example.com (5 credits): 5 entries — mix of DRAFT, SUBMITTED, RECEIVED
        createEntrantEntry(amadora, devUser, "Wildflower Traditional", amaM1A,
                Sweetness.DRY, 12.5, Carbonation.STILL, "Wildflower honey", null, false, null);
        // → leave as DRAFT

        var blueberryBliss = createEntrantEntry(amadora, devUser, "Blueberry Bliss", amaM2C,
                Sweetness.MEDIUM, 13.0, Carbonation.STILL, "Acacia honey", "Fresh blueberries", false, null);
        entryService.submitEntry(blueberryBliss.getId(), devUser.getId()); // → SUBMITTED

        createEntrantEntry(amadora, devUser, "Oak-Aged Bochet", amaM1A,
                Sweetness.SWEET, 16.0, Carbonation.STILL, "Caramelized wildflower honey",
                null, true, "French oak, 6 months");
        // → leave as DRAFT

        var honeyReserve = createEntrantEntry(amadora, devUser, "Honey Reserve", amaM1B,
                Sweetness.MEDIUM, 12.0, Carbonation.STILL, "Heather honey", null, false, null);
        advanceToReceived(honeyReserve, devUser, compAdminId);

        var strawberryFields = createEntrantEntry(amadora, devUser, "Strawberry Fields", amaM2C,
                Sweetness.MEDIUM, 11.0, Carbonation.PETILLANT, "Wildflower honey", "Strawberry pulp", false, null);
        advanceToReceived(strawberryFields, devUser, compAdminId);

        // entrant@example.com (3 credits): 3 entries
        createEntrantEntry(amadora, devEntrant, "Lavender Metheglin", amaM3B,
                Sweetness.MEDIUM, 11.5, Carbonation.PETILLANT, "Lavender honey",
                "Lavender, chamomile", false, null);
        // → leave as DRAFT

        var rosemarySage = createEntrantEntry(amadora, devEntrant, "Rosemary & Sage", amaM3B,
                Sweetness.DRY, 12.5, Carbonation.STILL, "Acacia honey", "Rosemary, sage", false, null);
        entryService.submitEntry(rosemarySage.getId(), devEntrant.getId()); // → SUBMITTED

        var mountainHoney = createEntrantEntry(amadora, devEntrant, "Mountain Honey", amaM1B,
                Sweetness.MEDIUM, 13.5, Carbonation.STILL, "Mountain wildflower honey", null, false, null);
        advanceToReceived(mountainHoney, devEntrant, compAdminId);

        log.info("Created 8 entrant-submitted entries for CHIP Amadora");

        // 11. Webhook orders — buyer1 (2 credits Amadora) + buyer2 (3 credits Profissional)
        webhookService.processOrderPaid(buildOrderPayload(
                "JS-1001", "buyer1@example.com", "Maria Silva",
                "1001", "CHIP-AMA", "CHIP Amadora Entry", 2, "PT"));
        webhookService.processOrderPaid(buildOrderPayload(
                "JS-1002", "buyer2@example.com", "João Santos",
                "1002", "CHIP-PRO", "CHIP Profissional Entry", 3, "BR"));
        log.info("Created example webhook orders");

        // 12. Admin-add 2 entries for buyer1 in Amadora — one RECEIVED, one WITHDRAWN
        var appleMead = entryService.adminCreateEntry(amadora.getId(), "buyer1@example.com",
                "Apple Mead", amaM4A.getId(),
                Sweetness.DRY, BigDecimal.valueOf(7.5), Carbonation.STILL,
                "Wildflower honey", "Apple cider", false, null, null, compAdminId);
        // adminCreateEntry returns a DRAFT — admin advances DRAFT → SUBMITTED → RECEIVED.
        entryService.advanceEntryStatus(appleMead.getId(), compAdminId);
        entryService.advanceEntryStatus(appleMead.getId(), compAdminId);

        var sunsetMead = entryService.adminCreateEntry(amadora.getId(), "buyer1@example.com",
                "Sunset Mead", amaM1A.getId(),
                Sweetness.SWEET, BigDecimal.valueOf(14.0), Carbonation.STILL,
                "Orange blossom honey", null, false, null, null, compAdminId);
        entryService.withdrawEntry(sunsetMead.getId(), compAdminId);

        log.info("Created 2 admin-added entries for buyer1 (1 RECEIVED, 1 WITHDRAWN)");

        // 12b. Hard-COI seed: judge3 is also an entrant with a RECEIVED entry in M1A.
        // When admin assigns judges to an M1A table, judge3 shows a red "Self-entry"
        // badge (§12.6.3); judge3 navigating to their own scoresheet is forwarded
        // away (§12.11.3).
        entryService.addCredits(amadora.getId(), "judge3@example.com", 1, compAdminId);
        var judge3Mead = entryService.adminCreateEntry(amadora.getId(), "judge3@example.com",
                "Judge's Secret Mead", amaM1A.getId(),
                Sweetness.DRY, BigDecimal.valueOf(12.0), Carbonation.STILL,
                "Wildflower honey", null, false, null, null, compAdminId);
        entryService.advanceEntryStatus(judge3Mead.getId(), compAdminId); // SUBMITTED
        entryService.advanceEntryStatus(judge3Mead.getId(), compAdminId); // RECEIVED
        log.info("Created hard-COI entry for judge3 in Amadora M1A");

        // 12c. Verbose demo entry — every field filled with long free-text, so
        // admin views (Entry Admin view/edit dialogs, label PDF) can be checked
        // against long mead descriptions. RECEIVED.
        createDetailedReceivedEntry(amadora, "buyer1@example.com",
                "Hidromel de Demonstração — Campos Completos", amaM1A, compAdminId);
        log.info("Created verbose all-fields demo entry for Amadora M1A");

        log.info("CHIP Amadora ready: 12 entries (3 DRAFT, 2 SUBMITTED, 6 RECEIVED, 1 WITHDRAWN)");

        // 13. Physical tables for both Amadora and Profissional, so the admin
        //     can immediately wire rounds to them.
        judgingService.createPhysicalTable(amadora.getId(), "Table 1", compAdminId);
        judgingService.createPhysicalTable(amadora.getId(), "Table 2", compAdminId);
        judgingService.createPhysicalTable(amadora.getId(), "Table 3", compAdminId);
        log.info("Amadora: created 3 physical tables");

        // 13. Profissional: pre-stage to JUDGING with 20 RECEIVED entries assigned to
        //     judging categories, so an admin can jump straight into §12.6+ without
        //     repeating the registration flow.
        seedProfissionalForJudging(profissional, compAdminId);
    }

    private void seedProfissionalForJudging(Division profissional, UUID compAdminId) {
        var pro1 = userService.findByEmail("proentrant1@example.com");
        var pro2 = userService.findByEmail("proentrant2@example.com");
        var pro3 = userService.findByEmail("proentrant3@example.com");
        var pro4 = userService.findByEmail("proentrant4@example.com");

        // Each pro entrant gets 5 credits → 5 entries → 20 total.
        entryService.addCredits(profissional.getId(), pro1.getEmail(), 5, compAdminId);
        entryService.addCredits(profissional.getId(), pro2.getEmail(), 5, compAdminId);
        entryService.addCredits(profissional.getId(), pro3.getEmail(), 5, compAdminId);
        entryService.addCredits(profissional.getId(), pro4.getEmail(), 5, compAdminId);

        var profCategories = competitionService.findDivisionCategories(profissional.getId());
        var proM1A = findCategoryByCode(profCategories, "M1A");
        var proM1B = findCategoryByCode(profCategories, "M1B");
        var proM2A = findCategoryByCode(profCategories, "M2A");
        var proM2C = findCategoryByCode(profCategories, "M2C");
        var proM3B = findCategoryByCode(profCategories, "M3B");

        // 5 categories × varied counts = 20 entries (within per-user 3/5/10 limits).
        // proentrant1: 1 per category
        createReceivedProEntry(profissional, pro1, compAdminId, "Quinta do Mel Tradicional", proM1A,
                Sweetness.DRY, 13.0, Carbonation.STILL, "Rosemary honey");
        createReceivedProEntry(profissional, pro1, compAdminId, "Reserva Antiga", proM1B,
                Sweetness.MEDIUM, 12.5, Carbonation.STILL, "Heather honey");
        createReceivedProEntry(profissional, pro1, compAdminId, "Maçã Selvagem", proM2A,
                Sweetness.MEDIUM, 10.5, Carbonation.STILL, "Wildflower honey");
        createReceivedProEntry(profissional, pro1, compAdminId, "Frutos Vermelhos", proM2C,
                Sweetness.MEDIUM, 12.0, Carbonation.STILL, "Acacia honey");
        createReceivedProEntry(profissional, pro1, compAdminId, "Ervas Aromáticas", proM3B,
                Sweetness.DRY, 11.5, Carbonation.STILL, "Rosemary honey");

        // proentrant2: 1 per category
        createReceivedProEntry(profissional, pro2, compAdminId, "Honey Lab Classic", proM1A,
                Sweetness.DRY, 12.0, Carbonation.STILL, "Orange blossom honey");
        createReceivedProEntry(profissional, pro2, compAdminId, "Lisboa Reserva", proM1B,
                Sweetness.MEDIUM, 13.0, Carbonation.STILL, "Eucalyptus honey");
        createReceivedProEntry(profissional, pro2, compAdminId, "Pera Atlântica", proM2A,
                Sweetness.MEDIUM, 11.0, Carbonation.PETILLANT, "Wildflower honey");
        createReceivedProEntry(profissional, pro2, compAdminId, "Cerejas em Flor", proM2C,
                Sweetness.MEDIUM, 12.5, Carbonation.STILL, "Acacia honey");
        createReceivedProEntry(profissional, pro2, compAdminId, "Cardamomo & Mel", proM3B,
                Sweetness.MEDIUM, 12.0, Carbonation.STILL, "Wildflower honey");

        // proentrant3: 1 per category
        createReceivedProEntry(profissional, pro3, compAdminId, "Madrid Tradicional", proM1A,
                Sweetness.DRY, 13.5, Carbonation.STILL, "Sunflower honey");
        createReceivedProEntry(profissional, pro3, compAdminId, "Solera de Miel", proM1B,
                Sweetness.MEDIUM, 14.0, Carbonation.STILL, "Wildflower honey");
        createReceivedProEntry(profissional, pro3, compAdminId, "Manzana de la Sierra", proM2A,
                Sweetness.DRY, 9.5, Carbonation.STILL, "Wildflower honey");
        createReceivedProEntry(profissional, pro3, compAdminId, "Fresas Andaluzas", proM2C,
                Sweetness.MEDIUM, 11.5, Carbonation.STILL, "Acacia honey");
        createReceivedProEntry(profissional, pro3, compAdminId, "Romero y Tomillo", proM3B,
                Sweetness.DRY, 12.5, Carbonation.STILL, "Rosemary honey");

        // proentrant4: 2 in M1A + 1 each in M1B / M2A / M2C (no M3B)
        createReceivedProEntry(profissional, pro4, compAdminId, "Miele Toscano", proM1A,
                Sweetness.DRY, 12.0, Carbonation.STILL, "Chestnut honey");
        createReceivedProEntry(profissional, pro4, compAdminId, "Cantina Riserva", proM1A,
                Sweetness.MEDIUM, 13.5, Carbonation.STILL, "Acacia honey");
        createReceivedProEntry(profissional, pro4, compAdminId, "Tradizione di Famiglia", proM1B,
                Sweetness.MEDIUM, 12.5, Carbonation.STILL, "Wildflower honey");
        createReceivedProEntry(profissional, pro4, compAdminId, "Mela d'Inverno", proM2A,
                Sweetness.MEDIUM, 10.0, Carbonation.STILL, "Wildflower honey");
        createReceivedProEntry(profissional, pro4, compAdminId, "Lampone & Mirtillo", proM2C,
                Sweetness.SWEET, 13.0, Carbonation.STILL, "Acacia honey");

        log.info("CHIP Profissional: created 20 RECEIVED entries across 4 pro entrants");

        // Advance Profissional through to JUDGING:
        // REGISTRATION_OPEN → REGISTRATION_CLOSED → (init judging cats + assign final cats) → JUDGING
        competitionService.advanceDivisionStatus(profissional.getId(), compAdminId);
        log.info("Profissional → REGISTRATION_CLOSED");

        competitionService.initializeJudgingCategories(profissional.getId(), compAdminId);
        log.info("Profissional: initialized judging categories");

        // Map JUDGING-scope category by code, then assign each entry's finalCategoryId
        // to its same-coded JUDGING category. This satisfies the
        // EntryFinalCategoryAdvanceGuard.
        var judgingCategories = competitionService.findJudgingCategories(profissional.getId());
        var registrationToJudging = new java.util.HashMap<UUID, UUID>();
        var byJudgingCode = new java.util.HashMap<String, UUID>();
        for (var jc : judgingCategories) {
            byJudgingCode.put(jc.getCode(), jc.getId());
        }
        for (var rc : profCategories) {
            var judgingId = byJudgingCode.get(rc.getCode());
            if (judgingId != null) {
                registrationToJudging.put(rc.getId(), judgingId);
            }
        }
        var profEntries = entryService.findEntriesByDivision(profissional.getId());
        for (var entry : profEntries) {
            var judgingId = registrationToJudging.get(entry.getInitialCategoryId());
            if (judgingId != null) {
                entryService.assignFinalCategory(entry.getId(), judgingId, compAdminId);
            }
        }
        log.info("Profissional: assigned final categories to all 20 entries");

        competitionService.advanceDivisionStatus(profissional.getId(), compAdminId);
        log.info("Profissional → JUDGING (ready for §12.6+ walkthrough)");

        // Physical tables for Profissional (5 to cover 5 judging categories in parallel).
        var pt1 = judgingService.createPhysicalTable(profissional.getId(), "Table 1", compAdminId);
        var pt2 = judgingService.createPhysicalTable(profissional.getId(), "Table 2", compAdminId);
        judgingService.createPhysicalTable(profissional.getId(), "Table 3", compAdminId);
        var pt4 = judgingService.createPhysicalTable(profissional.getId(), "Table 4", compAdminId);
        judgingService.createPhysicalTable(profissional.getId(), "Table 5", compAdminId);
        log.info("Profissional: created 5 physical tables");

        // === Pre-staged split-category demo + medal-round judges ===
        // Demonstrates redesign decisions #3 (per-round entry assignment, a
        // category split across two scoring rounds with different judges &
        // physical tables) and #5 (medal rounds with their own judge panel,
        // independent of scoring panels). Both rounds are left at PENDING so
        // the walkthrough admin can Start them interactively.

        var profJudging = judgingService.ensureJudgingExists(profissional.getId());
        var judge1Id = userService.findByEmail("judge@example.com").getId();
        var judge2Id = userService.findByEmail("judge2@example.com").getId();
        var judge4Id = userService.findByEmail("judge4@example.com").getId();
        var judge5Id = userService.findByEmail("judge5@example.com").getId();
        var judge6Id = userService.findByEmail("judge6@example.com").getId();

        var proJudgingCats = competitionService.findJudgingCategories(profissional.getId());
        var proM1AJ = findCategoryByCode(proJudgingCats, "M1A");
        var proM1BJ = findCategoryByCode(proJudgingCats, "M1B");

        // Split M1A (5 RECEIVED entries — 1 each from pro1/pro2/pro3, 2 from pro4)
        // across two scoring rounds.
        var m1aEntries = entryService.findEntriesByFinalCategoryId(proM1AJ.getId());
        var m1aRoundA = judgingService.createRound(
                profJudging.getId(), "M1A Panel A", proM1AJ.getId(), null, compAdminId);
        judgingService.assignRoundToPhysicalTable(m1aRoundA.getId(), pt1.getId(), compAdminId);
        judgingService.assignJudge(m1aRoundA.getId(), judge1Id, compAdminId);
        judgingService.assignJudge(m1aRoundA.getId(), judge2Id, compAdminId);

        var m1aRoundB = judgingService.createRound(
                profJudging.getId(), "M1A Panel B", proM1AJ.getId(), null, compAdminId);
        judgingService.assignRoundToPhysicalTable(m1aRoundB.getId(), pt2.getId(), compAdminId);
        judgingService.assignJudge(m1aRoundB.getId(), judge4Id, compAdminId);
        judgingService.assignJudge(m1aRoundB.getId(), judge5Id, compAdminId);

        // First two M1A entries → Panel A; remaining → Panel B.
        for (int i = 0; i < m1aEntries.size(); i++) {
            var entryId = m1aEntries.get(i).getId();
            var targetRound = (i < 2 ? m1aRoundA : m1aRoundB).getId();
            judgingService.assignEntryToRound(targetRound, entryId, compAdminId);
        }
        log.info("Profissional M1A: split into Panel A (2 entries, judges 1+2, Table 1) "
                + "and Panel B (3 entries, judges 4+5, Table 2)");

        // Verbose all-fields demo entry for Profissional — every field filled with
        // long free-text, assigned to M1A Panel A so a judge opening scoresheets
        // (once the admin starts the round) sees long mead descriptions rendered
        // on the scoresheet. Panel A then holds 3 entries.
        var proRegM1A = findCategoryByCode(profCategories, "M1A");
        var verboseProEntry = createDetailedReceivedEntry(profissional, "buyer2@example.com",
                "Hidromel de Demonstração — Campos Completos", proRegM1A, compAdminId);
        entryService.assignFinalCategory(verboseProEntry.getId(), proM1AJ.getId(), compAdminId);
        judgingService.assignEntryToRound(m1aRoundA.getId(), verboseProEntry.getId(), compAdminId);
        log.info("Profissional M1A Panel A: added verbose all-fields demo entry (now 3 entries)");

        // Pre-stage a medal round for M1B with its own judge panel (judges
        // 1, 2, 6 — note judge6 isn't on any M1B scoring panel here; this is
        // the point of independent medal-round judges).
        judgingService.configureCategoryMedalRound(
                proM1BJ.getId(), MedalRoundMode.COMPARATIVE, compAdminId);
        var m1bMedal = judgingService.createMedalRound(
                profJudging.getId(), proM1BJ.getId(), compAdminId);
        judgingService.assignRoundToPhysicalTable(m1bMedal.getId(), pt4.getId(), compAdminId);
        judgingService.assignJudge(m1bMedal.getId(), judge1Id, compAdminId);
        judgingService.assignJudge(m1bMedal.getId(), judge2Id, compAdminId);
        judgingService.assignJudge(m1bMedal.getId(), judge6Id, compAdminId);
        log.info("Profissional M1B: pre-staged medal round with judges 1+2+6, Table 4");
    }

    private void seedJudgeProfile(String email, java.util.Set<Certification> certifications,
                                    String qualificationDetails, UUID adminId) {
        var judge = userService.findByEmail(email);
        judgeProfileService.createOrUpdate(judge.getId(), certifications,
                qualificationDetails, adminId);
        // Also set the preferred comment language from the user's profile language
        // so ScoresheetView defaults match what the judge prefers.
        if (judge.getPreferredLanguage() != null) {
            judgeProfileService.updatePreferredCommentLanguage(judge.getId(), judge.getPreferredLanguage());
        }
    }

    private Entry createEntrantEntry(Division division, app.meads.identity.User entrant,
                                      String meadName, DivisionCategory category,
                                      Sweetness sweetness, double abv, Carbonation carbonation,
                                      String honey, String otherIngredients,
                                      boolean woodAged, String woodAgeingDetails) {
        return entryService.createEntry(
                division.getId(), entrant.getId(), meadName, category.getId(),
                sweetness, BigDecimal.valueOf(abv), carbonation, honey,
                otherIngredients, woodAged, woodAgeingDetails, null);
    }

    private void advanceToReceived(Entry entry, app.meads.identity.User owner, UUID adminId) {
        entryService.submitEntry(entry.getId(), owner.getId());
        entryService.advanceEntryStatus(entry.getId(), adminId);
    }

    private Entry createReceivedProEntry(Division division, app.meads.identity.User entrant,
                                         UUID adminId, String meadName, DivisionCategory category,
                                         Sweetness sweetness, double abv, Carbonation carbonation,
                                         String honey) {
        var entry = entryService.createEntry(
                division.getId(), entrant.getId(), meadName, category.getId(),
                sweetness, BigDecimal.valueOf(abv), carbonation, honey,
                null, false, null, null);
        advanceToReceived(entry, entrant, adminId);
        return entry;
    }

    /**
     * Admin-creates a fully-populated entry with long free-text in every field
     * (honey varieties, other ingredients, wood-ageing details, additional
     * information) and advances it to RECEIVED. Used to seed one verbose demo
     * entry per CHIP division so admin and judge views can be checked against
     * long mead descriptions. {@code initialCategory} is a REGISTRATION-scope
     * category of the division.
     */
    private Entry createDetailedReceivedEntry(Division division, String ownerEmail,
                                              String meadName, DivisionCategory initialCategory,
                                              UUID adminId) {
        var entry = entryService.adminCreateEntry(division.getId(), ownerEmail, meadName,
                initialCategory.getId(), Sweetness.SWEET, BigDecimal.valueOf(13.5),
                Carbonation.SPARKLING, LONG_HONEY, LONG_OTHER, true, LONG_WOOD,
                LONG_ADDITIONAL, adminId);
        entryService.advanceEntryStatus(entry.getId(), adminId); // DRAFT → SUBMITTED
        entryService.advanceEntryStatus(entry.getId(), adminId); // SUBMITTED → RECEIVED
        return entry;
    }

    private String buildOrderPayload(String orderId, String email, String fullName,
                                      String productId, String sku, String productName,
                                      int quantity, String countryCode) {
        var root = MAPPER.createObjectNode();
        var orderNode = root.putObject("order");
        orderNode.put("id", orderId);
        var customer = orderNode.putObject("customer");
        customer.put("email", email);
        var nameParts = fullName.split(" ", 2);
        var shipping = orderNode.putObject("shipping_address");
        shipping.put("name", nameParts[0]);
        shipping.put("surname", nameParts.length > 1 ? nameParts[1] : "");
        if (countryCode != null) {
            shipping.put("country_code", countryCode);
        }
        var products = orderNode.putArray("products");
        var product = products.addObject();
        product.put("id", productId);
        product.put("sku", sku);
        product.put("name", productName);
        product.put("qty", quantity);
        return root.toString();
    }

    private void seedTestCompetition(UUID sysAdminId, UUID compAdminId) {
        var test = competitionService.createCompetition(
                "Test Competition 2026",
                "test-2026",
                java.time.LocalDate.of(2026, 9, 1),
                java.time.LocalDate.of(2026, 9, 30),
                "Porto, Portugal",
                sysAdminId);

        competitionService.addParticipantByEmail(
                test.getId(), "compadmin@example.com", CompetitionRole.ADMIN, sysAdminId);

        competitionService.createDivision(
                test.getId(), "Open", "open", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", compAdminId);

        log.info("Created competition: {} with Open division", test.getName());
    }

    /**
     * Drives a small one-category division all the way to RESULTS_PUBLISHED with
     * three fully-scored entries, so a fresh dev DB lands directly on a published
     * entrant scoresheet — a fast-path for iterating the entrant-scoresheet
     * redesign without re-walking the §12 judging flow on every reset. The
     * entrant is {@code entrant@example.com}; judges 1 and 2 score the round. One
     * entry ("Mostra Loquaz") carries deliberately long comments to stress the
     * scoresheet layout. Drives the full pipeline: score round → auto-created
     * medal round (GOLD / SILVER / BRONZE) → Best of Show (places the GOLD) →
     * phase COMPLETE → DELIBERATION → publish, all as the competition admin
     * stepping in for the judges.
     */
    private void seedFastTrackPublished(UUID sysAdminId, UUID compAdminId) {
        var fastTrack = competitionService.createCompetition(
                "Fast Track 2026",
                "fast-track-2026",
                java.time.LocalDate.of(2026, 7, 1),
                java.time.LocalDate.of(2026, 7, 2),
                "Lisboa, Portugal",
                sysAdminId);
        competitionService.addParticipantByEmail(
                fastTrack.getId(), "compadmin@example.com", CompetitionRole.ADMIN, sysAdminId);

        var mostra = competitionService.createDivision(
                fastTrack.getId(), "Mostra", "mostra", ScoringSystem.MJP,
                LocalDateTime.of(2026, 7, 31, 23, 59), "Europe/Lisbon", compAdminId);
        competitionService.updateDivisionEntryLimits(mostra.getId(), 3, 5, 10, compAdminId);
        competitionService.updateDivision(mostra.getId(),
                mostra.getName(), mostra.getShortName(), mostra.getScoringSystem(),
                "FT", compAdminId);
        competitionService.advanceDivisionStatus(mostra.getId(), compAdminId); // → REGISTRATION_OPEN

        // Judges (must not own entries — entrant@ owns them, so COI is clear).
        competitionService.addParticipantByEmail(
                fastTrack.getId(), "judge@example.com", CompetitionRole.JUDGE, compAdminId);
        competitionService.addParticipantByEmail(
                fastTrack.getId(), "judge2@example.com", CompetitionRole.JUDGE, compAdminId);

        // Entrant + 3 RECEIVED entries in M1A. The third carries deliberately
        // long per-criterion + overall comments to stress the scoresheet layout.
        var entrant = userService.findByEmail("entrant@example.com");
        entryService.addCredits(mostra.getId(), entrant.getEmail(), 3, compAdminId);
        var categories = competitionService.findDivisionCategories(mostra.getId());
        var m1a = findCategoryByCode(categories, "M1A");
        var goldEntry = createReceivedProEntry(mostra, entrant, compAdminId, "Mostra Tradicional", m1a,
                Sweetness.DRY, 12.0, Carbonation.STILL, "Wildflower honey");
        var silverEntry = createReceivedProEntry(mostra, entrant, compAdminId, "Mostra Reserva", m1a,
                Sweetness.MEDIUM, 13.0, Carbonation.STILL, "Heather honey");
        var verboseEntry = createReceivedProEntry(mostra, entrant, compAdminId, "Mostra Loquaz", m1a,
                Sweetness.SWEET, 14.5, Carbonation.PETILLANT, "Orange blossom honey");

        // REGISTRATION_OPEN → REGISTRATION_CLOSED → init judging cats + assign → JUDGING
        competitionService.advanceDivisionStatus(mostra.getId(), compAdminId); // → REGISTRATION_CLOSED
        competitionService.initializeJudgingCategories(mostra.getId(), compAdminId);
        var m1aJudging = findCategoryByCode(
                competitionService.findJudgingCategories(mostra.getId()), "M1A");
        for (var entry : entryService.findEntriesByDivision(mostra.getId())) {
            entryService.assignFinalCategory(entry.getId(), m1aJudging.getId(), compAdminId);
        }
        competitionService.advanceDivisionStatus(mostra.getId(), compAdminId); // → JUDGING

        // Scoring round: physical table + 2 judges + both entries.
        var judging = judgingService.ensureJudgingExists(mostra.getId());
        var table = judgingService.createPhysicalTable(mostra.getId(), "Table 1", compAdminId);
        var judge1Id = userService.findByEmail("judge@example.com").getId();
        var judge2Id = userService.findByEmail("judge2@example.com").getId();
        var round = judgingService.createRound(
                judging.getId(), "Mostra M1A", m1aJudging.getId(), null, compAdminId);
        judgingService.assignRoundToPhysicalTable(round.getId(), table.getId(), compAdminId);
        judgingService.assignJudge(round.getId(), judge1Id, compAdminId);
        judgingService.assignJudge(round.getId(), judge2Id, compAdminId);
        for (var entry : entryService.findEntriesByFinalCategoryId(m1aJudging.getId())) {
            judgingService.assignEntryToRound(round.getId(), entry.getId(), compAdminId);
        }
        judgingService.startRound(round.getId(), compAdminId); // creates BLANK scoresheets

        // Fill + finalize: judge1 scores every sheet, then the round is finalized.
        // The "Loquaz" entry gets long comments to stress the scoresheet layout.
        for (var sheet : scoresheetService.findByRoundId(round.getId())) {
            if (sheet.getEntryId().equals(verboseEntry.getId())) {
                fillScoresheetVerbose(sheet.getId(), judge1Id);
            } else {
                fillScoresheet(sheet.getId(), judge1Id);
            }
        }
        scoresheetService.finalizeScoringRound(round.getId(), compAdminId); // → COMPLETE, totals locked

        // Finalizing the scoring round auto-creates a READY COMPARATIVE medal
        // round for the category. Run it (admin steps in for the judges): award
        // GOLD / SILVER / BRONZE, then complete it so BOS can start.
        var medalRound = judgingService.findRoundsByDivisionAndCategory(mostra.getId(), m1aJudging.getId())
                .stream()
                .filter(r -> r.getType() == RoundType.MEDAL)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Expected an auto-created medal round"));
        judgingService.assignRoundToPhysicalTable(medalRound.getId(), table.getId(), compAdminId);
        judgingService.startRound(medalRound.getId(), compAdminId);
        judgingService.recordMedal(goldEntry.getId(), Medal.GOLD, compAdminId);
        judgingService.recordMedal(silverEntry.getId(), Medal.SILVER, compAdminId);
        judgingService.recordMedal(verboseEntry.getId(), Medal.BRONZE, compAdminId);
        judgingService.completeMedalRoundById(medalRound.getId(), compAdminId);

        // Best of Show: place the confirmed GOLD (bosPlaces defaults to 1), then
        // complete BOS — judging phase flips to COMPLETE.
        judgingService.startBos(mostra.getId(), compAdminId);
        judgingService.recordBosPlacement(mostra.getId(), goldEntry.getId(), 1, compAdminId);
        judgingService.completeBos(mostra.getId(), compAdminId);

        // JUDGING → DELIBERATION → publish (RESULTS_PUBLISHED).
        competitionService.advanceDivisionStatus(mostra.getId(), compAdminId);
        awardsService.publish(mostra.getId(), compAdminId);
        log.info("Fast Track Mostra: published with 2 fully-scored entries "
                + "(entrant@example.com → My Results → scoresheet + PDF)");
    }

    private void fillScoresheet(UUID scoresheetId, UUID judgeUserId) {
        scoresheetService.updateScore(scoresheetId, "Appearance", 10,
                "Bright and clear with an attractive colour.", judgeUserId);
        scoresheetService.updateScore(scoresheetId, "Aroma/Bouquet", 24,
                "Pleasant honey character, clean and inviting.", judgeUserId);
        scoresheetService.updateScore(scoresheetId, "Flavour and Body", 26,
                "Well balanced flavour with a satisfying medium body.", judgeUserId);
        scoresheetService.updateScore(scoresheetId, "Finish", 11,
                "Clean lingering finish with no off-flavours.", judgeUserId);
        scoresheetService.updateScore(scoresheetId, "Overall Impression", 10,
                "An enjoyable, well-made example of the style.", judgeUserId);
        scoresheetService.updateOverallComments(scoresheetId,
                "A solid mead overall; a touch more acidity would lift the balance further.",
                judgeUserId);
        scoresheetService.markFilled(scoresheetId, judgeUserId);
    }

    /**
     * Fills a scoresheet with deliberately long, multi-sentence comments (each
     * well under the 2000-char column limit) so the entrant-scoresheet redesign
     * can be checked against verbose judge feedback — wrapping, overflow, and
     * per-criterion comment height all get exercised.
     */
    private void fillScoresheetVerbose(UUID scoresheetId, UUID judgeUserId) {
        scoresheetService.updateScore(scoresheetId, "Appearance", 11,
                "Pours a deep, luminous amber with distinct copper highlights when held to the light. "
                        + "Brilliant clarity throughout, with no haze, sediment, or stray particulates of any kind. "
                        + "A fine, persistent petillant bead rises steadily from the base of the glass and forms a "
                        + "delicate collar around the edge. The colour is entirely appropriate to the declared style "
                        + "and the orange-blossom honey, and the overall visual impression is genuinely inviting.",
                judgeUserId);
        scoresheetService.updateScore(scoresheetId, "Aroma/Bouquet", 27,
                "The nose is expressive and layered, opening with bright orange-blossom and acacia honey notes that "
                        + "are immediately recognisable and varietally true. Behind the primary honey character sit "
                        + "secondary aromas of candied citrus peel, chamomile, and a faint waxy floral note that adds "
                        + "real complexity. There is a gentle warming alcohol presence that frames the bouquet without "
                        + "ever becoming hot or solventy, and no oxidative, sulphury, or fermentation off-aromas were "
                        + "detected across repeated nosing. A clean, honest, and very attractive aromatic profile.",
                judgeUserId);
        scoresheetService.updateScore(scoresheetId, "Flavour and Body", 28,
                "Flavour delivery follows the nose closely: forward orange-blossom honey, a sweep of candied citrus, "
                        + "and a restrained herbal-floral mid-palate that keeps the sweetness from feeling cloying. "
                        + "The body is full and faintly viscous, consistent with the SWEET designation, yet a lively "
                        + "petillant lift and moderate acidity carry it and prevent any sense of heaviness. Sweetness, "
                        + "acidity, tannin, and alcohol are well integrated; the only nitpick is that the finish hints "
                        + "at a residual sweetness that could be balanced by a touch more perceived acidity. Faults: "
                        + "none. A confident, generous, and well-constructed palate that rewards slow sipping.",
                judgeUserId);
        scoresheetService.updateScore(scoresheetId, "Finish", 12,
                "Long, clean, and satisfying. The honey character lingers well past the swallow, slowly giving way "
                        + "to a drying floral and faintly citrus-pith note that invites the next sip. No metallic, "
                        + "bitter, or hot alcohol tail, and no astringency beyond what the style supports. The "
                        + "petillant carbonation refreshes the palate on the close, leaving it clean rather than "
                        + "sticky despite the residual sweetness.",
                judgeUserId);
        scoresheetService.updateScore(scoresheetId, "Overall Impression", 11,
                "A polished, expressive sweet sparkling mead that clearly showcases the orange-blossom honey while "
                        + "remaining balanced and drinkable. It reads as the work of a careful maker with a clear "
                        + "stylistic intent, and it would show very well on a competition table. With a marginal "
                        + "increase in acidity to offset the residual sweetness it would be close to flawless.",
                judgeUserId);
        scoresheetService.updateOverallComments(scoresheetId,
                "Thank you for entering this mead — it was a genuine pleasure to evaluate and it stood out on the "
                        + "table. To summarise the feedback above: the appearance is excellent, the bouquet is "
                        + "complex and varietally true, the palate is generous and well integrated, and the finish "
                        + "is long and clean. The single most useful adjustment you could make is to nudge the "
                        + "perceived acidity upward slightly; at the current residual sweetness level a little more "
                        + "acid would sharpen the focus, lengthen the finish further, and make the whole package feel "
                        + "even more lively. You might achieve this through a small acid addition to taste before "
                        + "packaging, by blending in a more acidic batch, or by selecting fruit or honey with brighter "
                        + "natural acidity in future. I would also encourage you to keep the petillant carbonation "
                        + "exactly where it is — it is doing a lot of quiet work to keep this sweet style refreshing. "
                        + "Storage and handling appeared faultless: no oxidation, no fermentation off-flavours, and "
                        + "no packaging taints were evident. This is a strong, confident entry that I scored highly, "
                        + "and with the minor refinement noted it has clear medal potential at the highest level. "
                        + "Congratulations on a very well-made mead, and best of luck with this and future batches.",
                judgeUserId);
        scoresheetService.markFilled(scoresheetId, judgeUserId);
    }

    private void removeCategory(UUID divisionId, String code, UUID compAdminId) {
        var categories = competitionService.findDivisionCategories(divisionId);
        categories.stream()
                .filter(c -> code.equals(c.getCode()))
                .findFirst()
                .ifPresent(c -> competitionService.removeDivisionCategory(
                        divisionId, c.getId(), compAdminId));
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
