# Competition Documents Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow competition admins to attach named documents (PDF uploads or external links) to a competition, viewable by authenticated users with competition access.

**Architecture:** New `CompetitionDocument` entity + `DocumentType` enum in the competition module public API. `CompetitionDocumentRepository` in `internal/`. Service methods added to existing `CompetitionService`. New "Documents" tab in `CompetitionDetailView` (admin). Document list section in `MyEntriesView` (entrant). Flyway V14 for the new table.

**Tech Stack:** JPA entity, Spring Data repository, Vaadin Upload + Grid + Anchor, StreamResource for PDF download.

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/app/meads/competition/DocumentType.java` | Enum: PDF, LINK |
| Create | `src/main/java/app/meads/competition/CompetitionDocument.java` | JPA entity |
| Create | `src/main/java/app/meads/competition/internal/CompetitionDocumentRepository.java` | Spring Data repository |
| Create | `src/main/resources/db/migration/V14__create_competition_documents_table.sql` | Flyway migration |
| Modify | `src/main/java/app/meads/competition/CompetitionService.java` | Add document CRUD methods |
| Modify | `src/main/java/app/meads/competition/internal/CompetitionDetailView.java` | Add "Documents" tab |
| Modify | `src/main/java/app/meads/entry/internal/MyEntriesView.java` | Add document list section |
| Create | `src/test/java/app/meads/competition/CompetitionDocumentTest.java` | Entity unit tests |
| Modify | `src/test/java/app/meads/competition/CompetitionServiceTest.java` | Document service unit tests |
| Create | `src/test/java/app/meads/competition/CompetitionDocumentRepositoryTest.java` | Repository tests |
| Modify | `src/test/java/app/meads/competition/CompetitionDetailViewTest.java` | Documents tab UI tests |
| Modify | `src/test/java/app/meads/entry/MyEntriesViewTest.java` | Document list UI tests |

---

## Task 1: Entity Unit Tests + DocumentType Enum + CompetitionDocument Entity

Tests for `CompetitionDocument` domain logic: constructor validation, type-specific constraints.

**Files:**
- Create: `src/test/java/app/meads/competition/CompetitionDocumentTest.java`
- Create: `src/main/java/app/meads/competition/DocumentType.java`
- Create: `src/main/java/app/meads/competition/CompetitionDocument.java`

### Step 1: RED — Write entity unit tests

- [ ] Create `CompetitionDocumentTest.java` with these tests:

```java
package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetitionDocumentTest {

    private static final UUID COMPETITION_ID = UUID.randomUUID();

    @Test
    void shouldCreatePdfDocument() {
        var doc = CompetitionDocument.createPdf(COMPETITION_ID, "Rules",
                new byte[]{1, 2, 3}, "application/pdf", 0);

        assertThat(doc.getId()).isNotNull();
        assertThat(doc.getCompetitionId()).isEqualTo(COMPETITION_ID);
        assertThat(doc.getName()).isEqualTo("Rules");
        assertThat(doc.getType()).isEqualTo(DocumentType.PDF);
        assertThat(doc.getData()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(doc.getContentType()).isEqualTo("application/pdf");
        assertThat(doc.getUrl()).isNull();
        assertThat(doc.getDisplayOrder()).isZero();
    }

    @Test
    void shouldCreateLinkDocument() {
        var doc = CompetitionDocument.createLink(COMPETITION_ID, "MJP Guidelines",
                "https://example.com/mjp.pdf", 1);

        assertThat(doc.getId()).isNotNull();
        assertThat(doc.getType()).isEqualTo(DocumentType.LINK);
        assertThat(doc.getUrl()).isEqualTo("https://example.com/mjp.pdf");
        assertThat(doc.getData()).isNull();
        assertThat(doc.getContentType()).isNull();
        assertThat(doc.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void shouldRejectPdfExceeding10Mb() {
        var largeData = new byte[10 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> CompetitionDocument.createPdf(
                COMPETITION_ID, "Rules", largeData, "application/pdf", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void shouldRejectNonPdfContentType() {
        assertThatThrownBy(() -> CompetitionDocument.createPdf(
                COMPETITION_ID, "Rules", new byte[]{1}, "image/png", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application/pdf");
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> CompetitionDocument.createPdf(
                COMPETITION_ID, "  ", new byte[]{1}, "application/pdf", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectBlankUrl() {
        assertThatThrownBy(() -> CompetitionDocument.createLink(
                COMPETITION_ID, "Link", "  ", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL");
    }

    @Test
    void shouldUpdateName() {
        var doc = CompetitionDocument.createLink(COMPETITION_ID, "Old", "https://example.com", 0);
        doc.updateName("New");
        assertThat(doc.getName()).isEqualTo("New");
    }

    @Test
    void shouldRejectBlankNameOnUpdate() {
        var doc = CompetitionDocument.createLink(COMPETITION_ID, "Name", "https://example.com", 0);
        assertThatThrownBy(() -> doc.updateName("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUpdateDisplayOrder() {
        var doc = CompetitionDocument.createLink(COMPETITION_ID, "Name", "https://example.com", 0);
        doc.updateDisplayOrder(3);
        assertThat(doc.getDisplayOrder()).isEqualTo(3);
    }
}
```

- [ ] Run: `mvn test -Dtest=CompetitionDocumentTest -Dsurefire.useFile=false`
- [ ] Verify: FAIL — `CompetitionDocument` class does not exist

### Step 2: GREEN — Create DocumentType enum and CompetitionDocument entity

- [ ] Create `src/main/java/app/meads/competition/DocumentType.java`:

```java
package app.meads.competition;

public enum DocumentType {
    PDF,
    LINK
}
```

- [ ] Create `src/main/java/app/meads/competition/CompetitionDocument.java`:

```java
package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competition_documents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"competition_id", "name"}))
@Getter
public class CompetitionDocument {

    private static final int MAX_PDF_SIZE = 10 * 1024 * 1024;

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Basic(fetch = FetchType.LAZY)
    @Column(length = 10485760)
    private byte[] data;

    @Column(name = "content_type", length = 100)
    private String contentType;

    private String url;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    protected CompetitionDocument() {} // JPA

    private CompetitionDocument(UUID competitionId, String name, DocumentType type,
                                 byte[] data, String contentType, String url, int displayOrder) {
        validateName(name);
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
        this.name = name;
        this.type = type;
        this.data = data;
        this.contentType = contentType;
        this.url = url;
        this.displayOrder = displayOrder;
    }

    public static CompetitionDocument createPdf(UUID competitionId, String name,
                                                  byte[] data, String contentType, int displayOrder) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("PDF data must not be empty");
        }
        if (data.length > MAX_PDF_SIZE) {
            throw new IllegalArgumentException("PDF must not exceed 10 MB");
        }
        if (!"application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("Content type must be application/pdf");
        }
        return new CompetitionDocument(competitionId, name, DocumentType.PDF,
                data, contentType, null, displayOrder);
    }

    public static CompetitionDocument createLink(UUID competitionId, String name,
                                                    String url, int displayOrder) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }
        return new CompetitionDocument(competitionId, name, DocumentType.LINK,
                null, null, url, displayOrder);
    }

    public void updateName(String name) {
        validateName(name);
        this.name = name;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Document name must not be blank");
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

- [ ] Run: `mvn test -Dtest=CompetitionDocumentTest -Dsurefire.useFile=false`
- [ ] Verify: all 9 tests PASS

### Step 3: REFACTOR — Review and run full suite

- [ ] Review entity and tests for consistency with existing patterns (`Competition.java`, `CompetitionTest.java`)
- [ ] Run: `mvn test -Dsurefire.useFile=false`
- [ ] Verify: full suite passes (no regressions)
- [ ] Commit: `git commit -m "Add CompetitionDocument entity and DocumentType enum with unit tests"`

---

## Task 2: Repository + Flyway Migration

Persistence layer for `CompetitionDocument`.

**Files:**
- Create: `src/test/java/app/meads/competition/CompetitionDocumentRepositoryTest.java`
- Create: `src/main/java/app/meads/competition/internal/CompetitionDocumentRepository.java`
- Create: `src/main/resources/db/migration/V14__create_competition_documents_table.sql`

### Step 1: RED — Write repository test

- [ ] Create `CompetitionDocumentRepositoryTest.java`:

```java
package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionDocumentRepository;
import app.meads.competition.internal.CompetitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CompetitionDocumentRepositoryTest {

    @Autowired
    CompetitionDocumentRepository competitionDocumentRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    private UUID competitionId;

    @BeforeEach
    void setup() {
        var competition = competitionRepository.save(new Competition(
                "Doc Test", "doc-test-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        competitionId = competition.getId();
    }

    @Test
    void shouldSaveAndFindByCompetitionIdOrderedByDisplayOrder() {
        var doc2 = CompetitionDocument.createLink(competitionId, "Second", "https://example.com/2", 1);
        var doc1 = CompetitionDocument.createLink(competitionId, "First", "https://example.com/1", 0);
        competitionDocumentRepository.save(doc2);
        competitionDocumentRepository.save(doc1);

        var docs = competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competitionId);
        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getName()).isEqualTo("First");
        assertThat(docs.get(1).getName()).isEqualTo("Second");
    }

    @Test
    void shouldCountByCompetitionId() {
        competitionDocumentRepository.save(
                CompetitionDocument.createLink(competitionId, "A", "https://example.com", 0));
        competitionDocumentRepository.save(
                CompetitionDocument.createLink(competitionId, "B", "https://example.com/b", 1));

        assertThat(competitionDocumentRepository.countByCompetitionId(competitionId)).isEqualTo(2);
    }

    @Test
    void shouldCheckExistsByCompetitionIdAndName() {
        competitionDocumentRepository.save(
                CompetitionDocument.createLink(competitionId, "Rules", "https://example.com", 0));

        assertThat(competitionDocumentRepository.existsByCompetitionIdAndName(competitionId, "Rules")).isTrue();
        assertThat(competitionDocumentRepository.existsByCompetitionIdAndName(competitionId, "Other")).isFalse();
    }

    @Test
    void shouldSavePdfDocument() {
        var doc = CompetitionDocument.createPdf(competitionId, "Rules PDF",
                new byte[]{1, 2, 3}, "application/pdf", 0);
        var saved = competitionDocumentRepository.save(doc);

        var found = competitionDocumentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Rules PDF");
        assertThat(found.getType()).isEqualTo(DocumentType.PDF);
        assertThat(found.getData()).isEqualTo(new byte[]{1, 2, 3});
    }
}
```

- [ ] Run: `mvn test -Dtest=CompetitionDocumentRepositoryTest -Dsurefire.useFile=false`
- [ ] Verify: FAIL — `CompetitionDocumentRepository` does not exist

### Step 2: GREEN — Create repository and migration

- [ ] Create `src/main/java/app/meads/competition/internal/CompetitionDocumentRepository.java`:

```java
package app.meads.competition.internal;

import app.meads.competition.CompetitionDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CompetitionDocumentRepository extends JpaRepository<CompetitionDocument, UUID> {
    List<CompetitionDocument> findByCompetitionIdOrderByDisplayOrder(UUID competitionId);
    int countByCompetitionId(UUID competitionId);
    boolean existsByCompetitionIdAndName(UUID competitionId, String name);
}
```

- [ ] Create `src/main/resources/db/migration/V14__create_competition_documents_table.sql`:

```sql
CREATE TABLE competition_documents (
    id UUID PRIMARY KEY,
    competition_id UUID NOT NULL REFERENCES competitions(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    data BYTEA,
    content_type VARCHAR(100),
    url TEXT,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (competition_id, name)
);

CREATE INDEX idx_competition_documents_competition_id ON competition_documents(competition_id);
```

- [ ] Run: `mvn test -Dtest=CompetitionDocumentRepositoryTest -Dsurefire.useFile=false`
- [ ] Verify: all 4 tests PASS

### Step 3: REFACTOR — Review and run full suite

- [ ] Review migration and repository for consistency with existing patterns (`CompetitionRepository.java`, `V3__*.sql`)
- [ ] Run: `mvn test -Dsurefire.useFile=false`
- [ ] Verify: full suite passes
- [ ] Commit: `git commit -m "Add CompetitionDocumentRepository and V14 migration"`

---

## Task 3: Service Layer — Document CRUD Methods

Add document management methods to `CompetitionService`.

**Files:**
- Modify: `src/test/java/app/meads/competition/CompetitionServiceTest.java`
- Modify: `src/main/java/app/meads/competition/CompetitionService.java`

### Step 1: RED — Write service unit tests

- [ ] Add to `CompetitionServiceTest.java` — new `@Mock` field and test methods:

Add mock field:

```java
@Mock
CompetitionDocumentRepository competitionDocumentRepository;
```

Add helper:

```java
private void setupAdminAuth(User admin, Competition competition) {
    given(userService.findById(admin.getId())).willReturn(admin);
    given(competitionRepository.findById(competition.getId()))
            .willReturn(Optional.of(competition));
}
```

Add tests:

```java
// --- addDocument (PDF) ---

@Test
void shouldAddPdfDocumentWhenAuthorized() {
    var admin = createAdmin();
    var competition = createCompetition();
    setupAdminAuth(admin, competition);
    given(competitionDocumentRepository.existsByCompetitionIdAndName(
            competition.getId(), "Rules")).willReturn(false);
    given(competitionDocumentRepository.countByCompetitionId(competition.getId()))
            .willReturn(0);
    given(competitionDocumentRepository.save(any(CompetitionDocument.class)))
            .willAnswer(inv -> inv.getArgument(0));

    var result = competitionService.addDocument(competition.getId(), "Rules",
            DocumentType.PDF, new byte[]{1, 2, 3}, "application/pdf", null, admin.getId());

    assertThat(result.getName()).isEqualTo("Rules");
    assertThat(result.getType()).isEqualTo(DocumentType.PDF);
    assertThat(result.getDisplayOrder()).isZero();
}

// --- addDocument (Link) ---

@Test
void shouldAddLinkDocumentWhenAuthorized() {
    var admin = createAdmin();
    var competition = createCompetition();
    setupAdminAuth(admin, competition);
    given(competitionDocumentRepository.existsByCompetitionIdAndName(
            competition.getId(), "MJP Guide")).willReturn(false);
    given(competitionDocumentRepository.countByCompetitionId(competition.getId()))
            .willReturn(2);
    given(competitionDocumentRepository.save(any(CompetitionDocument.class)))
            .willAnswer(inv -> inv.getArgument(0));

    var result = competitionService.addDocument(competition.getId(), "MJP Guide",
            DocumentType.LINK, null, null, "https://example.com/mjp", admin.getId());

    assertThat(result.getType()).isEqualTo(DocumentType.LINK);
    assertThat(result.getUrl()).isEqualTo("https://example.com/mjp");
    assertThat(result.getDisplayOrder()).isEqualTo(2);
}

// --- addDocument duplicate name ---

@Test
void shouldRejectDuplicateDocumentName() {
    var admin = createAdmin();
    var competition = createCompetition();
    setupAdminAuth(admin, competition);
    given(competitionDocumentRepository.existsByCompetitionIdAndName(
            competition.getId(), "Rules")).willReturn(true);

    assertThatThrownBy(() -> competitionService.addDocument(competition.getId(), "Rules",
            DocumentType.LINK, null, null, "https://example.com", admin.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
}

// --- removeDocument ---

@Test
void shouldRemoveDocumentWhenAuthorized() {
    var admin = createAdmin();
    var competition = createCompetition();
    var doc = CompetitionDocument.createLink(competition.getId(), "Rules", "https://example.com", 0);
    given(competitionDocumentRepository.findById(doc.getId()))
            .willReturn(Optional.of(doc));
    given(competitionRepository.findById(competition.getId()))
            .willReturn(Optional.of(competition));
    given(userService.findById(admin.getId())).willReturn(admin);

    competitionService.removeDocument(doc.getId(), admin.getId());

    then(competitionDocumentRepository).should().delete(doc);
}

// --- updateDocumentName ---

@Test
void shouldUpdateDocumentNameWhenAuthorized() {
    var admin = createAdmin();
    var competition = createCompetition();
    var doc = CompetitionDocument.createLink(competition.getId(), "Old Name", "https://example.com", 0);
    given(competitionDocumentRepository.findById(doc.getId()))
            .willReturn(Optional.of(doc));
    given(competitionRepository.findById(competition.getId()))
            .willReturn(Optional.of(competition));
    given(userService.findById(admin.getId())).willReturn(admin);
    given(competitionDocumentRepository.existsByCompetitionIdAndName(
            competition.getId(), "New Name")).willReturn(false);
    given(competitionDocumentRepository.save(any(CompetitionDocument.class)))
            .willAnswer(inv -> inv.getArgument(0));

    var result = competitionService.updateDocumentName(doc.getId(), "New Name", admin.getId());

    assertThat(result.getName()).isEqualTo("New Name");
}

// --- getDocuments ---

@Test
void shouldGetDocumentsOrderedByDisplayOrder() {
    var competition = createCompetition();
    var doc1 = CompetitionDocument.createLink(competition.getId(), "A", "https://a.com", 0);
    var doc2 = CompetitionDocument.createLink(competition.getId(), "B", "https://b.com", 1);
    given(competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competition.getId()))
            .willReturn(List.of(doc1, doc2));

    var result = competitionService.getDocuments(competition.getId());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getName()).isEqualTo("A");
}

// --- getDocument ---

@Test
void shouldGetDocumentById() {
    var doc = CompetitionDocument.createLink(UUID.randomUUID(), "Rules", "https://example.com", 0);
    given(competitionDocumentRepository.findById(doc.getId()))
            .willReturn(Optional.of(doc));

    var result = competitionService.getDocument(doc.getId());

    assertThat(result.getName()).isEqualTo("Rules");
}

@Test
void shouldThrowWhenDocumentNotFound() {
    var randomId = UUID.randomUUID();
    given(competitionDocumentRepository.findById(randomId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> competitionService.getDocument(randomId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
}

// --- reorderDocuments ---

@Test
void shouldReorderDocumentsWhenAuthorized() {
    var admin = createAdmin();
    var competition = createCompetition();
    setupAdminAuth(admin, competition);
    var doc1 = CompetitionDocument.createLink(competition.getId(), "A", "https://a.com", 0);
    var doc2 = CompetitionDocument.createLink(competition.getId(), "B", "https://b.com", 1);
    given(competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competition.getId()))
            .willReturn(List.of(doc1, doc2));

    // Reorder: B first, then A
    competitionService.reorderDocuments(competition.getId(),
            List.of(doc2.getId(), doc1.getId()), admin.getId());

    assertThat(doc2.getDisplayOrder()).isZero();
    assertThat(doc1.getDisplayOrder()).isEqualTo(1);
}
```

- [ ] Run: `mvn test -Dtest=CompetitionServiceTest#shouldAddPdfDocumentWhenAuthorized -Dsurefire.useFile=false`
- [ ] Verify: FAIL — methods do not exist on `CompetitionService`

### Step 2: GREEN — Implement service methods

- [ ] Add `CompetitionDocumentRepository` to `CompetitionService` constructor injection:

In `CompetitionService.java`, add field:

```java
private final CompetitionDocumentRepository competitionDocumentRepository;
```

Update constructor to include the new parameter (add after `categoryRepository`):

```java
CompetitionService(CompetitionRepository competitionRepository,
                   DivisionRepository divisionRepository,
                   ParticipantRepository participantRepository,
                   ParticipantRoleRepository participantRoleRepository,
                   DivisionCategoryRepository divisionCategoryRepository,
                   CategoryRepository categoryRepository,
                   CompetitionDocumentRepository competitionDocumentRepository,
                   UserService userService,
                   ApplicationEventPublisher eventPublisher,
                   List<DivisionRevertGuard> revertGuards) {
    // ... existing assignments ...
    this.competitionDocumentRepository = competitionDocumentRepository;
    // ... rest ...
}
```

- [ ] Add document methods to `CompetitionService.java` (after the division category methods, before participant methods):

```java
// --- Document methods ---

public CompetitionDocument addDocument(@NotNull UUID competitionId,
                                        @NotBlank String name,
                                        @NotNull DocumentType type,
                                        byte[] data,
                                        String contentType,
                                        String url,
                                        @NotNull UUID requestingUserId) {
    competitionRepository.findById(competitionId)
            .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
    requireAuthorized(competitionId, requestingUserId);
    if (competitionDocumentRepository.existsByCompetitionIdAndName(competitionId, name)) {
        throw new IllegalArgumentException("Document with this name already exists");
    }
    int nextOrder = competitionDocumentRepository.countByCompetitionId(competitionId);
    var doc = switch (type) {
        case PDF -> CompetitionDocument.createPdf(competitionId, name, data, contentType, nextOrder);
        case LINK -> CompetitionDocument.createLink(competitionId, name, url, nextOrder);
    };
    log.info("Added document '{}' (type={}) to competition {}", name, type, competitionId);
    return competitionDocumentRepository.save(doc);
}

public void removeDocument(@NotNull UUID documentId, @NotNull UUID requestingUserId) {
    var doc = competitionDocumentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    requireAuthorized(doc.getCompetitionId(), requestingUserId);
    competitionDocumentRepository.delete(doc);
    log.info("Removed document '{}' from competition {}", doc.getName(), doc.getCompetitionId());
}

public CompetitionDocument updateDocumentName(@NotNull UUID documentId,
                                                @NotBlank String name,
                                                @NotNull UUID requestingUserId) {
    var doc = competitionDocumentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    requireAuthorized(doc.getCompetitionId(), requestingUserId);
    if (competitionDocumentRepository.existsByCompetitionIdAndName(doc.getCompetitionId(), name)) {
        throw new IllegalArgumentException("Document with this name already exists");
    }
    doc.updateName(name);
    log.debug("Updated document name: {} → '{}'", documentId, name);
    return competitionDocumentRepository.save(doc);
}

public void reorderDocuments(@NotNull UUID competitionId,
                              @NotNull List<UUID> orderedIds,
                              @NotNull UUID requestingUserId) {
    competitionRepository.findById(competitionId)
            .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
    requireAuthorized(competitionId, requestingUserId);
    var docs = competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competitionId);
    var docMap = docs.stream()
            .collect(java.util.stream.Collectors.toMap(CompetitionDocument::getId,
                    java.util.function.Function.identity()));
    for (int i = 0; i < orderedIds.size(); i++) {
        var doc = docMap.get(orderedIds.get(i));
        if (doc != null) {
            doc.updateDisplayOrder(i);
        }
    }
    competitionDocumentRepository.saveAll(docs);
    log.debug("Reordered {} documents for competition {}", orderedIds.size(), competitionId);
}

public List<CompetitionDocument> getDocuments(@NotNull UUID competitionId) {
    return competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competitionId);
}

public CompetitionDocument getDocument(@NotNull UUID documentId) {
    return competitionDocumentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
}
```

- [ ] Run: `mvn test -Dtest=CompetitionServiceTest -Dsurefire.useFile=false`
- [ ] Verify: all tests PASS (existing + new)

### Step 3: REFACTOR — Review and run full suite

- [ ] Review service methods for consistency with existing patterns
- [ ] Note: `deleteCompetition` should also delete documents before deleting the competition (FK constraint). Add document cleanup to `deleteCompetition`:
  ```java
  var documents = competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competitionId);
  competitionDocumentRepository.deleteAll(documents);
  ```
  Add this before the existing `competitionRepository.delete(competition)` line. Also add a unit test for this in `CompetitionServiceTest`:
  ```java
  @Test
  void shouldDeleteDocumentsWhenDeletingCompetition() {
      var admin = createAdmin();
      var competition = createCompetition();
      given(userService.findById(admin.getId())).willReturn(admin);
      given(competitionRepository.findById(competition.getId()))
              .willReturn(Optional.of(competition));
      given(divisionRepository.findByCompetitionId(competition.getId()))
              .willReturn(List.of());
      given(competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competition.getId()))
              .willReturn(List.of());

      competitionService.deleteCompetition(competition.getId(), admin.getId());

      then(competitionDocumentRepository).should().deleteAll(List.of());
      then(competitionRepository).should().delete(competition);
  }
  ```
- [ ] Run: `mvn test -Dsurefire.useFile=false`
- [ ] Verify: full suite passes
- [ ] Commit: `git commit -m "Add document CRUD methods to CompetitionService"`

---

## Task 4: Admin UI — Documents Tab in CompetitionDetailView

Add a "Documents" tab for managing competition documents.

**Files:**
- Modify: `src/test/java/app/meads/competition/CompetitionDetailViewTest.java`
- Modify: `src/main/java/app/meads/competition/internal/CompetitionDetailView.java`

### Step 1: RED — Write UI test for Documents tab

- [ ] Add `@Autowired CompetitionService competitionService;` field to `CompetitionDetailViewTest.java` (it is not currently autowired in this test class).

- [ ] Add tests to `CompetitionDetailViewTest.java`:

```java
@Test
@WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
void shouldDisplayDocumentsTab() {
    UI.getCurrent().navigate("competitions/" + testCompetition.getShortName());

    var tabSheet = _get(TabSheet.class);
    tabSheet.setSelectedIndex(3); // Documents tab (after Divisions, Participants, Settings)

    var addButton = _get(Button.class, spec -> spec.withText("Add Document"));
    assertThat(addButton).isNotNull();
}

@Test
@WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
void shouldDisplayDocumentsInGrid() {
    // Add a document directly via service
    var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
    competitionService.addDocument(testCompetition.getId(), "Test Rules",
            DocumentType.LINK, null, null, "https://example.com/rules", admin.getId());

    UI.getCurrent().navigate("competitions/" + testCompetition.getShortName());

    var tabSheet = _get(TabSheet.class);
    tabSheet.setSelectedIndex(3); // Documents tab

    @SuppressWarnings("unchecked")
    var grid = (Grid<CompetitionDocument>) _find(Grid.class).stream()
            .filter(g -> !(g instanceof com.vaadin.flow.component.treegrid.TreeGrid))
            .reduce((first, second) -> second)
            .orElseThrow();
    assertThat(grid.getGenericDataView().getItems().count()).isEqualTo(1);
}
```

- [ ] Add imports for `DocumentType`, `CompetitionDocument`, `CompetitionService` (autowire `CompetitionService` if not already)

Add field if missing:

```java
@Autowired
CompetitionService competitionService;
```

- [ ] Run: `mvn test -Dtest=CompetitionDetailViewTest#shouldDisplayDocumentsTab -Dsurefire.useFile=false`
- [ ] Verify: FAIL — Documents tab doesn't exist yet (tab index 3 has no content / assertion fails)

### Step 2: GREEN — Add Documents tab to CompetitionDetailView

- [ ] In `CompetitionDetailView.java`, add the Documents tab to `createTabSheet()`:

```java
private TabSheet createTabSheet() {
    var tabSheet = new TabSheet();
    tabSheet.setWidthFull();

    tabSheet.add("Divisions", createDivisionsTab());
    tabSheet.add("Participants", createParticipantsTab());
    tabSheet.add("Settings", createSettingsTab());
    tabSheet.add("Documents", createDocumentsTab());

    return tabSheet;
}
```

- [ ] Add `createDocumentsTab()` method and supporting methods:

```java
private VerticalLayout createDocumentsTab() {
    var tab = new VerticalLayout();
    tab.setPadding(false);

    var actions = new HorizontalLayout();
    actions.setWidthFull();
    actions.setJustifyContentMode(JustifyContentMode.END);
    actions.add(new Button("Add Document", e -> openAddDocumentDialog()));
    tab.add(actions);

    var documentsGrid = new Grid<CompetitionDocument>(CompetitionDocument.class, false);
    documentsGrid.setAllRowsVisible(true);
    documentsGrid.addColumn(CompetitionDocument::getName).setHeader("Name").setFlexGrow(3);
    documentsGrid.addComponentColumn(doc -> {
        var badge = new Span(doc.getType().name());
        badge.getElement().getThemeList().add("badge pill small");
        return badge;
    }).setHeader("Type").setAutoWidth(true);
    documentsGrid.addComponentColumn(doc -> {
        var layout = new HorizontalLayout();
        layout.setSpacing(false);
        layout.getStyle().set("gap", "var(--lumo-space-xs)");

        if (doc.getType() == DocumentType.PDF) {
            var downloadAnchor = new Anchor(
                    new StreamResource(doc.getName() + ".pdf",
                            () -> new ByteArrayInputStream(
                                    competitionService.getDocument(doc.getId()).getData())),
                    "");
            downloadAnchor.getElement().setAttribute("download", true);
            var downloadButton = new Button(new Icon(VaadinIcon.DOWNLOAD));
            downloadButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            downloadButton.setTooltipText("Download");
            downloadAnchor.add(downloadButton);
            layout.add(downloadAnchor);
        } else {
            var openAnchor = new Anchor(doc.getUrl(), "");
            openAnchor.setTarget("_blank");
            var openButton = new Button(new Icon(VaadinIcon.EXTERNAL_LINK));
            openButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            openButton.setTooltipText("Open link");
            openAnchor.add(openButton);
            layout.add(openAnchor);
        }

        var upButton = new Button(new Icon(VaadinIcon.ARROW_UP));
        upButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        upButton.setTooltipText("Move up");
        upButton.addClickListener(e -> moveDocument(documentsGrid, doc, -1));

        var downButton = new Button(new Icon(VaadinIcon.ARROW_DOWN));
        downButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        downButton.setTooltipText("Move down");
        downButton.addClickListener(e -> moveDocument(documentsGrid, doc, 1));

        var editButton = new Button(new Icon(VaadinIcon.EDIT));
        editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        editButton.setTooltipText("Edit name");
        editButton.addClickListener(e -> openEditDocumentNameDialog(documentsGrid, doc));

        var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        deleteButton.setTooltipText("Delete");
        deleteButton.addClickListener(e -> openDeleteDocumentDialog(documentsGrid, doc));

        layout.add(upButton, downButton, editButton, deleteButton);
        return layout;
    }).setHeader("Actions").setAutoWidth(true);

    documentsGrid.setItems(competitionService.getDocuments(competitionId));
    tab.add(documentsGrid);
    return tab;
}

private void openAddDocumentDialog() {
    var dialog = new Dialog();
    dialog.setHeaderTitle("Add Document");

    var nameField = new TextField("Name");
    nameField.setRequired(true);
    nameField.setWidthFull();

    var typeSelect = new Select<DocumentType>();
    typeSelect.setLabel("Type");
    typeSelect.setItems(DocumentType.values());
    typeSelect.setValue(DocumentType.PDF);
    typeSelect.setWidthFull();

    var urlField = new TextField("URL");
    urlField.setWidthFull();
    urlField.setVisible(false);

    var pdfData = new byte[1][];
    var pdfContentType = new String[1];

    var uploadHandler = UploadHandler.inMemory((metadata, data) -> {
        pdfData[0] = data;
        pdfContentType[0] = metadata.contentType();
    });
    var upload = new Upload(uploadHandler);
    upload.setMaxFiles(1);
    upload.setMaxFileSize(10 * 1024 * 1024);
    upload.setAcceptedFileTypes("application/pdf");
    upload.addFileRejectedListener(e ->
            Notification.show(e.getErrorMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR));

    typeSelect.addValueChangeListener(e -> {
        upload.setVisible(e.getValue() == DocumentType.PDF);
        urlField.setVisible(e.getValue() == DocumentType.LINK);
    });

    var saveButton = new Button("Save", e -> {
        if (!StringUtils.hasText(nameField.getValue())) {
            nameField.setInvalid(true);
            nameField.setErrorMessage("Name is required");
            return;
        }
        try {
            var type = typeSelect.getValue();
            if (type == DocumentType.PDF && pdfData[0] == null) {
                Notification.show("Please upload a PDF file");
                return;
            }
            if (type == DocumentType.LINK && !StringUtils.hasText(urlField.getValue())) {
                urlField.setInvalid(true);
                urlField.setErrorMessage("URL is required");
                return;
            }
            competitionService.addDocument(competitionId, nameField.getValue().trim(),
                    type, pdfData[0], pdfContentType[0],
                    type == DocumentType.LINK ? urlField.getValue().trim() : null,
                    getCurrentUserId());
            // Refresh the documents grid by navigating back
            UI.getCurrent().navigate("competitions/" + competition.getShortName());
            var notification = Notification.show("Document added successfully");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
        } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage());
        }
    });

    var cancelButton = new Button("Cancel", e -> dialog.close());

    var form = new VerticalLayout(nameField, typeSelect, upload, urlField);
    form.setPadding(false);
    dialog.add(form);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
}

private void moveDocument(Grid<CompetitionDocument> grid, CompetitionDocument doc, int direction) {
    var docs = competitionService.getDocuments(competitionId);
    var ids = docs.stream().map(CompetitionDocument::getId).collect(java.util.stream.Collectors.toList());
    int currentIndex = ids.indexOf(doc.getId());
    int targetIndex = currentIndex + direction;
    if (targetIndex < 0 || targetIndex >= ids.size()) return;
    ids.remove(currentIndex);
    ids.add(targetIndex, doc.getId());
    try {
        competitionService.reorderDocuments(competitionId, ids, getCurrentUserId());
        grid.setItems(competitionService.getDocuments(competitionId));
    } catch (IllegalArgumentException ex) {
        Notification.show(ex.getMessage());
    }
}

private void openEditDocumentNameDialog(Grid<CompetitionDocument> grid, CompetitionDocument doc) {
    var dialog = new Dialog();
    dialog.setHeaderTitle("Edit Document Name");

    var nameField = new TextField("Name");
    nameField.setValue(doc.getName());
    nameField.setWidthFull();

    var saveButton = new Button("Save", e -> {
        if (!StringUtils.hasText(nameField.getValue())) {
            nameField.setInvalid(true);
            nameField.setErrorMessage("Name is required");
            return;
        }
        try {
            competitionService.updateDocumentName(doc.getId(),
                    nameField.getValue().trim(), getCurrentUserId());
            grid.setItems(competitionService.getDocuments(competitionId));
            var notification = Notification.show("Document name updated");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
        } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage());
        }
    });

    var cancelButton = new Button("Cancel", e -> dialog.close());
    dialog.add(nameField);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
}

private void openDeleteDocumentDialog(Grid<CompetitionDocument> grid, CompetitionDocument doc) {
    var dialog = new Dialog();
    dialog.setHeaderTitle("Delete Document");
    dialog.add("Are you sure you want to delete \"" + doc.getName() + "\"?");

    var confirmButton = new Button("Delete", e -> {
        try {
            competitionService.removeDocument(doc.getId(), getCurrentUserId());
            grid.setItems(competitionService.getDocuments(competitionId));
            var notification = Notification.show("Document deleted");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
        } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage());
            dialog.close();
        }
    });

    var cancelButton = new Button("Cancel", e -> dialog.close());
    dialog.getFooter().add(cancelButton, confirmButton);
    dialog.open();
}
```

- [ ] Add required imports to `CompetitionDetailView.java`:

```java
import app.meads.competition.CompetitionDocument;
import app.meads.competition.DocumentType;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;
```

- [ ] Run: `mvn test -Dtest=CompetitionDetailViewTest -Dsurefire.useFile=false`
- [ ] Verify: all tests PASS (existing + new)

### Step 3: REFACTOR — Review and run full suite

- [ ] Review view code for consistency with existing tab patterns
- [ ] Run: `mvn test -Dsurefire.useFile=false`
- [ ] Verify: full suite passes
- [ ] Commit: `git commit -m "Add Documents tab to CompetitionDetailView"`

---

## Task 5: Entrant UI — Document List in MyEntriesView

Show competition documents to entrants in their entries view.

**Files:**
- Modify: `src/test/java/app/meads/entry/MyEntriesViewTest.java`
- Modify: `src/main/java/app/meads/entry/internal/MyEntriesView.java`

**Context:** `MyEntriesViewTest` is in package `app.meads.entry` (NOT `internal`). It has fields: `admin` (User), `entrant` (User), `competition` (Competition), `division` (Division), `competitionService` (CompetitionService, already `@Autowired`). Constants: `ADMIN_EMAIL`, `ENTRANT_EMAIL`.

### Step 1: RED — Write UI test for document list

- [ ] Add test to `MyEntriesViewTest.java`:

```java
@Test
@WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
void shouldDisplayCompetitionDocuments() {
    // Add a link document to the competition (admin already available as field)
    competitionService.addDocument(competition.getId(), "Competition Rules",
            DocumentType.LINK, null, null, "https://example.com/rules", admin.getId());

    UI.getCurrent().navigate("competitions/" + competition.getShortName()
            + "/divisions/" + division.getShortName() + "/my-entries");

    // Find the documents section
    var anchors = _find(Anchor.class);
    var docAnchor = anchors.stream()
            .filter(a -> "Competition Rules".equals(a.getText()))
            .findFirst();
    assertThat(docAnchor).isPresent();
}
```

- [ ] Add necessary imports: `DocumentType`, `Anchor`
- [ ] Run: `mvn test -Dtest=MyEntriesViewTest#shouldDisplayCompetitionDocuments -Dsurefire.useFile=false`
- [ ] Verify: FAIL — no document anchors in the view

### Step 2: GREEN — Add document list to MyEntriesView

- [ ] In `MyEntriesView.java`, add a documents section in the `beforeEnter` method (after loading competition/division data, before or after the entries grid). Add the documents between the header/credits section and the entries grid:

```java
private VerticalLayout createDocumentsSection() {
    var docs = competitionService.getDocuments(competition.getId());
    if (docs.isEmpty()) {
        return new VerticalLayout(); // empty, hidden
    }

    var section = new VerticalLayout();
    section.setPadding(false);
    section.setSpacing(false);

    var header = new Span("Competition Documents");
    header.getStyle().set("font-weight", "600");
    section.add(header);

    for (var doc : docs) {
        if (doc.getType() == DocumentType.LINK) {
            var anchor = new Anchor(doc.getUrl(), doc.getName());
            anchor.setTarget("_blank");
            section.add(anchor);
        } else {
            var streamResource = new StreamResource(doc.getName() + ".pdf",
                    () -> new ByteArrayInputStream(
                            competitionService.getDocument(doc.getId()).getData()));
            var anchor = new Anchor(streamResource, doc.getName());
            anchor.getElement().setAttribute("download", true);
            section.add(anchor);
        }
    }
    return section;
}
```

- [ ] Call `createDocumentsSection()` in the appropriate place in the view's build method (typically in `beforeEnter` after building header content and before the entries grid). The exact location depends on the existing code structure — add it after credits info but before the grid.

- [ ] Add required imports:

```java
import app.meads.competition.DocumentType;
import java.io.ByteArrayInputStream;
import com.vaadin.flow.server.StreamResource;
```

Note: `StreamResource` and `ByteArrayInputStream` may already be imported. `Anchor` is likely already imported. Only add `DocumentType` if missing.

- [ ] Run: `mvn test -Dtest=MyEntriesViewTest -Dsurefire.useFile=false`
- [ ] Verify: all tests PASS (existing + new)

### Step 3: REFACTOR — Review and run full suite

- [ ] Review view code for consistency with existing patterns
- [ ] Run: `mvn test -Dsurefire.useFile=false`
- [ ] Verify: full suite passes
- [ ] Commit: `git commit -m "Add competition documents list to MyEntriesView"`

---

## Task 6: Documentation Updates

Update all project docs to reflect the new feature.

**Files:**
- Modify: `docs/SESSION_CONTEXT.md`
- Modify: `CLAUDE.md`
- Modify: `docs/walkthrough/manual-test.md`
- Delete: `docs/plans/2026-03-10-competition-documents-design.md` (completed)
- Delete: `docs/plans/2026-03-10-competition-documents-plan.md` (completed)

- [ ] Update `CLAUDE.md`:
  - Add `CompetitionDocument` and `DocumentType` to the competition module package layout
  - Add `CompetitionDocumentRepository` to `internal/` in the package layout
  - Update migration version note: "V14 is competition documents"
  - Update highest migration version to V14

- [ ] Update `docs/SESSION_CONTEXT.md`:
  - Add `CompetitionDocument` and `DocumentType` to competition module entities table
  - Add document methods to `CompetitionService` summary
  - Update `CompetitionDetailView` description to include Documents tab
  - Update `MyEntriesView` description to include document list
  - Add V14 to competition module migrations
  - Update test count (run `mvn test -Dsurefire.useFile=false` and record)
  - Move "Competition documents" from "What's Next" to completed work
  - Remove design/plan docs from documentation structure (they'll be deleted)

- [ ] Update `docs/walkthrough/manual-test.md`:
  - Add section for testing document management (add PDF, add link, reorder, rename, delete)
  - Add steps for verifying documents appear in entrant view

- [ ] Delete completed design and plan docs:
  - `docs/plans/2026-03-10-competition-documents-design.md`
  - `docs/plans/2026-03-10-competition-documents-plan.md`

- [ ] Run: `mvn test -Dsurefire.useFile=false` (final verification)
- [ ] Commit: `git commit -m "Update docs for competition documents feature"`
