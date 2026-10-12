package com.mycompany.tike.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class CsvExporter {
    private CsvExporter() {
    }

    static void write(Path path, List<String> headers, List<List<String>> rows) throws IOException {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendRow(csv, headers);
        for (List<String> row : rows) {
            appendRow(csv, row);
        }
        Files.writeString(path, csv.toString(), StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            String value = values.get(index) == null ? "" : values.get(index);
            int firstMeaningful = 0;
            while (firstMeaningful < value.length()
                    && (Character.isWhitespace(value.charAt(firstMeaningful))
                            || Character.isISOControl(value.charAt(firstMeaningful)))) {
                firstMeaningful++;
            }
            if ((firstMeaningful < value.length()
                    && "=+-@".indexOf(value.charAt(firstMeaningful)) >= 0)
                    || (!value.isEmpty() && Character.isISOControl(value.charAt(0)))) {
                value = "'" + value;
            }
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }
}
