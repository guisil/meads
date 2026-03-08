package app.meads.entry;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.entry.internal.EntryCreditRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
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
class EntryCreditRepositoryTest {

    @Autowired
    EntryCreditRepository creditRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    UserRepository userRepository;

    private Division createAndSaveDivision() {
        var competition = competitionRepository.save(new Competition("Test Competition", "test-competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        return divisionRepository.save(new Division(competition.getId(),
                "Home", "home", ScoringSystem.MJP));
    }

    private User createAndSaveUser() {
        return userRepository.save(new User("entrant@test.com", "Entrant",
                UserStatus.ACTIVE, Role.USER));
    }

    @Test
    void shouldSaveAndFindByDivisionIdAndUserId() {
        var division = createAndSaveDivision();
        var user = createAndSaveUser();

        var credit1 = new EntryCredit(division.getId(), user.getId(), 3,
                "WEBHOOK", "order-line-1");
        var credit2 = new EntryCredit(division.getId(), user.getId(), -1,
                "ADMIN", "admin@test.com");
        creditRepository.save(credit1);
        creditRepository.save(credit2);

        var found = creditRepository.findByDivisionIdAndUserId(
                division.getId(), user.getId());

        assertThat(found).hasSize(2);
        assertThat(found.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldSumAmountByDivisionIdAndUserId() {
        var division = createAndSaveDivision();
        var user = createAndSaveUser();

        creditRepository.save(new EntryCredit(division.getId(), user.getId(), 3,
                "WEBHOOK", "order-line-1"));
        creditRepository.save(new EntryCredit(division.getId(), user.getId(), 2,
                "WEBHOOK", "order-line-2"));
        creditRepository.save(new EntryCredit(division.getId(), user.getId(), -1,
                "ADMIN", "admin@test.com"));

        var balance = creditRepository.sumAmountByDivisionIdAndUserId(
                division.getId(), user.getId());

        assertThat(balance).isEqualTo(4); // 3 + 2 - 1
    }

    @Test
    void shouldReturnZeroBalanceForNonExistentUser() {
        var division = createAndSaveDivision();

        var balance = creditRepository.sumAmountByDivisionIdAndUserId(
                division.getId(), UUID.randomUUID());

        assertThat(balance).isZero();
    }

    @Test
    void shouldFindDistinctDivisionIdsByUserId() {
        var competition = competitionRepository.save(new Competition("Test Competition", "test-comp-2",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        var divisionA = divisionRepository.save(new Division(competition.getId(),
                "Home", "home", ScoringSystem.MJP));
        var divisionB = divisionRepository.save(new Division(competition.getId(),
                "Pro", "pro", ScoringSystem.MJP));
        var user = createAndSaveUser();

        creditRepository.save(new EntryCredit(divisionA.getId(), user.getId(), 1,
                "WEBHOOK", "ref-1"));
        creditRepository.save(new EntryCredit(divisionB.getId(), user.getId(), 2,
                "WEBHOOK", "ref-2"));

        var divisionIds = creditRepository.findDistinctDivisionIdsByUserId(user.getId());

        assertThat(divisionIds).hasSize(2);
        assertThat(divisionIds).containsExactlyInAnyOrder(
                divisionA.getId(), divisionB.getId());
    }
}
