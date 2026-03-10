package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CompetitionRepositoryTest {

    @Autowired
    CompetitionRepository competitionRepository;

    @Test
    void shouldSaveAndRetrieveCompetition() {
        var competition = new Competition("Regional Mead Festival", "regional-mead-festival",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto");

        competitionRepository.save(competition);
        var found = competitionRepository.findById(competition.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Regional Mead Festival");
        assertThat(found.get().getStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(found.get().getEndDate()).isEqualTo(LocalDate.of(2026, 6, 17));
        assertThat(found.get().getLocation()).isEqualTo("Porto");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNull();
    }

    @Test
    void shouldSaveAndRetrieveCompetitionWithLogo() {
        var competition = new Competition("Festival with Logo", "festival-with-logo",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null);
        byte[] logo = new byte[]{1, 2, 3, 4, 5};
        competition.updateLogo(logo, "image/png");

        competitionRepository.save(competition);
        var found = competitionRepository.findById(competition.getId());

        assertThat(found).isPresent();
        assertThat(found.get().hasLogo()).isTrue();
        assertThat(found.get().getLogo()).isEqualTo(logo);
        assertThat(found.get().getLogoContentType()).isEqualTo("image/png");
    }

    @Test
    void shouldSaveAndRetrieveCompetitionWithContactEmail() {
        var competition = new Competition("Email Competition", "email-competition",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), null);
        competition.updateContactEmail("organizer@example.com");

        competitionRepository.save(competition);
        var found = competitionRepository.findById(competition.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getContactEmail()).isEqualTo("organizer@example.com");
    }

    @Test
    void shouldSaveCompetitionWithNullLocation() {
        var competition = new Competition("No Location Competition", "no-location-competition",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null);

        competitionRepository.save(competition);
        var found = competitionRepository.findById(competition.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getLocation()).isNull();
        assertThat(found.get().hasLogo()).isFalse();
    }
}
