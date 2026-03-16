package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class LabelPdfService {

    private static final int QR_CODE_SIZE = 130;
    private static final float TWO_LINE_HEIGHT = 21f; // 2 lines at 8pt font with 10pt leading

    private final MessageSource messageSource;
    private final Font fontNormal;
    private final Font fontBold;
    private final Font fontHeader;
    private final Font fontCompetition;
    private final Font fontDivision;
    private final Font fontFieldValue;
    private final Font fontCharLabel;
    private final Font fontCharValue;
    private final Font fontSmall;
    private final Font fontSmallBold;
    private final Font fontDisclaimer;

    public LabelPdfService(MessageSource messageSource) {
        this.messageSource = messageSource;
        FontFactory.registerDirectories();
        fontNormal = unicodeFont(Font.NORMAL, 8);
        fontBold = unicodeFont(Font.BOLD, 8);
        fontHeader = unicodeFont(Font.NORMAL, 9);
        fontCompetition = unicodeFont(Font.BOLD, 10);
        fontDivision = unicodeFont(Font.BOLD, 14);
        fontFieldValue = unicodeFont(Font.BOLD, 8);
        fontCharLabel = unicodeFont(Font.NORMAL, 7);
        fontCharValue = unicodeFont(Font.BOLD, 7);
        fontSmall = unicodeFont(Font.NORMAL, 7);
        fontSmallBold = unicodeFont(Font.BOLD, 7);
        fontDisclaimer = unicodeFont(Font.BOLD, 8);
    }

    private static Font unicodeFont(int style, float size) {
        // Try common sans-serif TTF fonts available on Linux (Docker) and macOS
        for (var name : List.of("Liberation Sans", "DejaVu Sans", "Arial")) {
            var font = FontFactory.getFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, size, style);
            if (font.getBaseFont() != null) {
                return font;
            }
        }
        // Fallback to built-in Helvetica (Latin-1 only — Polish chars won't render)
        log.warn("No Unicode TTF font found, falling back to Helvetica (Latin-1 only)");
        return FontFactory.getFont(FontFactory.HELVETICA, size, style);
    }

    public byte[] generateLabel(Entry entry, Competition competition,
                                 Division division, DivisionCategory category) {
        return generateLabels(List.of(entry), competition, division, id -> category);
    }

    public byte[] generateLabel(Entry entry, Competition competition,
                                 Division division, DivisionCategory category, Locale locale) {
        return generateLabels(List.of(entry), competition, division, id -> category, locale);
    }

    public byte[] generateLabels(List<Entry> entries, Competition competition,
                                  Division division,
                                  Function<UUID, DivisionCategory> categoryResolver) {
        return generateLabels(entries, competition, division, categoryResolver, Locale.ENGLISH);
    }

    public byte[] generateLabels(List<Entry> entries, Competition competition,
                                  Division division,
                                  Function<UUID, DivisionCategory> categoryResolver,
                                  Locale locale) {
        var baos = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    document.newPage();
                }
                var entry = entries.get(i);
                var category = categoryResolver.apply(entry.getInitialCategoryId());
                addPage(document, entry, competition, division, category, locale);
            }

            document.close();
        } catch (Exception e) {
            log.error("Failed to generate label PDF", e);
            throw new RuntimeException("Failed to generate label PDF", e);
        }

        log.info("Generated label PDF for {} entries in division {} of competition {}",
                entries.size(), division.getName(), competition.getShortName());
        return baos.toByteArray();
    }

    private void addPage(Document document, Entry entry, Competition competition,
                          Division division, DivisionCategory category, Locale locale) throws Exception {
        // Instruction header
        addInstructionHeader(document, competition, locale);

        document.add(new Paragraph(" ", fontSmall)); // spacer

        // 3 labels side by side
        var table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);

        for (int i = 0; i < 3; i++) {
            var cell = createLabelCell(entry, competition, division, category);
            table.addCell(cell);
        }

        document.add(table);
    }

    private void addInstructionHeader(Document document, Competition competition, Locale locale) throws Exception {
        var line1 = new Paragraph(msg("pdf.instructions.line1", locale), fontHeader);
        line1.setAlignment(Element.ALIGN_CENTER);
        document.add(line1);

        // Line 2: shipping address
        if (competition.getShippingAddress() != null && !competition.getShippingAddress().isBlank()) {
            var line2 = new Paragraph(
                    msg("pdf.instructions.post-to", locale) + " "
                            + competition.getShippingAddress().replace("\n", ", ") + ".",
                    fontHeader);
            line2.setAlignment(Element.ALIGN_CENTER);
            document.add(line2);
        }

        // Line 3: phone + website
        var contactParts = new StringBuilder();
        if (competition.getPhoneNumber() != null && !competition.getPhoneNumber().isBlank()) {
            contactParts.append(msg("pdf.instructions.tel", locale)).append(" ").append(competition.getPhoneNumber());
        }
        if (competition.getWebsite() != null && !competition.getWebsite().isBlank()) {
            if (!contactParts.isEmpty()) {
                contactParts.append(", ");
            }
            contactParts.append(msg("pdf.instructions.web", locale)).append(" ").append(competition.getWebsite());
        }
        if (!contactParts.isEmpty()) {
            var line3 = new Paragraph(contactParts.toString(), fontHeader);
            line3.setAlignment(Element.ALIGN_CENTER);
            document.add(line3);
        }
    }

    private PdfPCell createLabelCell(Entry entry, Competition competition,
                                      Division division, DivisionCategory category) throws Exception {
        var cell = new PdfPCell();
        cell.setBorderColor(Color.GRAY);
        cell.setBorderWidth(0.5f);
        cell.setPadding(8);
        cell.setVerticalAlignment(Element.ALIGN_TOP);

        // Competition name (bold)
        addParagraph(cell, competition.getName(), fontCompetition, Element.ALIGN_CENTER);

        // Division name (large)
        addParagraph(cell, division.getName(), fontDivision, Element.ALIGN_CENTER);

        addSeparator(cell);

        // ID
        var entryId = formatEntryId(entry, division);
        addFieldLine(cell, "ID: ", entryId);

        // Small spacer before name
        var spacer = new Paragraph(" ");
        spacer.setLeading(3f);
        cell.addElement(spacer);

        // Name (fixed 2-line height)
        addFixedHeightFieldLine(cell, "Name: ", entry.getMeadName());

        // Category
        var categoryCode = category != null ? category.getCode() : "—";
        addFieldLine(cell, "Category: ", categoryCode);

        // Sweetness | Strength | Carbonation (compact with field names)
        var charPhrase = new Phrase();
        charPhrase.add(new Phrase("Sweetness: ", fontCharLabel));
        charPhrase.add(new Phrase(entry.getSweetness().name().toLowerCase(), fontCharValue));
        charPhrase.add(new Phrase("  |  Strength: ", fontCharLabel));
        charPhrase.add(new Phrase(entry.getStrength().name().toLowerCase(), fontCharValue));
        charPhrase.add(new Phrase("  |  Carbonation: ", fontCharLabel));
        charPhrase.add(new Phrase(entry.getCarbonation().name().toLowerCase(), fontCharValue));
        var charParagraph = new Paragraph(charPhrase);
        cell.addElement(charParagraph);

        addSeparator(cell);

        // Ingredients (always show all three fields, fixed 2-line height each)
        addFixedHeightFieldLine(cell, "Honey: ", entry.getHoneyVarieties());
        var otherIngredients = (entry.getOtherIngredients() != null && !entry.getOtherIngredients().isBlank())
                ? entry.getOtherIngredients().replace("\n", ", ") : "";
        addFixedHeightFieldLine(cell, "Other: ", otherIngredients);
        var woodDetails = (entry.isWoodAged() && entry.getWoodAgeingDetails() != null)
                ? entry.getWoodAgeingDetails() : "";
        addFixedHeightFieldLine(cell, "Wood: ", woodDetails);

        addSeparator(cell);

        // QR code (left) + Official notes area (right)
        var qrContent = formatQrContent(entry, competition, division);
        addQrAndNotesRow(cell, qrContent);

        addSeparator(cell);

        // Disclaimer
        var disclaimer = new Paragraph("FREE SAMPLES. NOT FOR RESALE.", fontDisclaimer);
        disclaimer.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(disclaimer);

        return cell;
    }

    private void addParagraph(PdfPCell cell, String text, Font font, int alignment) {
        var p = new Paragraph(text, font);
        p.setAlignment(alignment);
        cell.addElement(p);
    }

    private void addFieldLine(PdfPCell cell, String label, String value) {
        var phrase = new Phrase();
        phrase.add(new Phrase(label, fontNormal));
        phrase.add(new Phrase(value != null ? value : "—", fontFieldValue));
        var p = new Paragraph(phrase);
        cell.addElement(p);
    }

    private void addFixedHeightFieldLine(PdfPCell parentCell, String label, String value) {
        var innerTable = new PdfPTable(1);
        innerTable.setWidthPercentage(100);
        var innerCell = new PdfPCell();
        innerCell.setBorder(0);
        innerCell.setPadding(0);
        innerCell.setFixedHeight(TWO_LINE_HEIGHT);
        innerCell.setNoWrap(false);
        var p = new Paragraph(10f, "", fontNormal);
        p.add(new Phrase(label, fontNormal));
        p.add(new Phrase(value != null ? value : "", fontFieldValue));
        innerCell.addElement(p);
        innerTable.addCell(innerCell);
        parentCell.addElement(innerTable);
    }

    private void addSeparator(PdfPCell cell) {
        var separatorTable = new PdfPTable(1);
        separatorTable.setWidthPercentage(100);
        separatorTable.setSpacingBefore(3);
        separatorTable.setSpacingAfter(3);
        var separatorCell = new PdfPCell();
        separatorCell.setBorderWidth(0);
        separatorCell.setBorderWidthBottom(0.5f);
        separatorCell.setBorderColorBottom(Color.LIGHT_GRAY);
        separatorCell.setFixedHeight(1);
        separatorTable.addCell(separatorCell);
        cell.addElement(separatorTable);
    }

    private void addQrAndNotesRow(PdfPCell parentCell, String qrContent) throws Exception {
        var qrImage = generateQrImage(qrContent);

        var rowTable = new PdfPTable(2);
        rowTable.setWidthPercentage(100);
        rowTable.setWidths(new float[]{45, 55});

        // Left: QR code
        var qrCell = new PdfPCell(qrImage, false);
        qrCell.setBorderWidth(0);
        qrCell.setBorderWidthRight(0.5f);
        qrCell.setBorderColorRight(Color.LIGHT_GRAY);
        qrCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrCell.setPaddingRight(8);
        rowTable.addCell(qrCell);

        // Right: notes area
        var notesCell = new PdfPCell();
        notesCell.setBorderWidth(0);
        notesCell.setVerticalAlignment(Element.ALIGN_TOP);
        notesCell.setPaddingLeft(8);
        var notesLabel = new Paragraph("For official notes. Leave blank.", fontSmall);
        notesLabel.setAlignment(Element.ALIGN_RIGHT);
        notesCell.addElement(notesLabel);
        notesCell.setMinimumHeight(QR_CODE_SIZE);
        rowTable.addCell(notesCell);

        parentCell.addElement(rowTable);
    }

    private Image generateQrImage(String content) throws WriterException, IOException {
        var qrCodeWriter = new QRCodeWriter();
        var bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);
        var binaryImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        // ZXing produces TYPE_BYTE_BINARY images which OpenPDF cannot embed — convert to RGB
        var rgbImage = new java.awt.image.BufferedImage(
                binaryImage.getWidth(), binaryImage.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = rgbImage.createGraphics();
        g.drawImage(binaryImage, 0, 0, null);
        g.dispose();

        var imageBytes = new ByteArrayOutputStream();
        ImageIO.write(rgbImage, "PNG", imageBytes);
        var image = Image.getInstance(imageBytes.toByteArray());
        image.scaleAbsolute(QR_CODE_SIZE, QR_CODE_SIZE);
        return image;
    }

    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

    String formatEntryId(Entry entry, Division division) {
        var prefix = division.getEntryPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix + "-" + entry.getEntryNumber();
        }
        return String.valueOf(entry.getEntryNumber());
    }

    String formatQrContent(Entry entry, Competition competition, Division division) {
        var entryId = formatEntryId(entry, division);
        return competition.getShortName() + "-" + entryId;
    }
}
