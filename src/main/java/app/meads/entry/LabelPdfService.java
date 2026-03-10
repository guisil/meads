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
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class LabelPdfService {

    private static final Font FONT_NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font FONT_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final Font FONT_HEADER = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONT_COMPETITION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font FONT_DIVISION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font FONT_FIELD_VALUE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final Font FONT_SMALL = FontFactory.getFont(FontFactory.HELVETICA, 7);
    private static final Font FONT_SMALL_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
    private static final Font FONT_DISCLAIMER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final int QR_CODE_SIZE = 80;

    public byte[] generateLabel(Entry entry, Competition competition,
                                 Division division, DivisionCategory category) {
        return generateLabels(List.of(entry), competition, division, id -> category);
    }

    public byte[] generateLabels(List<Entry> entries, Competition competition,
                                  Division division,
                                  Function<UUID, DivisionCategory> categoryResolver) {
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
                addPage(document, entry, competition, division, category);
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
                          Division division, DivisionCategory category) throws Exception {
        // Instruction header
        addInstructionHeader(document, competition);

        document.add(new Paragraph(" ", FONT_SMALL)); // spacer

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

    private void addInstructionHeader(Document document, Competition competition) throws Exception {
        var sb = new StringBuilder();
        sb.append("Print the labels and cut along the lines. ");
        sb.append("Attach one label to each bottle using elastic bands (do not use sticky tape).");

        if (competition.getShippingAddress() != null && !competition.getShippingAddress().isBlank()) {
            sb.append(" Post your bottles to: ");
            sb.append(competition.getShippingAddress().replace("\n", ", "));
            if (competition.getPhoneNumber() != null && !competition.getPhoneNumber().isBlank()) {
                sb.append(", Tel. ").append(competition.getPhoneNumber());
            }
        } else if (competition.getPhoneNumber() != null && !competition.getPhoneNumber().isBlank()) {
            sb.append(" Tel. ").append(competition.getPhoneNumber());
        }

        var paragraph = new Paragraph(sb.toString(), FONT_HEADER);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        document.add(paragraph);
    }

    private PdfPCell createLabelCell(Entry entry, Competition competition,
                                      Division division, DivisionCategory category) throws Exception {
        var cell = new PdfPCell();
        cell.setBorderColor(Color.GRAY);
        cell.setBorderWidth(0.5f);
        cell.setPadding(8);
        cell.setVerticalAlignment(Element.ALIGN_TOP);

        // Competition name (bold)
        addParagraph(cell, competition.getName(), FONT_COMPETITION, Element.ALIGN_CENTER);

        // Division name (large)
        addParagraph(cell, division.getName(), FONT_DIVISION, Element.ALIGN_CENTER);

        addSeparator(cell);

        // ID
        var entryId = formatEntryId(entry, division);
        addFieldLine(cell, "ID: ", entryId);

        // Name
        addFieldLine(cell, "Name: ", entry.getMeadName());

        // Category
        var categoryCode = category != null ? category.getCode() : "—";
        addFieldLine(cell, "Category: ", categoryCode);

        // Sweetness | Strength | Carbonation
        var characteristics = entry.getSweetness().name().toLowerCase()
                + " | " + entry.getStrength().name().toLowerCase()
                + " | " + entry.getCarbonation().name().toLowerCase();
        addParagraph(cell, characteristics, FONT_NORMAL, Element.ALIGN_LEFT);

        addSeparator(cell);

        // Ingredients
        addFieldLine(cell, "Honey: ", entry.getHoneyVarieties());
        if (entry.getOtherIngredients() != null && !entry.getOtherIngredients().isBlank()) {
            addFieldLine(cell, "Other: ", entry.getOtherIngredients().replace("\n", ", "));
        }
        if (entry.isWoodAged() && entry.getWoodAgeingDetails() != null) {
            addFieldLine(cell, "Wood: ", entry.getWoodAgeingDetails());
        }

        addSeparator(cell);

        // QR code
        var qrContent = formatQrContent(entry, competition, division);
        addQrCode(cell, qrContent);

        addSeparator(cell);

        // Official notes area
        var notesLabel = new Paragraph("For official notes. Leave blank.", FONT_SMALL);
        notesLabel.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(notesLabel);
        // Empty space for notes
        addParagraph(cell, " \n \n", FONT_SMALL, Element.ALIGN_LEFT);

        addSeparator(cell);

        // Disclaimer
        var disclaimer = new Paragraph("FREE SAMPLES. NOT FOR RE-SALE.", FONT_DISCLAIMER);
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
        phrase.add(new Phrase(label, FONT_NORMAL));
        phrase.add(new Phrase(value != null ? value : "—", FONT_FIELD_VALUE));
        var p = new Paragraph(phrase);
        cell.addElement(p);
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

    private void addQrCode(PdfPCell cell, String content) throws WriterException, IOException {
        var qrCodeWriter = new QRCodeWriter();
        var bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);
        var bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        var imageBytes = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "PNG", imageBytes);
        var image = Image.getInstance(imageBytes.toByteArray());
        image.setAlignment(Element.ALIGN_CENTER);
        image.scaleAbsolute(QR_CODE_SIZE, QR_CODE_SIZE);

        var qrParagraph = new Paragraph();
        qrParagraph.setAlignment(Element.ALIGN_CENTER);
        qrParagraph.add(image);
        cell.addElement(qrParagraph);
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
