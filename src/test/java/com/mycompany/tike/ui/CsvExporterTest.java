package com.mycompany.tike.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvExporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsUtf8CsvWithEscapingAndSpreadsheetFormulaProtection() throws Exception {
        Path output = temporaryDirectory.resolve("rapport.csv");

        CsvExporter.write(output, List.of("Nom", "Détail"), List.of(
                List.of("=1+1", "Texte, avec virgule"),
                List.of("  =SUM(A1:A2)", "Valeur"),
                List.of("Client", "Il a dit \"bonjour\"\nSuite")));

        String csv = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("\"'=1+1\",\"Texte, avec virgule\"\r\n"));
        assertTrue(csv.contains("\"'  =SUM(A1:A2)\",\"Valeur\""));
        assertTrue(csv.contains("\"Il a dit \"\"bonjour\"\"\nSuite\""));
    }
}
