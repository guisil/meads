package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CategoryDisplay;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfCopy;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class ScoresheetPdfService {

    private static final BaseFont BASE_REGULAR;
    private static final BaseFont BASE_BOLD;

    static {
        try {
            BASE_REGULAR = BaseFont.createFont("fonts/LiberationSans-Regular.ttf",
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            BASE_BOLD = BaseFont.createFont("fonts/LiberationSans-Bold.ttf",
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Failed to load Liberation Sans fonts: " + e.getMessage());
        }
    }

    private static final Color SECONDARY = new Color(0x5F, 0x63, 0x68);
    private static final Color SUCCESS = new Color(0x2E, 0x7D, 0x32);
    private static final Color OUTCOME_BG = new Color(0xEA, 0xF1, 0xFB);

    private static final Font FONT_TITLE = new Font(BASE_BOLD, 14);
    private static final Font FONT_HEADER = new Font(BASE_BOLD, 11);
    private static final Font FONT_RESULT = new Font(BASE_BOLD, 12);
    private static final Font FONT_TOTAL = new Font(BASE_BOLD, 16);
    private static final Font FONT_NORMAL = new Font(BASE_REGULAR, 10);
    private static final Font FONT_BOLD = new Font(BASE_BOLD, 10);
    private static final Font FONT_CRITERION = new Font(BASE_BOLD, 11);
    private static final Font FONT_COMMENT = new Font(BASE_REGULAR, 9, Font.NORMAL, SECONDARY);
    private static final Font FONT_ADVANCED = new Font(BASE_BOLD, 10, Font.NORMAL, SUCCESS);
    private static final Font FONT_SMALL = new Font(BASE_REGULAR, 8);

    private final ScoresheetService scoresheetService;
    private final JudgingService judgingService;
    private final EntryService entryService;
    private final UserService userService;
    private final CompetitionService competitionService;
    private final MessageSource messageSource;

    public ScoresheetPdfService(ScoresheetService scoresheetService,
                                 JudgingService judgingService,
                                 EntryService entryService,
                                 UserService userService,
                                 CompetitionService competitionService,
                                 MessageSource messageSource) {
        this.scoresheetService = scoresheetService;
        this.judgingService = judgingService;
        this.entryService = entryService;
        this.userService = userService;
        this.competitionService = competitionService;
        this.messageSource = messageSource;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID scoresheetId, UUID requestingUserId,
                               AnonymizationLevel level, Locale locale) {
        var sheet = scoresheetService.findById(scoresheetId)
                .orElseThrow(() -> new BusinessRuleException("error.awards.scoresheet-not-found"));
        if (sheet.getStatus() != ScoresheetStatus.SUBMITTED) {
            throw new BusinessRuleException("error.awards.scoresheet-not-found");
        }
        var entry = entryService.findEntryById(sheet.getEntryId());
        var division = competitionService.findDivisionById(entry.getDivisionId());
        boolean isAdmin = competitionService.isAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        boolean isOwner = entry.getUserId().equals(requestingUserId);

        if (level == AnonymizationLevel.FULL && !isAdmin) {
            throw new BusinessRuleException("error.awards.unauthorized");
        }
        if (level == AnonymizationLevel.ANONYMIZED) {
            if (!isAdmin && !isOwner) {
                throw new BusinessRuleException("error.awards.unauthorized");
            }
            if (!isAdmin && division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
                throw new BusinessRuleException("error.awards.not-published");
            }
        }

        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var categoryId = entry.getFinalCategoryId() != null
                ? entry.getFinalCategoryId() : entry.getInitialCategoryId();
        var category = categoryId != null
                ? competitionService.findDivisionCategoryById(categoryId) : null;

        var baos = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // Header: logo + title on the same line (mirrors the dialog header bar).
            // Title block = "Competition — Division", "MJP Scoresheet", and the
            // entrant's "entryNumber — meadName".
            var titleCell = new PdfPCell();
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.addElement(new Paragraph(competition.getName() + " — " + division.getName(), FONT_TITLE));
            titleCell.addElement(new Paragraph(
                    division.getScoringSystem().name() + " " + msg("scoresheet.pdf.heading", locale), FONT_HEADER));
            titleCell.addElement(new Paragraph(
                    formatEntryNumber(entry, division) + " — " + entry.getMeadName(), FONT_BOLD));
            Image logoImg = null;
            if (competition.hasLogo()) {
                try {
                    logoImg = Image.getInstance(competition.getLogo());
                    logoImg.scaleToFit(80, 48);
                } catch (Exception ex) {
                    log.warn("Could not embed competition logo in scoresheet PDF: {}", ex.getMessage());
                }
            }
            if (logoImg != null) {
                var header = new PdfPTable(2);
                header.setWidthPercentage(100);
                header.setWidths(new float[]{1, 6});
                var logoCell = new PdfPCell(logoImg, false);
                logoCell.setBorder(Rectangle.NO_BORDER);
                logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                header.addCell(logoCell);
                header.addCell(titleCell);
                document.add(header);
            } else {
                document.add(new Paragraph(competition.getName() + " — " + division.getName(), FONT_TITLE));
                document.add(new Paragraph(
                        division.getScoringSystem().name() + " " + msg("scoresheet.pdf.heading", locale), FONT_HEADER));
                document.add(new Paragraph(
                        formatEntryNumber(entry, division) + " — " + entry.getMeadName(), FONT_BOLD));
            }
            document.add(new Paragraph(" ", FONT_SMALL));

            // Mead details as label: value lines (mirrors the dialog's detail block).
            if (category != null) {
                document.add(labelValue(msg("scoresheet.pdf.category", locale),
                        CategoryDisplay.codeAndName(category, locale, k -> msg(k, locale))));
            }
            document.add(labelValue(msg("entries.view.sweetness", locale),
                    enumLabel("entry.sweetness.", entry.getSweetness(), locale)));
            document.add(labelValue(msg("entries.view.strength", locale),
                    enumLabel("entry.strength.", entry.getStrength(), locale)));
            document.add(labelValue(msg("entries.view.abv", locale),
                    entry.getAbv() == null ? "—" : entry.getAbv().toPlainString() + "%"));
            document.add(labelValue(msg("entries.view.carbonation", locale),
                    enumLabel("entry.carbonation.", entry.getCarbonation(), locale)));
            document.add(labelValue(msg("entries.view.honey", locale), dash(entry.getHoneyVarieties())));
            document.add(labelValue(msg("entries.view.other-ingredients", locale),
                    dash(entry.getOtherIngredients())));
            document.add(labelValue(msg("entries.view.wood-aged", locale),
                    msg(entry.isWoodAged() ? "entries.view.wood-aged.yes" : "entries.view.wood-aged.no", locale)));
            document.add(labelValue(msg("entries.view.wood-details", locale), dash(entry.getWoodAgeingDetails())));
            document.add(labelValue(msg("entries.view.additional-info", locale),
                    dash(entry.getAdditionalInformation())));

            // Outcome banner: medal + Best of Show in a highlighted box (mirrors
            // the dialog's outcome banner). Omitted when the entry won neither.
            var medal = judgingService.findMedalAwardByEntryId(entry.getId())
                    .map(MedalAward::getMedal).orElse(null);
            var bosPlace = judgingService.findBosPlacementByEntryId(entry.getId())
                    .map(BosPlacement::getPlace).orElse(null);
            if (medal != null || bosPlace != null) {
                document.add(new Paragraph(" ", FONT_SMALL));
                var box = new PdfPTable(1);
                box.setWidthPercentage(100);
                var cell = new PdfPCell();
                cell.setBackgroundColor(OUTCOME_BG);
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPadding(8);
                if (medal != null) {
                    cell.addElement(new Paragraph(msg("my-results.column.medal", locale) + ": "
                            + msg("my-results.medal." + medal.name().toLowerCase(Locale.ROOT), locale), FONT_RESULT));
                }
                if (bosPlace != null) {
                    cell.addElement(new Paragraph(msg("awards.public.bos.heading", locale) + ": "
                            + msg("my-scoresheet.bos.place", locale, bosPlace), FONT_RESULT));
                }
                box.addCell(cell);
                document.add(box);
            }

            // Advanced-to-medal-round — green line, shown only when advanced
            // (mirrors the dialog; absence means "did not advance").
            if (sheet.isAdvancedToMedalRound()) {
                document.add(new Paragraph(msg("my-scoresheet.advanced", locale), FONT_ADVANCED));
            }

            // Judge identity appears only on the admin (FULL) PDF; comment language
            // is a small secondary line, like the dialog.
            if (level == AnonymizationLevel.FULL) {
                int ordinal = computeJudgeOrdinal(sheet);
                document.add(labelValue(msg("scoresheet.pdf.judge", locale),
                        formatJudgeLabel(sheet, ordinal, level, locale)));
            }
            if (sheet.getCommentLanguage() != null) {
                document.add(new Paragraph(msg("scoresheet.pdf.comment-language", locale) + ": "
                        + sheet.getCommentLanguage(), FONT_COMMENT));
            }

            document.add(new Paragraph(" ", FONT_SMALL));

            // Criteria — each as "Field: value / max" with its comment below,
            // instead of a Field/Value/Comment table (mirrors the dialog cards).
            for (ScoreField field : sheet.getFields()) {
                document.add(new Paragraph(field.getFieldName() + ": " + formatValue(field), FONT_CRITERION));
                if (field.getComment() != null && !field.getComment().isBlank()) {
                    var comment = new Paragraph(field.getComment(), FONT_COMMENT);
                    comment.setIndentationLeft(12);
                    document.add(comment);
                }
                document.add(new Paragraph(" ", FONT_SMALL));
            }

            // Total — prominent, with a separating rule above it.
            var totalSep = new Paragraph(" ", FONT_SMALL);
            document.add(totalSep);
            int maxTotal = sheet.getFields().stream().mapToInt(ScoreField::getMaxValue).sum();
            document.add(new Paragraph(msg("scoresheet.pdf.total", locale) + ": "
                    + (sheet.getTotalScore() != null ? sheet.getTotalScore() + " / " + maxTotal : "—"),
                    FONT_TOTAL));

            if (sheet.getOverallComments() != null && !sheet.getOverallComments().isBlank()) {
                document.add(new Paragraph(" ", FONT_SMALL));
                document.add(new Paragraph(msg("scoresheet.pdf.overall-comments", locale), FONT_HEADER));
                document.add(new Paragraph(sheet.getOverallComments(), FONT_NORMAL));
            }
            document.close();
        } catch (Exception e) {
            log.error("Failed to generate scoresheet PDF for {}", scoresheetId, e);
            throw new RuntimeException("Failed to generate scoresheet PDF", e);
        }
        log.info("Generated scoresheet PDF for {} (level={})", scoresheetId, level);
        return baos.toByteArray();
    }

    /**
     * Combines several scoresheet PDFs into one document (one scoresheet per
     * page). Each is generated via {@link #generatePdf} — so the same auth rules
     * apply per scoresheet — then merged with {@link PdfCopy}.
     */
    @Transactional(readOnly = true)
    public byte[] generateBatchPdf(List<UUID> scoresheetIds, UUID requestingUserId,
                                   AnonymizationLevel level, Locale locale) {
        var baos = new ByteArrayOutputStream();
        var document = new Document();
        try {
            var copy = new PdfCopy(document, baos);
            document.open();
            for (var id : scoresheetIds) {
                var single = generatePdf(id, requestingUserId, level, locale);
                var reader = new PdfReader(single);
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    copy.addPage(copy.getImportedPage(reader, page));
                }
                reader.close();
            }
            document.close();
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate batch scoresheet PDF", e);
            throw new RuntimeException("Failed to generate batch scoresheet PDF", e);
        }
        log.info("Generated batch scoresheet PDF ({} sheets, level={})", scoresheetIds.size(), level);
        return baos.toByteArray();
    }

    private String formatEntryNumber(Entry entry, Division division) {
        var prefix = division.getEntryPrefix();
        return prefix != null && !prefix.isBlank()
                ? prefix + "-" + entry.getEntryNumber()
                : String.valueOf(entry.getEntryNumber());
    }

    private int computeJudgeOrdinal(Scoresheet target) {
        var sheets = scoresheetService.findByEntryIdOrderBySubmittedAtAsc(target.getEntryId());
        var submitted = new ArrayList<Scoresheet>();
        for (var s : sheets) {
            if (s.getStatus() == ScoresheetStatus.SUBMITTED) {
                submitted.add(s);
            }
        }
        for (int i = 0; i < submitted.size(); i++) {
            if (submitted.get(i).getId().equals(target.getId())) {
                return i + 1;
            }
        }
        return 1;
    }

    private String formatJudgeLabel(Scoresheet sheet, int ordinal, AnonymizationLevel level, Locale locale) {
        if (level == AnonymizationLevel.FULL && sheet.getFilledByJudgeUserId() != null) {
            try {
                User judge = userService.findById(sheet.getFilledByJudgeUserId());
                return judge.getName();
            } catch (Exception e) {
                log.warn("Could not load judge {} for scoresheet {}; falling back to ordinal",
                        sheet.getFilledByJudgeUserId(), sheet.getId());
            }
        }
        return msg("scoresheet.pdf.judge-ordinal", locale, ordinal);
    }

    private String enumLabel(String keyPrefix, Enum<?> value, Locale locale) {
        return value == null ? "—" : msg(keyPrefix + value.name(), locale);
    }

    private String dash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String formatValue(ScoreField f) {
        if (f.getValue() == null) {
            return "—";
        }
        return f.getValue() + " / " + f.getMaxValue();
    }

    /** A "Label: value" paragraph — bold label, normal value (mirrors the dialog). */
    private Paragraph labelValue(String label, String value) {
        var p = new Paragraph();
        p.add(new Chunk(label + ": ", FONT_BOLD));
        p.add(new Chunk(value != null ? value : "—", FONT_NORMAL));
        return p;
    }

    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

}
