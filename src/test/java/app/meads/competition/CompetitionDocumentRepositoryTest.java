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
