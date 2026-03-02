package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.MeadEventRepository;
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
class MeadEventRepositoryTest {

    @Autowired
    MeadEventRepository meadEventRepository;

    @Test
    void shouldSaveAndRetrieveMeadEvent() {
        var meadEvent = new MeadEvent("Regional Mead Festival",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto");

        meadEventRepository.save(meadEvent);
        var found = meadEventRepository.findById(meadEvent.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Regional Mead Festival");
        assertThat(found.get().getStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(found.get().getEndDate()).isEqualTo(LocalDate.of(2026, 6, 17));
        assertThat(found.get().getLocation()).isEqualTo("Porto");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNull();
    }

    @Test
    void shouldSaveAndRetrieveMeadEventWithLogo() {
        var meadEvent = new MeadEvent("Festival with Logo",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null);
        byte[] logo = new byte[]{1, 2, 3, 4, 5};
        meadEvent.updateLogo(logo, "image/png");

        meadEventRepository.save(meadEvent);
        var found = meadEventRepository.findById(meadEvent.getId());

        assertThat(found).isPresent();
        assertThat(found.get().hasLogo()).isTrue();
        assertThat(found.get().getLogo()).isEqualTo(logo);
        assertThat(found.get().getLogoContentType()).isEqualTo("image/png");
    }

    @Test
    void shouldSaveMeadEventWithNullLocation() {
        var meadEvent = new MeadEvent("No Location Event",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null);

        meadEventRepository.save(meadEvent);
        var found = meadEventRepository.findById(meadEvent.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getLocation()).isNull();
        assertThat(found.get().hasLogo()).isFalse();
    }
}
