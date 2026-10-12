package com.mycompany.tike.util;

import com.mycompany.tike.model.Ticket;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public final class TicketPdfExporter {
    private static final float PAGE_WIDTH = 340;
    private static final float PAGE_HEIGHT = 490;
    private static final DateTimeFormatter EVENT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
    private static final Color PURPLE = new Color(91, 54, 153);
    private static final Color LIGHT_PURPLE = new Color(242, 237, 250);
    private static final Color TEXT = new Color(47, 42, 56);
    private static final Color MUTED = new Color(105, 99, 113);
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private TicketPdfExporter() {
    }

    public static void export(List<Ticket> tickets, Path destination) throws IOException {
        if (tickets == null || tickets.isEmpty()) {
            throw new IllegalArgumentException("Aucun billet à exporter.");
        }
        if (destination == null) {
            throw new IllegalArgumentException("Choisissez un fichier PDF de destination.");
        }
        try (PDDocument document = new PDDocument()) {
            for (Ticket ticket : tickets) {
                addTicketPage(document, ticket);
            }
            document.save(destination.toFile());
        }
    }

    private static void addTicketPage(PDDocument document, Ticket ticket) throws IOException {
        PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.setNonStrokingColor(Color.WHITE);
            content.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            content.fill();
            content.setStrokingColor(LIGHT_PURPLE);
            content.setLineWidth(1.2f);
            content.addRect(14, 14, PAGE_WIDTH - 28, PAGE_HEIGHT - 28);
            content.stroke();

            content.setNonStrokingColor(PURPLE);
            content.addRect(14, PAGE_HEIGHT - 86, PAGE_WIDTH - 28, 72);
            content.fill();
            text(content, PDType1Font.HELVETICA_BOLD, 10, Color.WHITE, 28, PAGE_HEIGHT - 35, "TIKE  |  BILLET D'ENTREE");
            drawWrapped(content, ticket.eventName(), PDType1Font.HELVETICA_BOLD, 16,
                    Color.WHITE, 28, PAGE_HEIGHT - 60, PAGE_WIDTH - 56, 2, 19);

            drawLabelValue(content, "DATE", eventDate(ticket), 386);
            drawLabelValue(content, "LIEU", ticket.location(), 342);
            drawLabelValue(content, "CATEGORIE", ticket.categoryName(), 298);
            drawLabelValue(content, "TITULAIRE", ticket.customerName(), 254);
            drawLabelValue(content, "PRIX", ticket.price().toPlainString() + " Ar", 210);
            drawLabelValue(content, "STATUT", statusLabel(ticket.status()), 172);

            PDImageXObject image = LosslessFactory.createFromImage(
                    document, TicketQrCode.create(ticket.uniqueCode()));
            float qrSize = 100;
            float qrX = (PAGE_WIDTH - qrSize) / 2;
            content.drawImage(image, qrX, 48, qrSize, qrSize);
            textCentered(content, PDType1Font.HELVETICA_BOLD, 9, TEXT, ticket.uniqueCode(), PAGE_WIDTH / 2, 37);
            textCentered(content, PDType1Font.HELVETICA, 8, MUTED,
                    "Presentez ce code a l'entree", PAGE_WIDTH / 2, 22);
        } catch (com.google.zxing.WriterException error) {
            throw new IOException("Impossible de générer le QR Code du billet " + ticket.uniqueCode() + ".", error);
        }
    }

    private static void drawLabelValue(PDPageContentStream content, String label, String value, float y)
            throws IOException {
        text(content, PDType1Font.HELVETICA_BOLD, 8, MUTED, 28, y, label);
        drawWrapped(content, value, PDType1Font.HELVETICA_BOLD, 11,
                TEXT, 28, y - 15, PAGE_WIDTH - 56, 2, 13);
    }

    private static void drawWrapped(
            PDPageContentStream content, String value, PDType1Font font, float size, Color color,
            float x, float y, float maxWidth, int maxLines, float lineHeight) throws IOException {
        String safe = pdfCompatible(value == null ? "" : value);
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : safe.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getStringWidth(candidate) * size / 1000 > maxWidth) {
                text(content, font, size, color, x, y - lines * lineHeight, line.toString());
                lines++;
                if (lines == maxLines) {
                    return;
                }
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty() && lines < maxLines) {
            text(content, font, size, color, x, y - lines * lineHeight, line.toString());
        }
    }

    private static void textCentered(
            PDPageContentStream content, PDType1Font font, float size, Color color, String value, float centerX,
            float y) throws IOException {
        String safe = pdfCompatible(value);
        float width = font.getStringWidth(safe) * size / 1000;
        text(content, font, size, color, centerX - width / 2, y, safe);
    }

    private static void text(
            PDPageContentStream content, PDType1Font font, float size, Color color, float x, float y, String value)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.setNonStrokingColor(color);
        content.newLineAtOffset(x, y);
        content.showText(pdfCompatible(value));
        content.endText();
    }

    private static String pdfCompatible(String value) {
        StringBuilder result = new StringBuilder(value.length());
        CharsetEncoder encoder = WIN_ANSI.newEncoder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            result.append(encoder.canEncode(character) ? character : "?");
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static String eventDate(Ticket ticket) {
        return ticket.eventStartsAt() == null ? "Date non renseignee" : EVENT_DATE.format(ticket.eventStartsAt());
    }

    private static String statusLabel(String ticketStatus) {
        return switch (ticketStatus) {
            case "VALIDE" -> "Valide";
            case "UTILISE" -> "Deja utilise";
            case "ANNULE" -> "Annule";
            case "REMBOURSE" -> "Rembourse";
            default -> ticketStatus;
        };
    }
}
