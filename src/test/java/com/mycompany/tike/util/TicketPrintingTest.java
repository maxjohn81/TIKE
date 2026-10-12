package com.mycompany.tike.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.mycompany.tike.model.Ticket;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class TicketPrintingTest {
    @Test
    void qrCodeContainsOnlyTheUniqueTicketCode() throws Exception {
        String code = UUID.randomUUID().toString();

        String decoded = new MultiFormatReader().decode(new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(TicketQrCode.create(code)))))
                .getText();

        assertEquals(code, decoded);
    }

    @Test
    void pdfContainsOnePrintablePagePerTicketAndTicketDetails() throws Exception {
        String firstCode = UUID.randomUUID().toString();
        String secondCode = UUID.randomUUID().toString();
        List<Ticket> tickets = List.of(
                new Ticket(1, "VT-TEST", "Concert de démonstration", LocalDateTime.of(2027, 6, 15, 19, 30),
                        "Salle TIKE — Antananarivo", "VIP", "Client Démo", new BigDecimal("25000.00"),
                        firstCode, "VALIDE"),
                new Ticket(2, "VT-TEST", "Concert de démonstration", LocalDateTime.of(2027, 6, 15, 19, 30),
                        "Salle TIKE — Antananarivo", "STANDARD", "Client Démo", new BigDecimal("12000.00"),
                        secondCode, "VALIDE"));
        Path pdf = Files.createTempFile("tike-tickets-", ".pdf");
        try {
            TicketPdfExporter.export(tickets, pdf);

            try (PDDocument document = PDDocument.load(pdf.toFile())) {
                assertEquals(2, document.getNumberOfPages());
                String content = new PDFTextStripper().getText(document);
                assertTrue(content.contains("Concert de démonstration"));
                assertTrue(content.contains("Salle TIKE"));
                assertTrue(content.contains(firstCode));
                assertTrue(content.contains(secondCode));
            }
        } finally {
            Files.deleteIfExists(pdf);
        }
    }
}
