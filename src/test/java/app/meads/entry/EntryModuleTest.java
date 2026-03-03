package app.meads.entry;

import app.meads.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class EntryModuleTest {

    @Autowired
    EntryService entryService;

    @Test
    void shouldBootstrapEntryModule() {
        assertThat(entryService).isNotNull();
    }
}
