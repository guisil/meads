package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.entry.EntryService;
import app.meads.judging.internal.BosPlacementRepository;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingServiceImpl;
import app.meads.judging.internal.JudgingTableRepository;
import app.meads.judging.internal.MedalAwardRepository;
import app.meads.judging.internal.ScoresheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class JudgingServiceMedalRoundTest {

    @InjectMocks
    JudgingServiceImpl service;

    @Mock JudgingRepository judgingRepository;
    @Mock JudgingTableRepository judgingTableRepository;
    @Mock ScoresheetRepository scoresheetRepository;
    @Mock CategoryJudgingConfigRepository categoryConfigRepository;
    @Mock MedalAwardRepository medalAwardRepository;
    @Mock CompetitionService competitionService;
    @Mock JudgeProfileService judgeProfileService;
    @Mock ScoresheetService scoresheetService;
    @Mock BosPlacementRepository bosPlacementRepository;
    @Mock EntryService entryService;
    @Mock CoiCheckService coiCheckService;
    @Mock ApplicationEventPublisher eventPublisher;

    UUID divisionId;
    UUID divisionCategoryId;
    UUID adminUserId;
    Division division;
    DivisionCategory category;
    Judging judging;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        divisionCategoryId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        division = new Division(UUID.randomUUID(), "Amateur", "amateur",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 6, 1, 23, 59),
                "Europe/Lisbon");
        category = new DivisionCategory(divisionId, null, "M1A", "Dry Trad",
                "Description", null, 0);
        judging = new Judging(divisionId);
    }

    @Test
    void shouldStartTableWhenSufficientJudges() {
        var table = new JudgingTable(judging.getId(), "T1", divisionCategoryId, null);
        table.assignJudge(UUID.randomUUID());
        table.assignJudge(UUID.randomUUID());
        given(judgingTableRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.empty());
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(judgingTableRepository.save(any(JudgingTable.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.startTable(table.getId(), adminUserId);

        assertThat(table.getStatus()).isEqualTo(JudgingTableStatus.ROUND_1);
        assertThat(judging.getPhase()).isEqualTo(JudgingPhase.ACTIVE);
        then(scoresheetService).should().createScoresheetsForTable(table.getId());
    }

    @Test
    void shouldRejectStartTableWhenInsufficientJudges() {
        var table = new JudgingTable(judging.getId(), "T1", divisionCategoryId, null);
        table.assignJudge(UUID.randomUUID()); // only 1 judge, min is 2
        given(judgingTableRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.startTable(table.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.judging-table.too-few-judges");

        assertThat(table.getStatus()).isEqualTo(JudgingTableStatus.NOT_STARTED);
        then(scoresheetService).should(never()).createScoresheetsForTable(any());
    }

    @Test
    void shouldConfigureCategoryMedalRoundCreatingNew() {
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.empty());
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var config = service.configureCategoryMedalRound(divisionCategoryId,
                MedalRoundMode.SCORE_BASED, adminUserId);

        assertThat(config.getMedalRoundMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
    }

    @Test
    void shouldConfigureCategoryMedalRoundUpdatingExisting() {
        var existing = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(existing));
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var config = service.configureCategoryMedalRound(divisionCategoryId,
                MedalRoundMode.SCORE_BASED, adminUserId);

        assertThat(config.getMedalRoundMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
    }

    @Test
    void shouldStartMedalRoundFromReady() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        config.markReady();
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.startMedalRound(divisionCategoryId, adminUserId);

        assertThat(config.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.ACTIVE);
    }

    @Test
    void shouldCompleteMedalRoundFromActive() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        config.markReady();
        config.startMedalRound();
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.completeMedalRound(divisionCategoryId, adminUserId);

        assertThat(config.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.COMPLETE);
    }

    @Test
    void shouldResetMedalRoundAndDeleteAwards() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        config.markReady();
        config.startMedalRound();
        judging.markActive();
        var award1 = new MedalAward(UUID.randomUUID(), divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(medalAwardRepository.findByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(award1));
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.resetMedalRound(divisionCategoryId, adminUserId);

        assertThat(config.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.READY);
        then(medalAwardRepository).should().deleteAll(List.of(award1));
    }

    @Test
    void shouldFindCategoryConfigsForDivisionAndLazyCreateMissing() {
        var existingCat = new DivisionCategory(divisionId, null, "M1A", "Dry Trad",
                "Desc", null, 0, app.meads.competition.CategoryScope.JUDGING);
        var newCat = new DivisionCategory(divisionId, null, "M1B", "Medium Trad",
                "Desc", null, 1, app.meads.competition.CategoryScope.JUDGING);
        var existingConfig = new CategoryJudgingConfig(existingCat.getId(), MedalRoundMode.SCORE_BASED);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findJudgingCategories(divisionId))
                .willReturn(List.of(existingCat, newCat));
        given(categoryConfigRepository.findByDivisionCategoryId(existingCat.getId()))
                .willReturn(Optional.of(existingConfig));
        given(categoryConfigRepository.findByDivisionCategoryId(newCat.getId()))
                .willReturn(Optional.empty());
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = service.findCategoryConfigsForDivision(divisionId, adminUserId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CategoryJudgingConfig::getDivisionCategoryId)
                .containsExactlyInAnyOrder(existingCat.getId(), newCat.getId());
        // The missing one should have been saved lazily with default COMPARATIVE
        then(categoryConfigRepository).should().save(any(CategoryJudgingConfig.class));
    }

    @Test
    void shouldRejectReopenMedalRoundWhenJudgingNotActive() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        config.markReady();
        config.startMedalRound();
        config.completeMedalRound();
        judging.markActive();
        judging.startBos(); // phase = BOS, not ACTIVE
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));

        assertThatThrownBy(() -> service.reopenMedalRound(divisionCategoryId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.medal-round.judging-not-active");
    }
}
