# Awards Module — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the awards module — publication lifecycle (publish, republish, send announcement) on top of the existing judging module data, with anonymized entrant/public views and a "freeze in place" guard that locks judging mutators once results are published.

**Architecture:** New `app.meads.awards` module with a single `Publication` aggregate (audit trail only — no copied data). Cross-module editing freeze via a new `DivisionStatus.isResultsFrozen()` enum helper consulted by every mutating method in `JudgingService` and `ScoresheetService`. Decoupled publish/republish/announcement: only `sendAnnouncement` triggers emails.

**Tech Stack:** Spring Boot 4, Spring Modulith 2.0.6, Vaadin 25.1.3 (Java Flow), Flyway, Testcontainers, Karibu Testing, Thymeleaf, OpenPDF + ZXing (for `ScoresheetPdfService`).

**Design reference:** `docs/plans/2026-05-12-awards-module-design.md`.

---

## File Map

### New files

| File | Responsibility |
|------|---------------|
| `src/main/java/app/meads/awards/package-info.java` | `@ApplicationModule(allowedDependencies = {...})` |
| `src/main/java/app/meads/awards/Publication.java` | JPA aggregate root, publish/republish factories |
| `src/main/java/app/meads/awards/AwardsService.java` | Public service interface |
| `src/main/java/app/meads/awards/ResultsPublishedEvent.java` | Spring application event record |
| `src/main/java/app/meads/awards/ResultsRepublishedEvent.java` | Spring application event record |
| `src/main/java/app/meads/awards/AnnouncementSentEvent.java` | Spring application event record |
| `src/main/java/app/meads/awards/EntrantResultRow.java` | DTO record for entrant view |
| `src/main/java/app/meads/awards/AdminResultsView.java` | DTO record for admin view |
| `src/main/java/app/meads/awards/PublicResultsView.java` | DTO record for public view |
| `src/main/java/app/meads/awards/AnonymizedScoresheetView.java` | DTO record for scoresheet drill-in |
| `src/main/java/app/meads/awards/internal/PublicationRepository.java` | JPA repository |
| `src/main/java/app/meads/awards/internal/AwardsServiceImpl.java` | Service impl |
| `src/main/java/app/meads/awards/internal/AwardsAdminView.java` | Admin Vaadin view |
| `src/main/java/app/meads/awards/internal/AwardsPublicResultsView.java` | Public Vaadin view |
| `src/main/java/app/meads/awards/internal/MyScoresheetView.java` | Entrant scoresheet drill-in |
| `src/main/java/app/meads/judging/ScoresheetPdfService.java` | PDF generation (judging public API) |
| `src/main/java/app/meads/judging/AnonymizationLevel.java` | Enum (judging public API) |
| `src/main/resources/db/migration/V28__create_publications.sql` | Schema migration |
| `src/main/resources/email-templates/results-published.html` | Initial-publish email template |
| `src/main/resources/email-templates/results-republished.html` | Republish email template (uses justification as body) |
| `src/main/resources/email-templates/custom-announcement.html` | Custom-message email template |
| `src/test/java/app/meads/awards/PublicationTest.java` | Entity unit tests |
| `src/test/java/app/meads/awards/internal/PublicationRepositoryTest.java` | Repository tests |
| `src/test/java/app/meads/awards/AwardsServiceImplTest.java` | Service unit tests |
| `src/test/java/app/meads/awards/internal/AwardsAdminViewTest.java` | Admin view UI tests |
| `src/test/java/app/meads/awards/internal/AwardsPublicResultsViewTest.java` | Public view UI tests |
| `src/test/java/app/meads/awards/internal/MyScoresheetViewTest.java` | Scoresheet view UI tests |
| `src/test/java/app/meads/awards/AwardsModuleTest.java` | Module integration test |
| `src/test/java/app/meads/judging/ScoresheetPdfServiceTest.java` | PDF service tests |

### Modified files

| File | Change |
|------|--------|
| `src/main/java/app/meads/competition/DivisionStatus.java` | Add `isResultsFrozen()` helper |
| `src/main/java/app/meads/judging/internal/JudgingServiceImpl.java` | Add freeze guard to ~20 mutating methods |
| `src/main/java/app/meads/judging/internal/ScoresheetServiceImpl.java` | Add freeze guard to 9 mutating methods |
| `src/main/java/app/meads/entry/internal/EntryRepository.java` | Add `findDistinctUserIdsByDivisionId` derived query |
| `src/main/java/app/meads/entry/internal/MyEntriesView.java` | Add 4 result columns + "View Scoresheet" button when published |
| `src/main/java/app/meads/judging/internal/JudgingAdminView.java` | Add "Manage Results" header button |
| `src/main/java/app/meads/identity/EmailService.java` | Add `sendResultsAnnouncement(...)` method |
| `src/main/java/app/meads/identity/internal/SmtpEmailService.java` | Implement `sendResultsAnnouncement` |
| `src/main/resources/messages*.properties` | New i18n keys (EN + PT first, ES/IT/PL catch-up at end) |
| `docs/SESSION_CONTEXT.md` | Module status + test count |
| `CLAUDE.md` | Module map + package layout + V28 |
| `docs/walkthrough/manual-test.md` | New Section 13 — awards module |

---

## Task 1: Module skeleton + `Publication` entity + V28 migration

### Step 1: Create package + `@ApplicationModule` declaration

- [ ] Create `src/main/java/app/meads/awards/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"judging", "competition", "entry", "identity"})
package app.meads.awards;
```

- [ ] Create directory `src/main/java/app/meads/awards/internal/` (empty for now — Java directory creation only).

### Step 2: Write failing `PublicationTest`

**File:** `src/test/java/app/meads/awards/PublicationTest.java`

- [ ] Write the test:

```java
package app.meads.awards;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PublicationTest {

    @Test
    void shouldCreateInitialPublicationWithVersion1() {
        var divisionId = UUID.randomUUID();
        var publishedBy = UUID.randomUUID();

        var publication = new Publication(divisionId, publishedBy);

        assertThat(publication.getId()).isNotNull();
        assertThat(publication.getDivisionId()).isEqualTo(divisionId);
        assertThat(publication.getVersion()).isEqualTo(1);
        assertThat(publication.getPublishedBy()).isEqualTo(publishedBy);
        assertThat(publication.getJustification()).isNull();
        assertThat(publication.isInitial()).isTrue();
    }

    @Test
    void shouldCreateRepublishWithIncrementedVersionAndJustification() {
        var divisionId = UUID.randomUUID();
        var publishedBy = UUID.randomUUID();

        var publication = Publication.republish(divisionId, 3,
                "Corrected silver medal in M1A — judge error.", publishedBy);

        assertThat(publication.getVersion()).isEqualTo(4);
        assertThat(publication.getJustification())
                .isEqualTo("Corrected silver medal in M1A — judge error.");
        assertThat(publication.isInitial()).isFalse();
    }

    @Test
    void shouldRejectRepublishWithBlankJustification() {
        assertThatThrownBy(() -> Publication.republish(UUID.randomUUID(), 1, "  ", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justification");
    }

    @Test
    void shouldRejectRepublishWithNullJustification() {
        assertThatThrownBy(() -> Publication.republish(UUID.randomUUID(), 1, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] Run: `mvn test -Dtest=PublicationTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: FAIL — `Publication` does not exist.

### Step 3: Create the `Publication` entity (GREEN)

**File:** `src/main/java/app/meads/awards/Publication.java`

- [ ] Write the entity:

```java
package app.meads.awards;

import jakarta.persistence.*;
import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publications")
@Getter
public class Publication {

    @Id
    private UUID id;

    @Column(name = "division_id", nullable = false)
    private UUID divisionId;

    @Column(nullable = false)
    private int version;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", nullable = false)
    private UUID publishedBy;

    @Column(columnDefinition = "TEXT")
    private String justification;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    protected Publication() {
    }

    public Publication(UUID divisionId, UUID publishedBy) {
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.version = 1;
        this.publishedBy = publishedBy;
        this.justification = null;
        this.initial = true;
    }

    public static Publication republish(UUID divisionId, int previousVersion,
                                         String justification, UUID publishedBy) {
        if (justification == null || Strings.isBlank(justification)) {
            throw new IllegalArgumentException("justification is required for republish");
        }
        var p = new Publication();
        p.id = UUID.randomUUID();
        p.divisionId = divisionId;
        p.version = previousVersion + 1;
        p.publishedBy = publishedBy;
        p.justification = justification;
        p.initial = false;
        return p;
    }

    @PrePersist
    void onCreate() {
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
    }
}
```

> Note: This project's existing entities use `@Getter` (Lombok), `@PrePersist` for timestamps, `UUID.randomUUID()` in the constructor, and a protected no-arg constructor for JPA. The boolean field name `initial` (rather than `isInitial`) keeps Lombok's `@Getter` generating `isInitial()` for boolean fields. Verify by checking generated getter name in a quick test print or rely on Lombok convention.

- [ ] Run: `mvn test -Dtest=PublicationTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS.

### Step 4: Create V28 migration

**File:** `src/main/resources/db/migration/V28__create_publications.sql`

- [ ] Write the migration:

```sql
CREATE TABLE publications (
    id UUID PRIMARY KEY,
    division_id UUID NOT NULL REFERENCES divisions(id),
    version INT NOT NULL CHECK (version >= 1),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_by UUID NOT NULL REFERENCES users(id),
    justification TEXT,
    is_initial BOOLEAN NOT NULL,
    UNIQUE (division_id, version)
);

CREATE INDEX idx_publications_division_id ON publications(division_id);
```

### Step 5: Commit

- [ ] Commit:

```bash
git add src/main/java/app/meads/awards/package-info.java \
        src/main/java/app/meads/awards/Publication.java \
        src/main/resources/db/migration/V28__create_publications.sql \
        src/test/java/app/meads/awards/PublicationTest.java
git commit -m "Awards module Phase 1: Publication entity + V28 migration"
```

---

## Task 2: `PublicationRepository` (repository test)

### Step 1: Write failing repository test

**File:** `src/test/java/app/meads/awards/internal/PublicationRepositoryTest.java`

- [ ] Write the test following the existing repository-test pattern (`@SpringBootTest` + `@Transactional` + `@Import(TestcontainersConfiguration.class)`):

```java
package app.meads.awards.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.awards.Publication;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class PublicationRepositoryTest {

    @Autowired PublicationRepository publicationRepository;
    @Autowired EntityManager entityManager;

    @Test
    void shouldSaveAndFindLatestPublicationByDivisionId() {
        var divisionId = UUID.randomUUID();
        var publishedBy = UUID.randomUUID();

        // NOTE: division and user FKs are real in V28 — for the repository test we
        // use raw UUIDs and rely on Hibernate skipping FK checks in the same tx? No:
        // FKs are enforced. Instead, insert a Division + User through their
        // repositories or use a TestEntityManager helper. For simplicity, this test
        // creates a User and Division via their JPA repositories (use whatever
        // helper or constructor matches the existing patterns in CompetitionTest /
        // UserTest).
        // Concrete approach: call the existing seedHelper if one exists, otherwise
        // create with new Competition(...) / new Division(...) / new User(...) and
        // entityManager.persist them.

        // Pseudo-setup (adapt to existing helpers):
        // var user = ...; entityManager.persist(user); publishedBy = user.getId();
        // var competition = ...; entityManager.persist(competition);
        // var division = new Division(competition.getId(), ...); entityManager.persist(division);
        // divisionId = division.getId();

        var initial = new Publication(divisionId, publishedBy);
        publicationRepository.save(initial);
        var republished = Publication.republish(divisionId, 1, "fix typo", publishedBy);
        publicationRepository.save(republished);

        var latest = publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId);
        assertThat(latest).isPresent();
        assertThat(latest.get().getVersion()).isEqualTo(2);
    }

    @Test
    void shouldReturnHistoryOrderedByVersionAsc() {
        // Similar setup as above, then assert findByDivisionIdOrderByVersionAsc returns [v1, v2, v3]
    }

    @Test
    void shouldReturnTrueWhenAnyPublicationExistsForDivision() {
        // Setup, save one publication, assert existsByDivisionId returns true
    }
}
```

> **Note for the implementer:** the FK constraints on `publications.division_id` and
> `publications.published_by` mean the test must create real `divisions` and `users` rows
> through their JPA repositories first. Check `CompetitionRepositoryTest` and
> `EntryRepositoryTest` for the existing patterns — both seed divisions/users through
> their own repositories. Adapt the same pattern here (or inject `CompetitionRepository`,
> `UserRepository`, `DivisionRepository` and create real entities).

- [ ] Run: `mvn test -Dtest=PublicationRepositoryTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: FAIL — `PublicationRepository` does not exist.

### Step 2: Create `PublicationRepository`

**File:** `src/main/java/app/meads/awards/internal/PublicationRepository.java`

```java
package app.meads.awards.internal;

import app.meads.awards.Publication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PublicationRepository extends JpaRepository<Publication, UUID> {

    Optional<Publication> findTopByDivisionIdOrderByVersionDesc(UUID divisionId);

    List<Publication> findByDivisionIdOrderByVersionAsc(UUID divisionId);

    boolean existsByDivisionId(UUID divisionId);
}
```

- [ ] Run: `mvn test -Dtest=PublicationRepositoryTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS.

### Step 3: Commit

```bash
git add src/main/java/app/meads/awards/internal/PublicationRepository.java \
        src/test/java/app/meads/awards/internal/PublicationRepositoryTest.java
git commit -m "Awards module Phase 2: PublicationRepository"
```

---

## Task 3: `DivisionStatus.isResultsFrozen()` + judging freeze guards

### Step 1: Add the enum helper (unit-test in passing — included in existing DivisionStatusTest if one exists)

**File:** `src/main/java/app/meads/competition/DivisionStatus.java`

- [ ] Add the helper method (place after `allowsJudgingCategoryManagement()`):

```java
public boolean isResultsFrozen() {
    return this == RESULTS_PUBLISHED;
}
```

### Step 2: Add i18n key

**Files:**
- `src/main/resources/messages.properties`
- `src/main/resources/messages_pt.properties`

- [ ] Add to `messages.properties`:

```properties
error.judging.results-published-frozen=Results have been published — judging data cannot be modified. Revert the publication first.
```

- [ ] Add to `messages_pt.properties` (escape non-ASCII as `\\uXXXX` per project convention — use `/tmp/escape_non_ascii.py` from prior i18n work if available, otherwise use `native2ascii` or escape by hand):

```properties
error.judging.results-published-frozen=Os resultados foram publicados — os dados de avaliação não podem ser modificados. Reverta a publicação primeiro.
```

> ES/IT/PL deferred to the final i18n catch-up cycle.

### Step 3: Write failing test for one mutator (canary)

Pick one canonical mutating method — `JudgingService.createTable` — and write a failing test that asserts the frozen guard:

**File:** `src/test/java/app/meads/judging/JudgingServiceFreezeGuardTest.java`

```java
package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.judging.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class JudgingServiceFreezeGuardTest {

    @Mock CompetitionService competitionService;
    // ...other JudgingServiceImpl collaborators mocked similarly to existing
    //    JudgingServiceImpl unit tests; copy the pattern from JudgingServiceImplTest

    @Test
    void shouldRejectCreateTableWhenResultsPublished() {
        var divisionId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var service = /* construct JudgingServiceImpl with the mocks */;

        assertThatThrownBy(() -> service.createTable(divisionId, UUID.randomUUID(), "T1", UUID.randomUUID(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.judging.results-published-frozen");
    }
}
```

- [ ] Run: `mvn test -Dtest=JudgingServiceFreezeGuardTest#shouldRejectCreateTableWhenResultsPublished -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: FAIL — no frozen guard in `createTable` yet.

### Step 4: Add the freeze guard to `JudgingServiceImpl` and `ScoresheetServiceImpl`

**File:** `src/main/java/app/meads/judging/internal/JudgingServiceImpl.java`

- [ ] At the top of every mutating method, after auth check, add:

```java
var division = competitionService.findDivisionById(divisionId);
if (division.getStatus().isResultsFrozen()) {
    throw new BusinessRuleException("error.judging.results-published-frozen");
}
```

> If `division` is already fetched earlier in the method, reuse the variable. Some methods accept arguments other than `divisionId` (e.g., `recordMedal(divisionCategoryId, entryId, medal, userId)`) — resolve `divisionId` from the existing in-method lookup (`competitionService.findDivisionCategoryById(divisionCategoryId).getDivisionId()` or similar — see existing implementation for the exact path).

Methods to update in `JudgingServiceImpl`:
`createTable`, `updateTableName`, `updateTableScheduledDate`, `deleteTable`,
`assignJudge`, `removeJudge`, `startTable`, `configureCategoryMedalRound`,
`startMedalRound`, `completeMedalRound`, `reopenMedalRound`, `resetMedalRound`,
`recordMedal`, `updateMedal`, `deleteMedalAward`, `startBos`, `completeBos`,
`reopenBos`, `resetBos`, `recordBosPlacement`, `updateBosPlacement`, `deleteBosPlacement`.

**File:** `src/main/java/app/meads/judging/internal/ScoresheetServiceImpl.java`

Methods to update in `ScoresheetServiceImpl`:
`createScoresheetsForTable`, `ensureScoresheetForEntry`, `updateScore`,
`updateOverallComments`, `setAdvancedToMedalRound`, `setCommentLanguage`,
`submit`, `revertToDraft`, `moveToTable`.

### Step 5: Write tests for each mutator's freeze guard

- [ ] Add a `shouldReject<Method>WhenResultsPublished` test for each mutating method (~30 tests total). Pattern is identical to Step 3's canary; copy the test body and adapt the method invocation.

- [ ] Run: `mvn test -Dtest=JudgingServiceFreezeGuardTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS for all.

### Step 6: Run full judging test suite to confirm no regressions

- [ ] Run: `mvn test -Dtest="app.meads.judging.**" -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: All pass.

### Step 7: Commit

```bash
git add src/main/java/app/meads/competition/DivisionStatus.java \
        src/main/java/app/meads/judging/internal/JudgingServiceImpl.java \
        src/main/java/app/meads/judging/internal/ScoresheetServiceImpl.java \
        src/main/resources/messages.properties \
        src/main/resources/messages_pt.properties \
        src/test/java/app/meads/judging/JudgingServiceFreezeGuardTest.java
git commit -m "Awards module Phase 3: judging mutators rejected when results published"
```

---

## Task 4: `AwardsService.publish` (unit + integration)

### Step 1: Write failing unit test

**File:** `src/test/java/app/meads/awards/AwardsServiceImplTest.java`

```java
package app.meads.awards;

import app.meads.BusinessRuleException;
import app.meads.awards.internal.AwardsServiceImpl;
import app.meads.awards.internal.PublicationRepository;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AwardsServiceImplTest {

    @Mock PublicationRepository publicationRepository;
    @Mock CompetitionService competitionService;
    @Mock ApplicationEventPublisher eventPublisher;
    // (other collaborators added as later tests need them)

    @Test
    void shouldPublishInitialResults() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.DELIBERATION);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId))
                .willReturn(Optional.empty());
        given(publicationRepository.save(any(Publication.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var publication = service.publish(divisionId, adminUserId);

        assertThat(publication.getVersion()).isEqualTo(1);
        assertThat(publication.isInitial()).isTrue();
        then(competitionService).should().advanceDivisionStatus(divisionId, adminUserId);
        var captor = ArgumentCaptor.forClass(ResultsPublishedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().divisionId()).isEqualTo(divisionId);
        assertThat(captor.getValue().version()).isEqualTo(1);
    }

    @Test
    void shouldRejectPublishWhenStatusNotDeliberation() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.JUDGING);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.publish(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.publish-wrong-status");
    }

    @Test
    void shouldRejectPublishWhenUnauthorized() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.publish(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.unauthorized");
    }

    private AwardsServiceImpl createService() {
        return new AwardsServiceImpl(
                publicationRepository, competitionService,
                /* entryService */ null, /* entryRepository */ null,
                /* judgingService */ null, /* scoresheetService */ null,
                /* userService */ null, /* emailService */ null,
                eventPublisher);
    }
}
```

- [ ] Run: `mvn test -Dtest=AwardsServiceImplTest#shouldPublishInitialResults -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: FAIL — `AwardsService` and impl don't exist.

### Step 2: Create `AwardsService` interface

**File:** `src/main/java/app/meads/awards/AwardsService.java`

```java
package app.meads.awards;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardsService {

    Publication publish(UUID divisionId, UUID adminUserId);

    Publication republish(UUID divisionId, String justification, UUID adminUserId);

    void sendAnnouncement(UUID divisionId, String customMessage, UUID adminUserId);

    Optional<Publication> getLatestPublication(UUID divisionId);

    List<Publication> getPublicationHistory(UUID divisionId);

    List<EntrantResultRow> getResultsForEntrant(UUID userId, UUID divisionId);

    AdminResultsView getResultsForAdmin(UUID divisionId, UUID adminUserId);

    PublicResultsView getPublicResults(String competitionShortName, String divisionShortName);

    AnonymizedScoresheetView getAnonymizedScoresheet(UUID scoresheetId, UUID requestingUserId);
}
```

### Step 3: Create event records (just `ResultsPublishedEvent` for now; others as later tasks need them)

**File:** `src/main/java/app/meads/awards/ResultsPublishedEvent.java`

```java
package app.meads.awards;

import java.time.Instant;
import java.util.UUID;

public record ResultsPublishedEvent(
        UUID divisionId,
        UUID publicationId,
        int version,
        Instant publishedAt,
        UUID publishedBy) {
}
```

### Step 4: Create `AwardsServiceImpl` with `publish` only (GREEN)

**File:** `src/main/java/app/meads/awards/internal/AwardsServiceImpl.java`

```java
package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.awards.*;
import app.meads.competition.CompetitionService;
import app.meads.competition.DivisionStatus;
import app.meads.entry.EntryService;
import app.meads.entry.internal.EntryRepository;  // package-private; needs adjustment, see note below
import app.meads.identity.EmailService;
import app.meads.identity.UserService;
import app.meads.judging.JudgingService;
import app.meads.judging.ScoresheetService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

@Slf4j
@Service
@Transactional
@Validated
public class AwardsServiceImpl implements AwardsService {

    private final PublicationRepository publicationRepository;
    private final CompetitionService competitionService;
    private final EntryService entryService;
    // NOTE: EntryRepository is package-private in entry.internal. To avoid breaking
    // the module boundary, add a public method on EntryService (or a new public
    // interface in entry module) for `findDistinctUserIdsByDivisionId`. See Task 7
    // step 1 for the cleanest extraction. Until then, this field is omitted from
    // the constructor.
    private final JudgingService judgingService;
    private final ScoresheetService scoresheetService;
    private final UserService userService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    AwardsServiceImpl(PublicationRepository publicationRepository,
                       CompetitionService competitionService,
                       EntryService entryService,
                       JudgingService judgingService,
                       ScoresheetService scoresheetService,
                       UserService userService,
                       EmailService emailService,
                       ApplicationEventPublisher eventPublisher) {
        this.publicationRepository = publicationRepository;
        this.competitionService = competitionService;
        this.entryService = entryService;
        this.judgingService = judgingService;
        this.scoresheetService = scoresheetService;
        this.userService = userService;
        this.emailService = emailService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Publication publish(@NotNull UUID divisionId, @NotNull UUID adminUserId) {
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.awards.unauthorized");
        }
        var division = competitionService.findDivisionById(divisionId);
        if (division.getStatus() != DivisionStatus.DELIBERATION) {
            throw new BusinessRuleException("error.awards.publish-wrong-status");
        }
        if (publicationRepository.existsByDivisionId(divisionId)) {
            throw new BusinessRuleException("error.awards.already-published");
        }
        var publication = publicationRepository.save(new Publication(divisionId, adminUserId));
        competitionService.advanceDivisionStatus(divisionId, adminUserId);
        eventPublisher.publishEvent(new ResultsPublishedEvent(
                divisionId, publication.getId(), publication.getVersion(),
                publication.getPublishedAt(), publication.getPublishedBy()));
        log.info("Published results for division {} (version {})", divisionId, publication.getVersion());
        return publication;
    }

    // republish, sendAnnouncement, read methods — stubbed (UnsupportedOperationException) for now
    @Override public Publication republish(UUID divisionId, String justification, UUID adminUserId) {
        throw new UnsupportedOperationException();
    }
    @Override public void sendAnnouncement(UUID divisionId, String customMessage, UUID adminUserId) {
        throw new UnsupportedOperationException();
    }
    @Override public Optional<Publication> getLatestPublication(UUID divisionId) {
        throw new UnsupportedOperationException();
    }
    @Override public List<Publication> getPublicationHistory(UUID divisionId) {
        throw new UnsupportedOperationException();
    }
    @Override public List<EntrantResultRow> getResultsForEntrant(UUID userId, UUID divisionId) {
        throw new UnsupportedOperationException();
    }
    @Override public AdminResultsView getResultsForAdmin(UUID divisionId, UUID adminUserId) {
        throw new UnsupportedOperationException();
    }
    @Override public PublicResultsView getPublicResults(String c, String d) {
        throw new UnsupportedOperationException();
    }
    @Override public AnonymizedScoresheetView getAnonymizedScoresheet(UUID scoresheetId, UUID requestingUserId) {
        throw new UnsupportedOperationException();
    }
}
```

### Step 5: Add i18n keys

- [ ] Add to `messages.properties`:

```properties
error.awards.unauthorized=You are not authorized to manage results for this division.
error.awards.publish-wrong-status=Results can only be published when the division is in Deliberation.
error.awards.already-published=Results have already been published for this division.
```

- [ ] Add Portuguese translations (escape non-ASCII):

```properties
error.awards.unauthorized=Não tem autorização para gerir os resultados desta divisão.
error.awards.publish-wrong-status=Os resultados só podem ser publicados quando a divisão está em Deliberação.
error.awards.already-published=Os resultados já foram publicados para esta divisão.
```

- [ ] Run: `mvn test -Dtest=AwardsServiceImplTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS for publish tests.

### Step 6: Commit

```bash
git add src/main/java/app/meads/awards/AwardsService.java \
        src/main/java/app/meads/awards/ResultsPublishedEvent.java \
        src/main/java/app/meads/awards/internal/AwardsServiceImpl.java \
        src/main/resources/messages.properties \
        src/main/resources/messages_pt.properties \
        src/test/java/app/meads/awards/AwardsServiceImplTest.java
git commit -m "Awards module Phase 4: AwardsService.publish"
```

---

## Task 5: `AwardsService.republish`

### Step 1: Write failing test

- [ ] Add tests to `AwardsServiceImplTest`:

```java
@Test
void shouldRepublishWithIncrementedVersion() {
    var service = createService();
    var divisionId = UUID.randomUUID();
    var adminUserId = UUID.randomUUID();
    var division = mock(Division.class);
    given(division.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
    given(competitionService.findDivisionById(divisionId)).willReturn(division);
    given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
    var existing = new Publication(divisionId, adminUserId);
    given(publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId))
            .willReturn(Optional.of(existing));
    given(publicationRepository.save(any(Publication.class)))
            .willAnswer(inv -> inv.getArgument(0));

    var publication = service.republish(divisionId,
            "Fixed gold medal in M1A — judge re-scored after spreadsheet error.", adminUserId);

    assertThat(publication.getVersion()).isEqualTo(2);
    assertThat(publication.isInitial()).isFalse();
    assertThat(publication.getJustification()).contains("Fixed gold medal");
    then(competitionService).should(never()).advanceDivisionStatus(any(), any());

    var captor = ArgumentCaptor.forClass(ResultsRepublishedEvent.class);
    then(eventPublisher).should().publishEvent(captor.capture());
    assertThat(captor.getValue().version()).isEqualTo(2);
}

@Test
void shouldRejectRepublishWhenStatusNotPublished() {
    var service = createService();
    var divisionId = UUID.randomUUID();
    var adminUserId = UUID.randomUUID();
    var division = mock(Division.class);
    given(division.getStatus()).willReturn(DivisionStatus.DELIBERATION);
    given(competitionService.findDivisionById(divisionId)).willReturn(division);
    given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

    assertThatThrownBy(() -> service.republish(divisionId, "valid justification text", adminUserId))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("error.awards.republish-wrong-status");
}

@Test
void shouldRejectRepublishWithJustificationTooShort() {
    var service = createService();
    var divisionId = UUID.randomUUID();
    var adminUserId = UUID.randomUUID();
    var division = mock(Division.class);
    given(division.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
    given(competitionService.findDivisionById(divisionId)).willReturn(division);
    given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

    assertThatThrownBy(() -> service.republish(divisionId, "short", adminUserId))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("error.awards.justification-too-short");
}
```

- [ ] Run: `mvn test -Dtest=AwardsServiceImplTest#shouldRepublishWithIncrementedVersion -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: FAIL — `UnsupportedOperationException`.

### Step 2: Create `ResultsRepublishedEvent` + implement `republish`

**File:** `src/main/java/app/meads/awards/ResultsRepublishedEvent.java`

```java
package app.meads.awards;

import java.time.Instant;
import java.util.UUID;

public record ResultsRepublishedEvent(
        UUID divisionId,
        UUID publicationId,
        int version,
        Instant publishedAt,
        UUID publishedBy,
        String justification) {
}
```

- [ ] Replace the `republish` stub in `AwardsServiceImpl`:

```java
private static final int JUSTIFICATION_MIN_LENGTH = 20;
private static final int JUSTIFICATION_MAX_LENGTH = 1000;

@Override
public Publication republish(@NotNull UUID divisionId, @NotBlank String justification, @NotNull UUID adminUserId) {
    if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
        throw new BusinessRuleException("error.awards.unauthorized");
    }
    var division = competitionService.findDivisionById(divisionId);
    if (division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
        throw new BusinessRuleException("error.awards.republish-wrong-status");
    }
    var trimmed = justification.trim();
    if (trimmed.length() < JUSTIFICATION_MIN_LENGTH) {
        throw new BusinessRuleException("error.awards.justification-too-short");
    }
    if (trimmed.length() > JUSTIFICATION_MAX_LENGTH) {
        throw new BusinessRuleException("error.awards.justification-too-long");
    }
    var previous = publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId)
            .orElseThrow(() -> new BusinessRuleException("error.awards.no-prior-publication"));
    var publication = publicationRepository.save(
            Publication.republish(divisionId, previous.getVersion(), trimmed, adminUserId));
    eventPublisher.publishEvent(new ResultsRepublishedEvent(
            divisionId, publication.getId(), publication.getVersion(),
            publication.getPublishedAt(), publication.getPublishedBy(), trimmed));
    log.info("Republished results for division {} (version {})", divisionId, publication.getVersion());
    return publication;
}
```

- [ ] Add i18n keys (EN + PT) for `error.awards.republish-wrong-status`,
      `error.awards.justification-too-short`, `error.awards.justification-too-long`,
      `error.awards.no-prior-publication`.

- [ ] Run: `mvn test -Dtest=AwardsServiceImplTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS.

### Step 3: Commit

```bash
git add src/main/java/app/meads/awards/ResultsRepublishedEvent.java \
        src/main/java/app/meads/awards/internal/AwardsServiceImpl.java \
        src/main/resources/messages.properties \
        src/main/resources/messages_pt.properties \
        src/test/java/app/meads/awards/AwardsServiceImplTest.java
git commit -m "Awards module Phase 5: AwardsService.republish"
```

---

## Task 6: Read methods + DTOs

This is the largest service-side cycle. Implement read methods one at a time, each with its own test.

### Step 1: DTOs

**Files:**

```java
// src/main/java/app/meads/awards/EntrantResultRow.java
package app.meads.awards;

import app.meads.entry.EntryStatus;
import app.meads.judging.Medal;

import java.util.UUID;

public record EntrantResultRow(
        UUID entryId,
        String entryNumber,
        String meadName,
        String categoryCode,
        String categoryName,
        EntryStatus status,
        Integer round1Total,
        boolean advancedToMedalRound,
        Medal medal,           // null = withheld OR no medal awarded; entrant view renders both as "—"
        Integer bosPlace,      // nullable
        UUID scoresheetId      // primary scoresheet for drill-in (oldest by submittedAt); null if none submitted
) {}
```

```java
// src/main/java/app/meads/awards/AdminResultsView.java
package app.meads.awards;

import java.util.List;
import java.util.UUID;

public record AdminResultsView(
        UUID divisionId,
        String divisionName,
        String competitionName,
        String divisionStatusKey,                 // e.g. "DELIBERATION", "RESULTS_PUBLISHED"
        List<AdminCategoryLeaderboard> categories,
        List<AdminBosRow> bosLeaderboard,
        List<PublicationSummary> publicationHistory) {

    public record AdminCategoryLeaderboard(
            UUID divisionCategoryId, String categoryCode, String categoryName,
            List<AdminEntryRow> rows) {}

    public record AdminEntryRow(
            UUID entryId, String entryNumber, String entrantName, String meaderyName,
            String meadName, Integer round1Total, boolean advancedToMedalRound,
            String medalLabel,                    // "GOLD" / "SILVER" / "BRONZE" / "Withheld" / "—"
            Integer bosPlace) {}

    public record AdminBosRow(
            int place, UUID entryId, String entryNumber, String entrantName,
            String meaderyName, String meadName, String originatingCategoryCode) {}

    public record PublicationSummary(
            int version, java.time.Instant publishedAt, String publishedByDisplayName,
            String justification, boolean initial) {}
}
```

```java
// src/main/java/app/meads/awards/PublicResultsView.java
package app.meads.awards;

import java.util.List;

public record PublicResultsView(
        String competitionName,
        String divisionName,
        java.time.Instant lastUpdatedAt,
        boolean hasMultiplePublications,           // controls whether to show "Last updated" footer
        List<PublicCategorySection> categories,
        List<PublicBosRow> bosLeaderboard) {

    public record PublicCategorySection(
            String categoryCode, String categoryName,
            List<PublicMedalRow> golds,
            List<PublicMedalRow> silvers,
            List<PublicMedalRow> bronzes) {}

    public record PublicMedalRow(String meadName, String meaderyName) {}

    public record PublicBosRow(int place, String meadName, String meaderyName) {}
}
```

```java
// src/main/java/app/meads/awards/AnonymizedScoresheetView.java
package app.meads.awards;

import java.util.List;
import java.util.UUID;

public record AnonymizedScoresheetView(
        UUID scoresheetId,
        UUID entryId,
        String entryNumber,
        String meadName,
        String categoryCode,
        String categoryName,
        List<AnonymizedScoresheet> scoresheets   // one per judge, anonymized
) {

    public record AnonymizedScoresheet(
            int judgeOrdinal,                      // 1, 2, 3 — stable by submittedAt asc
            String commentLanguage,
            Integer totalScore,
            List<FieldScore> fieldScores,
            String overallComments) {}

    public record FieldScore(
            String fieldName,                      // canonical English key, e.g. "Appearance"
            int value,
            int maxValue,
            String tierLabel) {}
}
```

### Step 2: Tests + implementation per read method

For each of the following methods, write one failing test then implement (TDD):

- `getLatestPublication` — wraps `publicationRepository.findTopByDivisionIdOrderByVersionDesc`. Test: returns `Optional.empty` when none, returns latest version when multiple.
- `getPublicationHistory` — wraps `publicationRepository.findByDivisionIdOrderByVersionAsc`. Test: returns list ordered by version asc.
- `getResultsForEntrant(userId, divisionId)` — uses `entryService.findEntriesByUserAndDivision(userId, divisionId)`, then for each entry calls `scoresheetService.findByEntryId(entryId)` and `judgingService.findMedalAwardByEntryId`, `judgingService.findBosPlacementByEntryId`. Test: returns rows matching entry list; status=RESULTS_PUBLISHED required (throws `error.awards.not-published`).
- `getResultsForAdmin(divisionId, adminUserId)` — auth check; assembles `AdminResultsView` by category. Test: rows sorted by round1Total desc; medal/BOS fields populated; publication history included.
- `getPublicResults(competitionShortName, divisionShortName)` — looks up competition + division by short name; throws if `division.status != RESULTS_PUBLISHED` (test: throws `error.awards.not-published`).
- `getAnonymizedScoresheet(scoresheetId, requestingUserId)` — authorization: (a) admin → allowed, (b) entry owner → allowed, (c) other → `error.awards.unauthorized`. Builds `AnonymizedScoresheetView` with all scoresheets for the entry, sorted by `submittedAt asc`, ordinals 1..N, judge ids stripped.

> **Implementation note:** Some of these methods will require new helper methods on
> `JudgingService` and `ScoresheetService` that don't exist yet:
> - `JudgingService.findMedalAwardByEntryId(entryId)` — likely already exists per design §3.2 (check `JudgingService` interface)
> - `JudgingService.findBosPlacementByEntryId(entryId)` — likely already exists
> - `ScoresheetService.findByEntryId(entryId)` — needs adding if missing
> - `ScoresheetService.findByEntryIdOrderBySubmittedAtAsc(entryId)` — needed for stable ordinals
> Audit which helpers exist before implementing; add new public-API methods if missing,
> following the existing service interface conventions.

### Step 3: Add error-key i18n

Add to `messages.properties` (and PT):

```properties
error.awards.not-published=Results for this division have not been published yet.
error.awards.scoresheet-not-found=Scoresheet not found.
```

### Step 4: Run AwardsServiceImplTest and commit

- [ ] Run: `mvn test -Dtest=AwardsServiceImplTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS for all.

- [ ] Commit:

```bash
git add src/main/java/app/meads/awards/ \
        src/main/java/app/meads/judging/ \
        src/main/java/app/meads/entry/ \
        src/main/resources/messages.properties \
        src/main/resources/messages_pt.properties \
        src/test/java/app/meads/awards/AwardsServiceImplTest.java
git commit -m "Awards module Phase 6: read methods + DTOs"
```

---

## Task 7: `sendAnnouncement` + email templates

### Step 1: Add `findDistinctUserIdsByDivisionId` to entry module

The cleanest path: add a derived query on `EntryRepository` AND a public delegating
method on `EntryService` (so the awards module doesn't reach into entry internals).

**File:** `src/main/java/app/meads/entry/internal/EntryRepository.java`

```java
@Query("select distinct e.userId from Entry e where e.divisionId = :divisionId")
List<UUID> findDistinctUserIdsByDivisionId(@Param("divisionId") UUID divisionId);
```

**File:** `src/main/java/app/meads/entry/EntryService.java`

```java
public List<UUID> findEntrantUserIdsForDivision(UUID divisionId) {
    return entryRepository.findDistinctUserIdsByDivisionId(divisionId);
}
```

- [ ] Add a unit test in `EntryServiceTest` asserting `findEntrantUserIdsForDivision` delegates correctly.

### Step 2: Add `sendResultsAnnouncement` to `EmailService`

**File:** `src/main/java/app/meads/identity/EmailService.java`

Add the interface method:

```java
void sendResultsAnnouncement(String recipientEmail, Locale locale,
                              ResultsAnnouncementType type,
                              String competitionName, String divisionName,
                              String customOrJustificationBody,   // null when type=INITIAL_NO_CUSTOM
                              String resultsUrl,
                              String contactEmail);

enum ResultsAnnouncementType {
    INITIAL_NO_CUSTOM,        // uses results-published.html
    REPUBLISH_NO_CUSTOM,      // uses results-republished.html with justification body
    CUSTOM_MESSAGE            // uses custom-announcement.html with admin's body
}
```

> Note: `ResultsAnnouncementType` can be a nested enum on `EmailService` or a top-level
> enum in `app.meads.identity` — either works. Keep it next to `EmailService` for
> discoverability.

### Step 3: Implement in `SmtpEmailService`

**File:** `src/main/java/app/meads/identity/internal/SmtpEmailService.java`

```java
@Override
public void sendResultsAnnouncement(String recipientEmail, Locale locale,
                                     EmailService.ResultsAnnouncementType type,
                                     String competitionName, String divisionName,
                                     String customOrJustificationBody,
                                     String resultsUrl,
                                     String contactEmail) {
    var subjectKey = switch (type) {
        case INITIAL_NO_CUSTOM -> "email.results-published.subject";
        case REPUBLISH_NO_CUSTOM -> "email.results-republished.subject";
        case CUSTOM_MESSAGE -> "email.custom-announcement.subject";
    };
    var templateName = switch (type) {
        case INITIAL_NO_CUSTOM -> "results-published";
        case REPUBLISH_NO_CUSTOM -> "results-republished";
        case CUSTOM_MESSAGE -> "custom-announcement";
    };
    var subject = messageSource.getMessage(subjectKey, new Object[]{competitionName, divisionName}, locale);

    var link = jwtMagicLinkService.generateLink(recipientEmail, TOKEN_VALIDITY);
    var ctx = new Context(locale);
    ctx.setVariable("subject", subject);
    ctx.setVariable("competitionName", competitionName);
    ctx.setVariable("divisionName", divisionName);
    ctx.setVariable("body", customOrJustificationBody);            // null/blank for INITIAL_NO_CUSTOM
    ctx.setVariable("ctaUrl", link + resultsUrl);
    ctx.setVariable("ctaLabel", messageSource.getMessage("email.results.cta-label", null, locale));
    ctx.setVariable("contactEmail", contactEmail);
    sendEmail(recipientEmail, subject, ctx, templateName);
}
```

> Verify `sendEmail` signature in existing `SmtpEmailService` — adapt accordingly. The
> existing pattern in this project passes a template name (without `.html`) plus the
> Thymeleaf `Context` to a private `sendEmail` helper.

### Step 4: Create three Thymeleaf templates

**File:** `src/main/resources/email-templates/results-published.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body th:replace="~{email-base :: layout(content=~{::content})}">
    <th:block th:fragment="content">
        <h1 th:text="#{email.results-published.heading(${competitionName}, ${divisionName})}">Results are available</h1>
        <p th:text="#{email.results-published.body}">Your results for the competition are now available. Click below to view them.</p>
    </th:block>
</body>
</html>
```

**File:** `src/main/resources/email-templates/results-republished.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body th:replace="~{email-base :: layout(content=~{::content})}">
    <th:block th:fragment="content">
        <h1 th:text="#{email.results-republished.heading(${competitionName}, ${divisionName})}">Results updated</h1>
        <p th:text="#{email.results-republished.intro}">The results have been updated. The reason given by the administrator:</p>
        <blockquote style="border-left:4px solid #ccc; padding-left:1em; margin:1em 0;" th:text="${body}">[justification]</blockquote>
        <p th:text="#{email.results-republished.cta-text}">Please review the updated results.</p>
    </th:block>
</body>
</html>
```

**File:** `src/main/resources/email-templates/custom-announcement.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body th:replace="~{email-base :: layout(content=~{::content})}">
    <th:block th:fragment="content">
        <h1 th:text="#{email.custom-announcement.heading(${competitionName}, ${divisionName})}">Announcement</h1>
        <p style="white-space:pre-wrap" th:text="${body}">[admin's custom message]</p>
    </th:block>
</body>
</html>
```

> Match the project's existing template style by inspecting one of the existing templates
> (e.g., `magic-link.html` or `credit-notification.html`) for the exact `email-base`
> fragment invocation.

### Step 5: Write failing `sendAnnouncement` test

Add to `AwardsServiceImplTest`:

```java
@Test
void shouldSendInitialAnnouncementWithDefaultTemplate() {
    var service = createService();
    var divisionId = UUID.randomUUID();
    var adminUserId = UUID.randomUUID();
    var division = mock(Division.class);
    given(division.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
    given(division.getCompetitionId()).willReturn(UUID.randomUUID());
    given(division.getShortName()).willReturn("amadora");
    given(division.getName()).willReturn("Amadora");
    given(competitionService.findDivisionById(divisionId)).willReturn(division);
    given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
    var competition = mock(Competition.class);
    given(competition.getName()).willReturn("CHIP 2026");
    given(competition.getShortName()).willReturn("chip-2026");
    given(competition.getContactEmail()).willReturn("admin@chip.pt");
    given(competitionService.findCompetitionById(any())).willReturn(competition);
    given(publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId))
            .willReturn(Optional.of(new Publication(divisionId, adminUserId)));

    var user1 = UUID.randomUUID();
    var user2 = UUID.randomUUID();
    given(entryService.findEntrantUserIdsForDivision(divisionId))
            .willReturn(List.of(user1, user2));
    var u1 = mock(User.class);
    given(u1.getEmail()).willReturn("a@test");
    given(u1.getPreferredLanguage()).willReturn("en");
    var u2 = mock(User.class);
    given(u2.getEmail()).willReturn("b@test");
    given(u2.getPreferredLanguage()).willReturn("pt");
    given(userService.findById(user1)).willReturn(u1);
    given(userService.findById(user2)).willReturn(u2);

    service.sendAnnouncement(divisionId, null, adminUserId);

    then(emailService).should().sendResultsAnnouncement(
            eq("a@test"), eq(Locale.ENGLISH),
            eq(EmailService.ResultsAnnouncementType.INITIAL_NO_CUSTOM),
            eq("CHIP 2026"), eq("Amadora"),
            isNull(), contains("chip-2026/divisions/amadora/my-entries"),
            eq("admin@chip.pt"));
    then(emailService).should().sendResultsAnnouncement(
            eq("b@test"), eq(Locale.of("pt")),
            eq(EmailService.ResultsAnnouncementType.INITIAL_NO_CUSTOM),
            any(), any(), isNull(), any(), any());
    then(eventPublisher).should().publishEvent(any(AnnouncementSentEvent.class));
}

// Additional tests:
//   - shouldSendRepublishAnnouncementWithJustificationAsBody (latest publication version > 1)
//   - shouldSendCustomMessageAnnouncementWhenMessageNonBlank
//   - shouldRejectSendAnnouncementWhenStatusNotPublished
//   - shouldRejectSendAnnouncementWhenUnauthorized
//   - shouldRejectSendAnnouncementWhenNoPublicationExists
```

### Step 6: Create `AnnouncementSentEvent` + implement `sendAnnouncement`

**File:** `src/main/java/app/meads/awards/AnnouncementSentEvent.java`

```java
package app.meads.awards;

import java.util.UUID;

public record AnnouncementSentEvent(
        UUID divisionId,
        UUID publicationId,
        int recipientCount,
        boolean usedCustomMessage) {}
```

Replace the `sendAnnouncement` stub in `AwardsServiceImpl`:

```java
@Override
public void sendAnnouncement(@NotNull UUID divisionId, String customMessage, @NotNull UUID adminUserId) {
    if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
        throw new BusinessRuleException("error.awards.unauthorized");
    }
    var division = competitionService.findDivisionById(divisionId);
    if (division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
        throw new BusinessRuleException("error.awards.announcement-wrong-status");
    }
    var latest = publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId)
            .orElseThrow(() -> new BusinessRuleException("error.awards.no-prior-publication"));

    var competition = competitionService.findCompetitionById(division.getCompetitionId());
    boolean useCustom = customMessage != null && !customMessage.isBlank();
    EmailService.ResultsAnnouncementType type;
    String body;
    if (useCustom) {
        type = EmailService.ResultsAnnouncementType.CUSTOM_MESSAGE;
        body = customMessage.trim();
    } else if (latest.getVersion() > 1) {
        type = EmailService.ResultsAnnouncementType.REPUBLISH_NO_CUSTOM;
        body = latest.getJustification();
    } else {
        type = EmailService.ResultsAnnouncementType.INITIAL_NO_CUSTOM;
        body = null;
    }
    var resultsUrl = "/competitions/" + competition.getShortName()
            + "/divisions/" + division.getShortName() + "/my-entries";

    var userIds = entryService.findEntrantUserIdsForDivision(divisionId);
    int sent = 0;
    for (var userId : userIds) {
        var user = userService.findById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) continue;
        var locale = LocaleResolver.resolve(user.getPreferredLanguage());
        try {
            emailService.sendResultsAnnouncement(user.getEmail(), locale, type,
                    competition.getName(), division.getName(),
                    body, resultsUrl, competition.getContactEmail());
            sent++;
        } catch (Exception e) {
            log.error("Failed to send announcement to {} for division {}",
                    user.getEmail(), divisionId, e);
        }
    }
    eventPublisher.publishEvent(new AnnouncementSentEvent(
            divisionId, latest.getId(), sent, useCustom));
    log.info("Sent {} results announcements for division {} (publication v{}, type={})",
            sent, divisionId, latest.getVersion(), type);
}
```

> `LocaleResolver.resolve(String)` is assumed to follow the existing pattern in
> `SmtpEmailService` or `MeadsI18NProvider`. Verify the exact helper name —
> `LanguageMapping.resolveLocale(...)` may be the existing path. Adapt the call site.

### Step 7: Add i18n keys for subjects and template bodies

In `messages.properties`:

```properties
email.results-published.subject=Your {0} — {1} results are available
email.results-published.heading=Results are available: {0} — {1}
email.results-published.body=Your results for this competition are now available. Click the button below to view them.

email.results-republished.subject={0} — {1} results have been updated
email.results-republished.heading=Results updated: {0} — {1}
email.results-republished.intro=The results have been updated. The reason given by the administrator:
email.results-republished.cta-text=Please review the updated results.

email.custom-announcement.subject=Update from {0} — {1}
email.custom-announcement.heading=Announcement: {0} — {1}

email.results.cta-label=View results
```

Mirror in `messages_pt.properties` (escape non-ASCII).

ES/IT/PL deferred to the final cycle.

### Step 8: Run and commit

- [ ] Run: `mvn test -Dtest=AwardsServiceImplTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS.

- [ ] Commit:

```bash
git add src/main/java/app/meads/awards/ \
        src/main/java/app/meads/identity/ \
        src/main/java/app/meads/entry/ \
        src/main/resources/messages*.properties \
        src/main/resources/email-templates/*.html \
        src/test/java/app/meads/awards/AwardsServiceImplTest.java
git commit -m "Awards module Phase 7: sendAnnouncement + email templates"
```

---

## Task 8: `ScoresheetPdfService` (judging module)

### Step 1: Create `AnonymizationLevel` enum

**File:** `src/main/java/app/meads/judging/AnonymizationLevel.java`

```java
package app.meads.judging;

public enum AnonymizationLevel {
    ANONYMIZED, FULL
}
```

### Step 2: Failing test for `ScoresheetPdfService`

**File:** `src/test/java/app/meads/judging/ScoresheetPdfServiceTest.java`

```java
package app.meads.judging;

import app.meads.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ScoresheetPdfServiceTest {

    @Test
    void shouldGenerateAnonymizedPdfForEntryOwner() {
        // Build a service with mocked deps (mirrors LabelPdfService pattern).
        // Given: scoresheet exists, requesting user owns the entry.
        // When: generatePdf(scoresheetId, userId, ANONYMIZED, Locale.ENGLISH)
        // Then: returns non-empty byte[], starts with %PDF (PDF signature).
    }

    @Test
    void shouldGenerateFullPdfForAdmin() {
        // SYSTEM_ADMIN → FULL level allowed; returns PDF.
    }

    @Test
    void shouldRejectFullLevelForEntrant() {
        // Entrant requests FULL → BusinessRuleException("error.awards.unauthorized") or similar.
    }

    @Test
    void shouldRejectAnonymizedLevelForNonOwner() {
        // Non-owner requests ANONYMIZED → BusinessRuleException.
    }
}
```

### Step 3: Implement `ScoresheetPdfService`

**File:** `src/main/java/app/meads/judging/ScoresheetPdfService.java`

Mirror the structure of `app.meads.entry.LabelPdfService` (existing): public class
in module root, package-private constructor, `@Service`-annotated, dependencies:
`ScoresheetService`, `JudgingService`, `EntryService` (for entry data), `UserService`
(for judge names in FULL mode), `CompetitionService` (for competition/division names),
`MessageSource` (for tier labels + locale-aware headings).

Use OpenPDF (`com.lowagie.text.*`) and the same `LiberationSans` font setup as
`LabelPdfService` for Unicode support.

Layout (per design §9.1): A4 portrait, single scoresheet per invocation.

> Detailed PDF layout code is non-trivial. Start with a minimal working PDF that
> includes all required text (judge ordinal or name, 5 per-field rows, total,
> comments) and iterate on layout in the REFACTOR step. The judge anonymization
> ordinal must come from the caller (`AnonymizedScoresheetView.judgeOrdinal`), since
> the service receives `scoresheetId` directly — pre-compute the ordinal by sorting
> all scoresheets for the entry by `submittedAt asc` and finding the position of
> the requested scoresheet.

### Step 4: Run and commit

- [ ] Run: `mvn test -Dtest=ScoresheetPdfServiceTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS.

- [ ] Commit:

```bash
git add src/main/java/app/meads/judging/AnonymizationLevel.java \
        src/main/java/app/meads/judging/ScoresheetPdfService.java \
        src/test/java/app/meads/judging/ScoresheetPdfServiceTest.java
git commit -m "Awards module Phase 8: ScoresheetPdfService"
```

---

## Task 9: `AwardsPublicResultsView` (Vaadin UI test)

### Step 1: Write failing UI test

**File:** `src/test/java/app/meads/awards/internal/AwardsPublicResultsViewTest.java`

Follow the existing Vaadin UI test pattern from `MyEntriesViewTest` /
`JudgingAdminViewTest`:

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class AwardsPublicResultsViewTest {

    @Autowired ApplicationContext ctx;
    @Autowired AwardsService awardsService;
    // ... competition / division / user seeding helpers

    @BeforeEach
    void setup() {
        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
    }

    @Test
    void shouldForwardToRootWhenStatusNotPublished() {
        // Seed division at status JUDGING.
        // Navigate to /competitions/{c}/divisions/{d}/results.
        // Assert UI redirected to "" or expected forward target.
    }

    @Test
    void shouldRenderCategorySectionsAndBosWhenPublished() {
        // Seed division at RESULTS_PUBLISHED with at least one medal + one BOS placement.
        // Navigate to /competitions/{c}/divisions/{d}/results.
        // Assert page shows category section with GOLD list containing mead name + meadery only.
        // Assert page shows BOS section with placement rows.
        // Assert no entry IDs visible in rendered HTML (e.g., _find Span by id "public-entry-id" → empty).
    }

    @Test
    void shouldOmitWithheldMedalsFromPublicView() {
        // Seed medal with medal=null. Assert no row rendered for it.
    }
}
```

### Step 2: Create `AwardsPublicResultsView`

**File:** `src/main/java/app/meads/awards/internal/AwardsPublicResultsView.java`

Pattern:

```java
@Route(value = "competitions/:competitionShortName/divisions/:divisionShortName/results",
       layout = MainLayout.class)
@AnonymousAllowed
public class AwardsPublicResultsView extends VerticalLayout implements BeforeEnterObserver {

    private final AwardsService awardsService;
    private final CompetitionService competitionService;
    private final I18NProvider i18n;
    private String competitionShortName;
    private String divisionShortName;

    public AwardsPublicResultsView(AwardsService awardsService,
                                    CompetitionService competitionService,
                                    I18NProvider i18n) { ... }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        competitionShortName = event.getRouteParameters().get("competitionShortName").orElseThrow();
        divisionShortName = event.getRouteParameters().get("divisionShortName").orElseThrow();
        try {
            var view = awardsService.getPublicResults(competitionShortName, divisionShortName);
            renderResults(view);
        } catch (BusinessRuleException e) {
            // results not published or division not found → forward to root
            event.forwardTo("");
        }
    }

    private void renderResults(PublicResultsView view) {
        removeAll();
        // logo + "Competition — Division — Results" header
        // BOS section first
        // per-category sections
        // "Last updated" footer when hasMultiplePublications
    }
}
```

Use the same i18n key namespace `awards.public.*`. Add keys:

```properties
awards.public.title={0} — {1} — Results
awards.public.bos.heading=Best of Show
awards.public.bos.empty=No Best of Show placements.
awards.public.category.no-medals=No medals awarded in this category.
awards.public.medal.gold=Gold
awards.public.medal.silver=Silver
awards.public.medal.bronze=Bronze
awards.public.last-updated=Last updated {0}
```

### Step 3: Run and commit

- [ ] Run: `mvn test -Dtest=AwardsPublicResultsViewTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS.

- [ ] Commit:

```bash
git add src/main/java/app/meads/awards/internal/AwardsPublicResultsView.java \
        src/main/resources/messages.properties \
        src/main/resources/messages_pt.properties \
        src/test/java/app/meads/awards/internal/AwardsPublicResultsViewTest.java
git commit -m "Awards module Phase 9: AwardsPublicResultsView"
```

---

## Task 10: `AwardsAdminView`

### Step 1: Failing UI tests

**File:** `src/test/java/app/meads/awards/internal/AwardsAdminViewTest.java`

Tests to write:
- `shouldShowPublishButtonOnlyWhenStatusDeliberation`
- `shouldShowRepublishRevertSendButtonsOnlyWhenStatusPublished`
- `shouldOpenPublishConfirmDialogAndCallService`
- `shouldRequireJustificationOnRepublishDialog`
- `shouldRequireTypedConfirmTokenOnRevertDialog`
- `shouldOpenSendAnnouncementDialogWithTemplatePreview`
- `shouldRedirectUnauthorizedUserAway`
- `shouldShowPublicationHistorySection`

Patterns follow `JudgingAdminViewTest` (auth + Karibu interactions).

### Step 2: Create `AwardsAdminView`

**File:** `src/main/java/app/meads/awards/internal/AwardsAdminView.java`

`@Route(value = "competitions/:competitionShortName/divisions/:divisionShortName/results-admin",
       layout = MainLayout.class)`, `@PermitAll` + `beforeEnter()` auth (SYSTEM_ADMIN
or `isAuthorizedForDivision`; password gate for non-SYSTEM_ADMIN).

Layout per design §7.2. Buttons:

```java
publishButton = new Button(getTranslation("awards.admin.publish"));
publishButton.addClickListener(e -> openPublishDialog());
publishButton.setVisible(division.getStatus() == DivisionStatus.DELIBERATION);
publishButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
publishButton.setDisableOnClick(true);   // CLAUDE.md: double-click protection
```

The republish dialog uses a `TextArea` with `setMaxLength(1000)` and a server-side
length check on save. The revert dialog uses a `TextField` that the admin must
fill with "REVERT".

Send announcement dialog shows a `Span` with a small preview text:
- When `latestPublication.version == 1`: "Empty message will send the standard 'results published' email."
- When `latestPublication.version > 1`: shows `latestPublication.justification` truncated to 200 chars + "Empty message will use this as the email body."

### Step 3: Add "Manage Results" button on `JudgingAdminView`

**File:** `src/main/java/app/meads/judging/internal/JudgingAdminView.java`

Add to the header layout:

```java
var manageResultsButton = new Button(getTranslation("judging-admin.manage-results"));
manageResultsButton.setVisible(division.getStatus().ordinal() >= DivisionStatus.DELIBERATION.ordinal());
manageResultsButton.addClickListener(e -> ui.navigate(
        "competitions/" + competition.getShortName()
        + "/divisions/" + division.getShortName() + "/results-admin"));
```

i18n keys `judging-admin.manage-results` in EN + PT.

### Step 4: i18n keys

Add comprehensive `awards.admin.*` keys for the page title, buttons, dialog
titles/labels, success/error notifications, publication history section. Mirror in
`messages_pt.properties`. (Roughly 40 keys.)

### Step 5: Run and commit

- [ ] Run: `mvn test -Dtest=AwardsAdminViewTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Run: `mvn test -Dtest="app.meads.judging.**" -Dsurefire.useFile=false 2>&1 | tail -50` (confirm JudgingAdminView still passes)
- [ ] Commit:

```bash
git add src/main/java/app/meads/awards/internal/AwardsAdminView.java \
        src/main/java/app/meads/judging/internal/JudgingAdminView.java \
        src/main/resources/messages.properties \
        src/main/resources/messages_pt.properties \
        src/test/java/app/meads/awards/internal/AwardsAdminViewTest.java
git commit -m "Awards module Phase 10: AwardsAdminView + Manage Results button"
```

---

## Task 11: `MyEntriesView` extension + `MyScoresheetView`

### Step 1: Failing UI tests for `MyEntriesView` extension

**File:** `src/test/java/app/meads/entry/MyEntriesViewTest.java` (existing — extend)

Add tests:
- `shouldShowResultsBannerAndColumnsWhenPublished`
- `shouldRenderWithheldMedalAsDashForEntrant`
- `shouldShowViewScoresheetButtonWhenPublished`
- `shouldNotShowResultsColumnsWhenStatusDeliberation`

### Step 2: Extend `MyEntriesView`

**File:** `src/main/java/app/meads/entry/internal/MyEntriesView.java`

Conditional logic in `populateGrid()`: when `division.getStatus() == DivisionStatus.RESULTS_PUBLISHED`:
- Show "Results" banner above the grid (small Vaadin `Span` + announce-date subtext)
- Add 4 grid columns sourced from `awardsService.getResultsForEntrant(currentUserId, divisionId)`:
  - Round 1 total ("N / 100")
  - Advanced to medal round (Yes / No)
  - Medal ("Gold" / "Silver" / "Bronze" / "—")
  - BOS place ("1st" / "2nd" / "—")
- Extend the actions column with a `🔍 View Scoresheet` button → navigates to `MyScoresheetView`

The Awards module dependency needs to be added — `MyEntriesView` is in `entry.internal`,
which depends on `competition` and `identity`. To use `AwardsService`, the `entry`
module's allowed dependencies must include `awards`. But awards depends on entry...
**circular dependency**. Fix:

> Option A: invert — awards module exposes `MyEntriesResultsContributor` interface
> implemented in awards; entry module declares the interface and injects all impls.
> Option B: move the cross-module call out of MyEntriesView into a parallel view
> path: when published, MyEntriesView shows a banner + a link to a NEW
> `/my-results` page owned by awards.
>
> **Recommendation:** Option B is cleaner — keeps the cycle out. Concretely:
> add a banner to MyEntriesView ("Results are available — view your results")
> with a link to a new `MyResultsView` in the awards module at
> `/competitions/:c/divisions/:d/my-results`, which renders the 4 columns +
> drill-in to `MyScoresheetView`. The dependency direction stays unidirectional
> (awards → entry).
>
> **This deviates from design §7.3 (which described in-place extension of
> MyEntriesView).** Update the design doc §13 ("Open Questions / Deferred") to
> note this trade-off, or accept option A if the cycle can be cleanly broken.
> Decide at implementation; option B is the safer default.

> If option B chosen: rename test cases to test the banner + the new `MyResultsView`.

### Step 3: Create `MyScoresheetView` (or `MyResultsView` + `MyScoresheetView` if option B)

**File:** `src/main/java/app/meads/awards/internal/MyScoresheetView.java`

`@Route(value = "competitions/:c/divisions/:d/my-entries/:entryId/scoresheet",
       layout = MainLayout.class)`, `@PermitAll` + `beforeEnter()` (entry owner or
admin; status must be `RESULTS_PUBLISHED`).

Calls `awardsService.getAnonymizedScoresheet(scoresheetId, currentUserId)` — but the
route only carries `entryId`, so the view must resolve all scoresheets for the entry
and render them in order. Use `scoresheetService.findByEntryIdOrderBySubmittedAtAsc(entryId)`
and then call `getAnonymizedScoresheet` for each.

Layout: entry header card → one card per scoresheet (Judge N label + 5 field rows +
total + comments with language subheader) → Download PDF button → "Back to My Entries" link.

i18n keys: `my-scoresheet.*` namespace.

### Step 4: Failing test for `MyScoresheetView`

**File:** `src/test/java/app/meads/awards/internal/MyScoresheetViewTest.java`

Tests:
- `shouldRenderAllScoresheetsWithStableOrdinals`
- `shouldRejectAccessFromNonOwnerEntrant`
- `shouldRejectAccessWhenResultsNotPublished`
- `shouldOfferAnonymizedPdfDownloadButton`

### Step 5: Run and commit

- [ ] Run: `mvn test -Dtest=MyEntriesViewTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Run: `mvn test -Dtest=MyScoresheetViewTest -Dsurefire.useFile=false 2>&1 | tail -50`

- [ ] Commit:

```bash
git add src/main/java/app/meads/awards/internal/ \
        src/main/java/app/meads/entry/internal/MyEntriesView.java \
        src/main/resources/messages.properties \
        src/main/resources/messages_pt.properties \
        src/test/java/app/meads/entry/MyEntriesViewTest.java \
        src/test/java/app/meads/awards/internal/MyScoresheetViewTest.java
git commit -m "Awards module Phase 11: MyEntriesView extension + MyScoresheetView"
```

---

## Task 12: Module integration test + Modulith verify

### Step 1: Write `AwardsModuleTest`

**File:** `src/test/java/app/meads/awards/AwardsModuleTest.java`

```java
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class AwardsModuleTest {

    @Autowired AwardsService awardsService;
    @Autowired JudgingService judgingService;
    @Autowired ScoresheetService scoresheetService;
    @Autowired CompetitionService competitionService;
    @Autowired EntryService entryService;
    @Autowired UserService userService;

    @Test
    void shouldHandleFullPublishLifecycle() {
        // 1. Seed: competition + division at DELIBERATION + medal rounds complete + at least one MedalAward + one BosPlacement
        // 2. Call awardsService.publish(divisionId, adminUserId)
        // 3. Assert Publication v1 created; division.status == RESULTS_PUBLISHED
        // 4. Assert any subsequent judging mutator (e.g. judgingService.updateMedal) throws BusinessRuleException("error.judging.results-published-frozen")
        // 5. Call competitionService.revertDivisionStatus(divisionId, adminUserId)
        // 6. Assert division.status reverted to DELIBERATION; existing Publication row preserved
        // 7. Call judgingService.updateMedal(...) — succeeds now
        // 8. Call awardsService.publish — rejects (already published) — actually need fresh status: precondition is DELIBERATION; revert leaves us back at DELIBERATION so call awardsService.publish? But existsByDivisionId is true → throws already-published
        //    → instead test republish path: publish requires DELIBERATION + no existing publication. Since publication exists, republish is the path. Re-publish via revertDivisionStatus → advanceDivisionStatus (manual) → status RESULTS_PUBLISHED again, then republish via awardsService.republish(divisionId, "...", adminUserId)
        // 9. Assert Publication v2 created with justification
        // 10. Call awardsService.sendAnnouncement(divisionId, null, adminUserId)
        // 11. Assert at least one email sent (verify via Mailpit fixture or by capturing EmailService calls — depends on existing integration-test pattern in entry module)
    }
}
```

> The `awardsService.publish` second-time guard needs to handle the
> "publication-already-exists" case smoothly. Re-publishing after a revert should
> use the `republish` flow (with justification) — that's the design intent.
> Confirm `publish` rejects when any publication already exists for the division.

### Step 2: Modulith structure verify

`ModulithStructureTest` automatically picks up the new awards module via its
`package-info.java`. Run it:

- [ ] Run: `mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false 2>&1 | tail -50`
- [ ] Expected: PASS — no boundary violations.

### Step 3: Full suite

- [ ] Run: `mvn test -Dsurefire.useFile=false 2>&1 | tail -80`
- [ ] Expected: All tests pass.

### Step 4: Commit

```bash
git add src/test/java/app/meads/awards/AwardsModuleTest.java
git commit -m "Awards module Phase 12: module integration test"
```

---

## Task 13: ES/IT/PL i18n catch-up + walkthrough

### Step 1: ES/IT/PL translations

For every new key added in `messages.properties` and `messages_pt.properties`
during Tasks 1–12, add matching keys to:
- `src/main/resources/messages_es.properties` (ES formal "usted")
- `src/main/resources/messages_it.properties` (IT informal "tu")
- `src/main/resources/messages_pl.properties` (PL standard, locative grammar)

Convert all non-ASCII to `\uXXXX` escapes (use the existing `/tmp/escape_non_ascii.py`
helper from prior i18n cycles, or `native2ascii`).

> Estimate: ~100–150 keys total across `error.judging.*`, `error.awards.*`,
> `awards.public.*`, `awards.admin.*`, `my-scoresheet.*`, `email.results-*.*`,
> `email.custom-announcement.*`, `judging-admin.manage-results`.

### Step 2: Walkthrough Section 13

**File:** `docs/walkthrough/manual-test.md`

Add a new top-level section "Section 13 — Awards Module" covering:

1. **Setup** — Have a division with at least one Round-1 table complete, all
   medal rounds completed, BOS placements recorded; status is DELIBERATION.
2. **Publish** — From JudgingAdminView, click "Manage Results"; on AwardsAdminView,
   click "Publish Results"; confirm; verify status advances to RESULTS_PUBLISHED;
   verify no email sent (check Mailpit).
3. **Public results page** — Open `/competitions/:c/divisions/:d/results` in a
   logged-out browser; verify medal sections + BOS leaderboard render; verify no
   entry IDs visible; verify withheld medals not listed.
4. **Send announcement (initial)** — From AwardsAdminView, click "Send Announcement";
   leave message empty; submit; verify all entrants receive the initial-publish
   email in their `preferredLanguage`; CTA link works (magic-link login).
5. **Entrant view** — Log in as an entrant; navigate to MyEntries; verify Results
   banner + result columns + "View Scoresheet" buttons.
6. **Scoresheet drill-in** — Click "View Scoresheet"; verify anonymized
   ("Judge 1", "Judge 2") rendering; verify per-field scores, comments,
   comment-language subheader, total; click "Download PDF"; verify PDF is
   anonymized.
7. **Try to edit judging data** — From JudgingAdminView, try to edit a medal;
   verify rejection with the frozen error message.
8. **Revert publication** — From AwardsAdminView, click "Revert Publication";
   type "REVERT"; submit; verify status returns to DELIBERATION; verify the
   prior Publication row still exists (visible in "Publication history" section).
9. **Edit a medal** — Now editable; change e.g. SILVER → BRONZE on one entry.
10. **Republish** — From AwardsAdminView, click "Republish"; fill justification
    (≥20 chars); submit; verify Publication v2 created; verify status remains
    RESULTS_PUBLISHED; verify reminder banner shown; verify no email sent.
11. **Send announcement (republish, default body)** — Click "Send Announcement";
    leave message empty; submit; verify all entrants receive the republish email
    with the justification rendered as the body.
12. **Send announcement (custom message)** — Click "Send Announcement" again;
    fill a custom message; submit; verify all entrants receive the custom
    announcement.
13. **Anonymity verification** — Confirm that entrant scoresheet view + PDF show
    no judge names or certifications, only "Judge 1", "Judge 2".

### Step 3: Update SESSION_CONTEXT and CLAUDE.md

**File:** `docs/SESSION_CONTEXT.md`

- Add "awards module — Complete" under Modules Implemented.
- Update test count.
- Move "Awards module" from `What's Next` to `Completed priorities`.

**File:** `CLAUDE.md`

- Add `awards` module to the Module Map table.
- Add the awards package layout to the Actual Package Layout section.
- Note V28 in Database & Migrations.

### Step 4: Final commit

```bash
git add src/main/resources/messages_*.properties \
        docs/walkthrough/manual-test.md \
        docs/SESSION_CONTEXT.md \
        CLAUDE.md
git commit -m "Awards module Phase 13: ES/IT/PL i18n + walkthrough + docs"
```

---

## Final verification

- [ ] Run: `mvn verify 2>&1 | tail -60`
- [ ] Expected: BUILD SUCCESS, all tests pass.
- [ ] Run: `mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false 2>&1 | tail -30`
- [ ] Expected: PASS.

---

## Notes for the implementer

- **MyEntriesView circular dependency** — see Task 11 Step 2. Default to option B
  (new `MyResultsView` in awards module + banner link from `MyEntriesView`); this
  keeps the module dependency graph acyclic. If option A is pursued, declare the
  contributor interface in `entry` module's public API.
- **EntryRepository visibility** — `findDistinctUserIdsByDivisionId` exposed via
  `EntryService.findEntrantUserIdsForDivision` (public method) to avoid awards
  reaching into `entry.internal`.
- **ScoresheetService missing helpers** — audit `ScoresheetService` interface
  for the helpers needed by `getResultsForEntrant` and `getAnonymizedScoresheet`:
  `findByEntryId`, `findByEntryIdOrderBySubmittedAtAsc`. Add to the public
  interface if missing.
- **Two-tier TDD** — per CLAUDE.md, each Task here is a Full Cycle (multiple
  responses); each Step is one action. Suggest commits at the end of each Task,
  not after every micro-step.
- **i18n flow** — EN + PT in each task; ES/IT/PL deferred to Task 13.
- **Test count delta estimate** — ~100 new tests across all tasks. Final count
  should land near 1075 (from current 975).
- **No new event listeners required** in v1 — events are published for
  observability but no `@ApplicationModuleListener` consumers exist yet (matches
  the design's deferred-listeners stance for judging events).
