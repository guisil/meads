package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.DivisionStatus;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoresheetPdfServiceTest {

    @InjectMocks ScoresheetPdfService service;

    @Mock ScoresheetService scoresheetService;
    @Mock JudgingService judgingService;
    @Mock EntryService entryService;
    @Mock UserService userService;
    @Mock CompetitionService competitionService;
    @Mock MessageSource messageSource;

    UUID scoresheetId;
    UUID entryId;
    UUID divisionId;
    UUID adminUserId;
    UUID ownerUserId;
    UUID otherUserId;
    Scoresheet sheet;

    @BeforeEach
    void setUp() {
        scoresheetId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        divisionId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        ownerUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        sheet = mock(Scoresheet.class);
        given(sheet.getId()).willReturn(scoresheetId);
        given(sheet.getEntryId()).willReturn(entryId);
        given(sheet.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(sheet.getFields()).willReturn(List.of());
        given(scoresheetService.findById(scoresheetId)).willReturn(Optional.of(sheet));
        given(scoresheetService.findByEntryIdOrderBySubmittedAtAsc(entryId))
                .willReturn(List.of(sheet));

        var entry = mock(Entry.class);
        given(entry.getDivisionId()).willReturn(divisionId);
        given(entry.getUserId()).willReturn(ownerUserId);
        given(entry.getEntryCode()).willReturn("AMA-1");
        given(entry.getMeadName()).willReturn("Sample Mead");
        given(entryService.findEntryById(entryId)).willReturn(entry);

        var division = mock(Division.class);
        given(division.getName()).willReturn("Amateur");
        given(division.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
        given(division.getCompetitionId()).willReturn(UUID.randomUUID());
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("Test Competition");
        given(competitionService.findCompetitionById(any())).willReturn(competition);

        given(messageSource.getMessage(any(String.class), any(), any(String.class), any(Locale.class)))
                .willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldGenerateAnonymizedPdfForEntryOwner() {
        given(competitionService.isAuthorizedForDivision(divisionId, ownerUserId)).willReturn(false);
        var pdf = service.generatePdf(scoresheetId, ownerUserId,
                AnonymizationLevel.ANONYMIZED, Locale.ENGLISH);
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void shouldGenerateFullPdfForAdmin() {
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        var pdf = service.generatePdf(scoresheetId, adminUserId,
                AnonymizationLevel.FULL, Locale.ENGLISH);
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void shouldRejectFullLevelForNonAdmin() {
        given(competitionService.isAuthorizedForDivision(divisionId, ownerUserId)).willReturn(false);
        assertThatThrownBy(() -> service.generatePdf(scoresheetId, ownerUserId,
                AnonymizationLevel.FULL, Locale.ENGLISH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.unauthorized");
    }

    @Test
    void shouldRejectAnonymizedLevelForNonOwnerNonAdmin() {
        given(competitionService.isAuthorizedForDivision(divisionId, otherUserId)).willReturn(false);
        assertThatThrownBy(() -> service.generatePdf(scoresheetId, otherUserId,
                AnonymizationLevel.ANONYMIZED, Locale.ENGLISH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.unauthorized");
    }
}
