package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.CategoryScope;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingTableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class JudgingTableRepositoryTest {

    @Autowired
    JudgingRepository judgingRepository;

    @Autowired
    JudgingTableRepository judgingTableRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    DivisionCategoryRepository divisionCategoryRepository;

    @Autowired
    UserRepository userRepository;

    private Division createAndSaveDivision() {
        var competition = competitionRepository.save(new Competition("Test Competition", "test-competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        return divisionRepository.save(new Division(competition.getId(),
                "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
    }

    private Judging createAndSaveJudging(Division division) {
        return judgingRepository.save(new Judging(division.getId()));
    }

    private DivisionCategory createAndSaveJudgingCategory(Division division) {
        return divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Traditional Mead",
                "Traditional mead description", null, 1, CategoryScope.JUDGING));
    }

    private User createAndSaveJudge(String email) {
        return userRepository.save(new User(email, "Judge",
                UserStatus.ACTIVE, Role.USER));
    }

    @Test
    void shouldSaveAndFindJudgingTableWithAssignedJudges() {
        var division = createAndSaveDivision();
        var judging = createAndSaveJudging(division);
        var category = createAndSaveJudgingCategory(division);
        var judge1 = createAndSaveJudge("judge1@test.com");
        var judge2 = createAndSaveJudge("judge2@test.com");

        var table = new JudgingTable(judging.getId(), "Table A",
                category.getId(), LocalDate.of(2026, 7, 1));
        table.assignJudge(judge1.getId());
        table.assignJudge(judge2.getId());

        judgingTableRepository.save(table);

        var found = judgingTableRepository.findById(table.getId()).orElseThrow();

        assertThat(found.getJudgingId()).isEqualTo(judging.getId());
        assertThat(found.getName()).isEqualTo("Table A");
        assertThat(found.getDivisionCategoryId()).isEqualTo(category.getId());
        assertThat(found.getScheduledDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(found.getStatus()).isEqualTo(JudgingTableStatus.NOT_STARTED);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getAssignments()).hasSize(2);
        assertThat(found.getAssignments())
                .extracting(a -> a.getJudgeUserId())
                .containsExactlyInAnyOrder(judge1.getId(), judge2.getId());
        assertThat(found.getAssignments())
                .allSatisfy(a -> assertThat(a.getAssignedAt()).isNotNull());
    }

    @Test
    void shouldFindByJudgingId() {
        var division = createAndSaveDivision();
        var judging = createAndSaveJudging(division);
        var category = createAndSaveJudgingCategory(division);

        judgingTableRepository.save(new JudgingTable(judging.getId(), "Table A", category.getId(), null));
        judgingTableRepository.save(new JudgingTable(judging.getId(), "Table B", category.getId(), null));

        var found = judgingTableRepository.findByJudgingId(judging.getId());

        assertThat(found).hasSize(2);
        assertThat(found).extracting(JudgingTable::getName)
                .containsExactlyInAnyOrder("Table A", "Table B");
    }

    @Test
    void shouldRemoveJudgeAssignmentViaOrphanRemoval() {
        var division = createAndSaveDivision();
        var judging = createAndSaveJudging(division);
        var category = createAndSaveJudgingCategory(division);
        var judge = createAndSaveJudge("judge@test.com");

        var table = new JudgingTable(judging.getId(), "Table A", category.getId(), null);
        table.assignJudge(judge.getId());
        var saved = judgingTableRepository.save(table);

        saved.removeJudge(judge.getId());
        judgingTableRepository.save(saved);

        var refound = judgingTableRepository.findById(saved.getId()).orElseThrow();
        assertThat(refound.getAssignments()).isEmpty();
    }

    @Test
    void shouldFindByJudgeUserIdAcrossTables() {
        var division = createAndSaveDivision();
        var judging = createAndSaveJudging(division);
        var category = createAndSaveJudgingCategory(division);
        var judge = createAndSaveJudge("judge@test.com");
        var otherJudge = createAndSaveJudge("other@test.com");

        var tableA = new JudgingTable(judging.getId(), "Table A", category.getId(), null);
        tableA.assignJudge(judge.getId());
        judgingTableRepository.save(tableA);

        var tableB = new JudgingTable(judging.getId(), "Table B", category.getId(), null);
        tableB.assignJudge(judge.getId());
        tableB.assignJudge(otherJudge.getId());
        judgingTableRepository.save(tableB);

        var tableC = new JudgingTable(judging.getId(), "Table C", category.getId(), null);
        tableC.assignJudge(otherJudge.getId());
        judgingTableRepository.save(tableC);

        var found = judgingTableRepository.findByJudgeUserId(judge.getId());

        assertThat(found).extracting(JudgingTable::getName)
                .containsExactlyInAnyOrder("Table A", "Table B");
    }

    @Test
    void shouldReturnEmptyForNoTables() {
        var found = judgingTableRepository.findByJudgingId(UUID.randomUUID());
        assertThat(found).isEmpty();
    }
}
