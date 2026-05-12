package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.DivisionStatus;
import app.meads.entry.EntryService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
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

    private static final Font FONT_TITLE = new Font(BASE_BOLD, 14);
    private static final Font FONT_HEADER = new Font(BASE_BOLD, 11);
    private static final Font FONT_NORMAL = new Font(BASE_REGULAR, 10);
    private static final Font FONT_BOLD = new Font(BASE_BOLD, 10);
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

        int ordinal = computeJudgeOrdinal(sheet);
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
            document.add(new Paragraph(competition.getName() + " — " + division.getName(), FONT_TITLE));
            document.add(new Paragraph(msg("scoresheet.pdf.heading", locale), FONT_HEADER));
            document.add(new Paragraph(" ", FONT_NORMAL));

            var meta = new PdfPTable(2);
            meta.setWidthPercentage(100);
            meta.setWidths(new float[]{1, 3});
            addCell(meta, msg("scoresheet.pdf.entry-code", locale), FONT_BOLD);
            addCell(meta, entry.getEntryCode(), FONT_NORMAL);
            addCell(meta, msg("scoresheet.pdf.mead-name", locale), FONT_BOLD);
            addCell(meta, entry.getMeadName(), FONT_NORMAL);
            if (category != null) {
                addCell(meta, msg("scoresheet.pdf.category", locale), FONT_BOLD);
                addCell(meta, category.getCode() + " — " + category.getName(), FONT_NORMAL);
            }
            addCell(meta, msg("scoresheet.pdf.judge", locale), FONT_BOLD);
            addCell(meta, formatJudgeLabel(sheet, ordinal, level, locale), FONT_NORMAL);
            if (sheet.getCommentLanguage() != null) {
                addCell(meta, msg("scoresheet.pdf.comment-language", locale), FONT_BOLD);
                addCell(meta, sheet.getCommentLanguage(), FONT_NORMAL);
            }
            document.add(meta);

            document.add(new Paragraph(" ", FONT_NORMAL));
            document.add(new Paragraph(msg("scoresheet.pdf.scores", locale), FONT_HEADER));

            var scores = new PdfPTable(3);
            scores.setWidthPercentage(100);
            scores.setWidths(new float[]{2, 1, 4});
            addHeader(scores, msg("scoresheet.pdf.field", locale));
            addHeader(scores, msg("scoresheet.pdf.value", locale));
            addHeader(scores, msg("scoresheet.pdf.comment", locale));
            for (ScoreField field : sheet.getFields()) {
                addCell(scores, field.getFieldName(), FONT_NORMAL);
                addCell(scores, formatValue(field), FONT_NORMAL);
                addCell(scores, nullSafe(field.getComment()), FONT_SMALL);
            }
            document.add(scores);

            document.add(new Paragraph(" ", FONT_NORMAL));
            document.add(new Paragraph(msg("scoresheet.pdf.total", locale) + ": "
                    + (sheet.getTotalScore() != null ? sheet.getTotalScore() : "—"), FONT_BOLD));
            document.add(new Paragraph(" ", FONT_NORMAL));
            if (sheet.getOverallComments() != null && !sheet.getOverallComments().isBlank()) {
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

    private String formatValue(ScoreField f) {
        if (f.getValue() == null) {
            return "—";
        }
        return f.getValue() + " / " + f.getMaxValue();
    }

    private void addCell(PdfPTable table, String text, Font font) {
        var cell = new PdfPCell(new Paragraph(text != null ? text : "", font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private void addHeader(PdfPTable table, String text) {
        var cell = new PdfPCell(new Paragraph(text, FONT_BOLD));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

}
