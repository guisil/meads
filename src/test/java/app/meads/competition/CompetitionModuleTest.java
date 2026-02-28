package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class CompetitionModuleTest {

    @Autowired
    EventService eventService;

    @Autowired
    CompetitionService competitionService;

    @Test
    void shouldBootstrapCompetitionModule() {
        assertThat(eventService).isNotNull();
        assertThat(competitionService).isNotNull();
    }
}
