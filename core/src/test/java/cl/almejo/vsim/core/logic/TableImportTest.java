package cl.almejo.vsim.core.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static cl.almejo.vsim.core.logic.LogicValue.DONT_CARE;
import static cl.almejo.vsim.core.logic.LogicValue.ONE;
import static cl.almejo.vsim.core.logic.LogicValue.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableImportTest {

    private static final String WORKBOOK = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="Tabla" sheetId="1" r:id="rId7"/></sheets>
            </workbook>
            """;
    private static final String RELATIONSHIPS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId7" Type="worksheet" Target="/xl/worksheets/tabla.xml"/>
            </Relationships>
            """;

    // --- readers ---

    @Test
    void readsACommaSeparatedFileWithQuotesAndCrlf() {
        ImportedGrid grid = TableImport.readCsv("﻿A,B,\"Salida, F\"\r\n0,0,\"1\"\r\n0,1,\"say \"\"x\"\"\"\r\n\r\n");

        assertThat(grid.header()).containsExactly("A", "B", "Salida, F");
        assertThat(grid.rows()).containsExactly(List.of("0", "0", "1"), List.of("0", "1", "say \"x\""));
        assertThat(grid.headerRow()).isEqualTo(1);
    }

    @Test
    void readsASemicolonSeparatedFile() {
        ImportedGrid grid = TableImport.readCsv("A;B;F\n0;1;0,5\n");

        assertThat(grid.header()).containsExactly("A", "B", "F");
        assertThat(grid.rows()).containsExactly(List.of("0", "1", "0,5"));
    }

    @Test
    void leadingBlankLinesMoveTheHeaderRow() {
        ImportedGrid grid = TableImport.readCsv("\n;;\nA;F\n1;0\n");

        assertThat(grid.header()).containsExactly("A", "F");
        assertThat(grid.headerRow()).isEqualTo(3);
    }

    @Test
    void readsTabSeparatedCellsCopiedFromASpreadsheet() {
        ImportedGrid grid = TableImport.readTabSeparated("A\tB\tF\n0\t0\t1\n1\t1\t\n");

        assertThat(grid.header()).containsExactly("A", "B", "F");
        assertThat(grid.rows()).containsExactly(List.of("0", "0", "1"), List.of("1", "1", ""));
    }

    @Test
    void readsTheCommittedXlsxWithSharedStringsNumbersBooleansAndEmptyCells() throws IOException {
        ImportedGrid grid;
        try (InputStream input = TableImportTest.class.getResourceAsStream("/logic/table-import.xlsx")) {
            grid = TableImport.readXlsx(input);
        }

        assertThat(grid.header()).containsExactly("A", "B", "F", "G");
        assertThat(grid.rows()).containsExactly(
                List.of("0", "0", "0", "TRUE"),
                List.of("0", "1", "1", "X"),
                List.of("1", "0", "1", "FALSE"),
                List.of("1", "1", "", "1"));

        ImportResult result = TableImport.toSpec(grid, 2, "Tabla");
        assertThat(result.problems()).isEmpty();
        TruthTableSpec spec = result.spec().orElseThrow();
        assertThat(spec.outputs()).containsExactly(
                List.of(ZERO, ONE, ONE, DONT_CARE),
                List.of(ONE, DONT_CARE, ZERO, ONE));
    }

    @Test
    void readsInlineStringsRichTextAndGapsFromTheFirstSheetOfTheWorkbook() throws IOException {
        String sharedStrings = """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><r><t>Ca</t></r><r><t>rry</t></r></si>
                </sst>
                """;
        String sheet = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                  <row r="2"><c r="B2" t="inlineStr"><is><t>A</t></is></c><c r="C2" t="s"><v>0</v></c></row>
                  <row r="4"><c r="B4"><v>1.0</v></c><c r="C4" t="str"><v>0</v></c></row>
                  <row r="5"><c r="B5" t="b"><v>0</v></c><c r="D5"><v>1</v></c></row>
                </sheetData></worksheet>
                """;
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("xl/workbook.xml", WORKBOOK);
        entries.put("xl/_rels/workbook.xml.rels", RELATIONSHIPS);
        entries.put("xl/sharedStrings.xml", sharedStrings);
        entries.put("xl/worksheets/sheet1.xml", "<worksheet><sheetData><row r=\"1\"><c><v>9</v></c></row></sheetData></worksheet>");
        entries.put("xl/worksheets/tabla.xml", sheet);

        ImportedGrid grid = TableImport.readXlsx(new ByteArrayInputStream(zip(entries)));

        assertThat(grid.headerRow()).isEqualTo(2);
        assertThat(grid.header()).containsExactly("", "A", "Carry");
        assertThat(grid.rows()).containsExactly(
                List.of(),
                List.of("", "1.0", "0"),
                List.of("", "FALSE", "", "1"));
    }

    @Test
    void anXlsxWithADoctypeIsRefusedWithoutReadingTheEntity(@TempDir Path directory) throws IOException {
        Path secret = directory.resolve("secret.txt");
        Files.writeString(secret, "do not read");
        String sheet = "<?xml version=\"1.0\"?>\n<!DOCTYPE worksheet [<!ENTITY secret SYSTEM \"" + secret.toUri()
                + "\">]>\n<worksheet><sheetData><row r=\"1\"><c t=\"inlineStr\"><is><t>&secret;</t></is></c></row>"
                + "</sheetData></worksheet>";
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("xl/worksheets/sheet1.xml", sheet);

        assertThatThrownBy(() -> TableImport.readXlsx(new ByteArrayInputStream(zip(entries))))
                .isInstanceOfSatisfying(TableImportException.class, exception ->
                        assertThat(exception.problem().messageKey()).isEqualTo(ImportProblem.NOT_A_SPREADSHEET));
    }

    @Test
    void somethingThatIsNotAnXlsxIsAClearProblem() {
        assertThatThrownBy(() -> TableImport.readXlsx(new ByteArrayInputStream("A,B\n".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(TableImportException.class, exception ->
                        assertThat(exception.problem().messageKey()).isEqualTo(ImportProblem.NOT_A_SPREADSHEET));
    }

    @Test
    void anXlsxRowOrColumnBeyondExcelsLimitsIsNotASpreadsheet() {
        for (String row : List.of(
                "<row r=\"2000000000\"><c><v>1</v></c></row>",
                "<row r=\"0\"><c><v>1</v></c></row>",
                "<row r=\"1\"><c r=\"ZZZZZZ1\"><v>1</v></c></row>")) {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("xl/worksheets/sheet1.xml", "<worksheet><sheetData>" + row + "</sheetData></worksheet>");
            assertThatThrownBy(() -> TableImport.readXlsx(new ByteArrayInputStream(zip(entries))))
                    .as(row)
                    .isInstanceOfSatisfying(TableImportException.class, exception ->
                            assertThat(exception.problem().messageKey()).isEqualTo(ImportProblem.NOT_A_SPREADSHEET));
        }
    }

    @Test
    void aFileLargerThanTheLimitIsRefusedBeforeReadingIt() throws TableImportException {
        TableImport.checkSize(TableImport.MAXIMUM_FILE_MEGABYTES * 1024L * 1024L);
        assertThatThrownBy(() -> TableImport.checkSize(TableImport.MAXIMUM_FILE_MEGABYTES * 1024L * 1024L + 1))
                .isInstanceOfSatisfying(TableImportException.class, exception ->
                        assertThat(exception.problem().messageKey()).isEqualTo(ImportProblem.TOO_LARGE));
    }

    @Test
    void theKeptColumnsLeaveOutTheOnesWithNoNameAndNoValue() {
        ImportedGrid grid = TableImport.readCsv("A,,B,F,\n0,,0,1\n1,,1,0\n");
        assertThat(TableImport.columns(grid)).containsExactly(0, 2, 3);
        assertThat(TableImport.columns(TableImport.readCsv("A,,F\n0,1,1\n"))).containsExactly(0, 1, 2);
    }

    // --- toSpec ---

    @Test
    void cellsAcceptEveryWrittenFormOfZeroOneAndX() {
        ImportedGrid grid = TableImport.readCsv("""
                A,B,F,G,H
                0,0,0,1,X
                0,1,FALSE,TRUE,x
                1,0,falso,verdadero,-
                1.0,1,0.0,1.0,
                """);

        ImportResult result = TableImport.toSpec(grid, 2, "T");

        assertThat(result.problems()).isEmpty();
        assertThat(result.missingRows()).isZero();
        TruthTableSpec spec = result.spec().orElseThrow();
        assertThat(spec.title()).isEqualTo("T");
        assertThat(spec.inputNames()).containsExactly("A", "B");
        assertThat(spec.outputNames()).containsExactly("F", "G", "H");
        assertThat(spec.outputs()).containsExactly(
                List.of(ZERO, ZERO, ZERO, ZERO),
                List.of(ONE, ONE, ONE, ONE),
                List.of(DONT_CARE, DONT_CARE, DONT_CARE, DONT_CARE));
    }

    @Test
    void aDecimalCommaOrPointAfterZeroOrOneIsTheSameValue() {
        assertThat(TableImport.valueOf("1,0")).isEqualTo(ONE);
        assertThat(TableImport.valueOf("0,0")).isEqualTo(ZERO);
        assertThat(TableImport.valueOf("1.0")).isEqualTo(ONE);
        assertThat(TableImport.valueOf("0,000")).isEqualTo(ZERO);
        assertThat(TableImport.valueOf(" 1, ")).as("Excel's «1,» with no decimals shown").isEqualTo(ONE);
        assertThat(TableImport.valueOf("1,5")).isNull();
        assertThat(TableImport.valueOf("2,0")).isNull();
        assertThat(TableImport.valueOf("1,0,0")).isNull();
    }

    @Test
    void aSemicolonFileFromASpanishExcelReadsDecimalCommasAsZeroAndOne() {
        ImportedGrid grid = TableImport.readCsv("A;B;F\n0,0;0,0;1,0\n0,0;1,0;0,0\n1,0;0,0;1.0\n1,0;1,0;0\n");

        ImportResult result = TableImport.toSpec(grid, 2, "T");

        assertThat(result.problems()).isEmpty();
        assertThat(result.spec().orElseThrow().outputs()).containsExactly(List.of(ONE, ZERO, ONE, ZERO));
    }

    @Test
    void tabSeparatedCellsPastedFromASpanishExcelReadDecimalCommas() {
        ImportedGrid grid = TableImport.readTabSeparated("A\tB\tF\n0\t0\t0,0\n0\t1\t1,0\n1,0\t0\t1,0\n1,0\t1,0\t0,0\n");

        ImportResult result = TableImport.toSpec(grid, 2, "T");

        assertThat(result.problems()).isEmpty();
        assertThat(result.spec().orElseThrow().outputs()).containsExactly(List.of(ZERO, ONE, ONE, ZERO));
    }

    @Test
    void inACommaSeparatedFileADecimalCommaSplitsTheCellInTwo() {
        // Documented: with commas as the separator «1,0» cannot be one cell (unless it is quoted), so the row has one
        // cell too many. Excel uses the semicolon whenever the decimal separator is the comma.
        ImportedGrid grid = TableImport.readCsv("A,B,F\n0,0,1,0\n0,1,\"1,0\"\n");

        assertThat(grid.rows().getFirst()).containsExactly("0", "0", "1", "0");
        assertThat(TableImport.toSpec(grid, 2, "T").problems())
                .containsExactly(new ImportProblem(ImportProblem.WRONG_CELL_COUNT, List.of(4, 3), 2, 0));
    }

    @Test
    void rowsInAnyOrderAndMissingCombinationsBecomeXAndAreCounted() {
        ImportedGrid grid = TableImport.readCsv("A,B,C,F\n1,1,1,1\n0,0,0,0\n1,1,1,1\n");

        ImportResult result = TableImport.toSpec(grid, 3, "T");

        assertThat(result.problems()).isEmpty();
        assertThat(result.missingRows()).isEqualTo(6);
        assertThat(result.spec().orElseThrow().outputs().get(0))
                .containsExactly(ZERO, DONT_CARE, DONT_CARE, DONT_CARE, DONT_CARE, DONT_CARE, DONT_CARE, ONE);
    }

    @Test
    void columnsAndRowsThatAreCompletelyEmptyAreIgnoredButKeepTheirNumbers() {
        ImportedGrid grid = TableImport.readCsv(";A;;F;\n;0;;1;\n;;;;\n;1;;Q;\n");

        ImportResult result = TableImport.toSpec(grid, 1, "T");

        assertThat(result.problems())
                .containsExactly(new ImportProblem(ImportProblem.INVALID_VALUE, List.of("Q"), 4, 4));
    }

    @Test
    void aRepeatedCombinationWithDifferentOutputsNamesBothRows() {
        ImportedGrid grid = TableImport.readCsv("A,B,F,G\n0,1,1,0\n0,1,1,0\n0,1,1,1\n");

        ImportResult result = TableImport.toSpec(grid, 2, "T");

        assertThat(result.spec()).isEmpty();
        assertThat(result.problems()).containsExactly(new ImportProblem(ImportProblem.CONFLICTING_ROW,
                List.of("0 1", List.of("G"), 1, 2, 4), 0, 0));
    }

    @Test
    void aRepeatedCombinationNamesEveryOutputThatDiffers() {
        ImportedGrid grid = TableImport.readCsv("A,B,C,F,G,H\n0,1,1,0,1,0\n1,1,1,0,0,0\n0,1,1,1,1,1\n");

        ImportResult result = TableImport.toSpec(grid, 3, "T");

        assertThat(result.problems()).containsExactly(new ImportProblem(ImportProblem.CONFLICTING_ROW,
                List.of("0 1 1", List.of("F", "H"), 2, 2, 4), 0, 0));
    }

    @Test
    void badCellsAreProblemsNamingRowAndColumn() {
        ImportedGrid grid = TableImport.readCsv("A,B,F\n0,X,1\n1,,0\n1,1,2\n0,0,1,1\n0,0,1,,\n");

        ImportResult result = TableImport.toSpec(grid, 2, "T");

        assertThat(result.spec()).isEmpty();
        assertThat(result.problems()).containsExactly(
                new ImportProblem(ImportProblem.INPUT_NOT_BINARY, List.of("X"), 2, 2),
                new ImportProblem(ImportProblem.EMPTY_INPUT, List.of(), 3, 2),
                new ImportProblem(ImportProblem.INVALID_VALUE, List.of("2"), 4, 3),
                new ImportProblem(ImportProblem.WRONG_CELL_COUNT, List.of(4, 3), 5, 0));
    }

    @Test
    void namesMustBePresentAndDistinct() {
        ImportedGrid grid = TableImport.readCsv("A, ,a,F\n0,0,0,1\n");

        ImportResult result = TableImport.toSpec(grid, 3, "T");

        assertThat(result.problems()).containsExactly(
                new ImportProblem(ImportProblem.BLANK_NAME, List.of(), 1, 2),
                new ImportProblem(ImportProblem.REPEATED_NAME, List.of("a"), 1, 3));
    }

    @Test
    void namesAreStrippedButOtherwiseKeptAsWritten() {
        ImportedGrid grid = TableImport.readCsv(" Entrada 1 ,Carry Out\n0,1\n1,0\n");

        TruthTableSpec spec = TableImport.toSpec(grid, 1, "T").spec().orElseThrow();

        assertThat(spec.inputNames()).containsExactly("Entrada 1");
        assertThat(spec.outputNames()).containsExactly("Carry Out");
    }

    @Test
    void theInputAndOutputCountsHaveLimits() {
        String header = "I1,I2,I3,I4,I5,I6,I7,I8,I9,F\n";
        ImportedGrid nineInputs = TableImport.readCsv(header + "0,0,0,0,0,0,0,0,0,1\n");

        assertThat(TableImport.toSpec(nineInputs, 9, "T").problems())
                .containsExactly(new ImportProblem(ImportProblem.TOO_MANY_INPUTS, List.of(9, 8), 0, 0));
        assertThat(TableImport.toSpec(nineInputs, 0, "T").problems())
                .containsExactly(new ImportProblem(ImportProblem.NO_INPUTS, List.of(), 0, 0),
                        new ImportProblem(ImportProblem.TOO_MANY_OUTPUTS, List.of(10, 8), 0, 0));
        assertThat(TableImport.toSpec(nineInputs, 10, "T").problems())
                .containsExactly(new ImportProblem(ImportProblem.TOO_MANY_INPUTS, List.of(10, 8), 0, 0),
                        new ImportProblem(ImportProblem.NO_OUTPUTS, List.of(), 0, 0));
        assertThat(TableImport.toSpec(nineInputs, 1, "T").problems())
                .containsExactly(new ImportProblem(ImportProblem.TOO_MANY_OUTPUTS, List.of(9, 8), 0, 0));
    }

    @Test
    void emptyAndHeaderOnlyFilesAreProblems() {
        assertThat(TableImport.toSpec(TableImport.readCsv(""), 1, "T").problems())
                .containsExactly(new ImportProblem(ImportProblem.EMPTY_FILE, List.of(), 0, 0));
        assertThat(TableImport.toSpec(TableImport.readCsv("\n , \n"), 1, "T").problems())
                .containsExactly(new ImportProblem(ImportProblem.EMPTY_FILE, List.of(), 0, 0));
        assertThat(TableImport.toSpec(TableImport.readCsv("A,F\n"), 1, "T").problems())
                .containsExactly(new ImportProblem(ImportProblem.NO_ROWS, List.of(), 0, 0));
    }

    @Test
    void anEightByEightTableIsAccepted() {
        StringBuilder text = new StringBuilder("A,B,C,D,E,G,H,I,O1,O2,O3,O4,O5,O6,O7,O8\n");
        for (int row = 0; row < 256; row++) {
            StringBuilder line = new StringBuilder();
            for (int bit = 7; bit >= 0; bit--) {
                line.append(row >> bit & 1).append(',');
            }
            for (int output = 0; output < 8; output++) {
                line.append(Integer.bitCount(row) % 2 == output % 2 ? '1' : '0').append(output < 7 ? "," : "");
            }
            text.append(line).append('\n');
        }

        ImportResult result = TableImport.toSpec(TableImport.readCsv(text.toString()), 8, "T");

        assertThat(result.problems()).isEmpty();
        assertThat(result.spec().orElseThrow().value(1, 3)).isEqualTo(ZERO);
        assertThat(result.spec().orElseThrow().value(0, 3)).isEqualTo(ONE);
    }

    @Test
    void problemsNameTheirCellLikeASpreadsheet() {
        assertThat(new ImportProblem(ImportProblem.INVALID_VALUE, List.of("Q"), 4, 4).cellName()).isEqualTo("D4");
        assertThat(new ImportProblem(ImportProblem.WRONG_CELL_COUNT, List.of(4, 3), 5, 0).cellName()).isEqualTo("5");
        assertThat(ImportProblem.general(ImportProblem.NO_ROWS).cellName()).isEmpty();
        assertThat(ImportProblem.columnLetters(26)).isEqualTo("Z");
        assertThat(ImportProblem.columnLetters(27)).isEqualTo("AA");
        assertThat(ImportProblem.columnLetters(703)).isEqualTo("AAA");
    }

    // --- proposed input count ---

    @Test
    void theProposedInputCountIsTheNamesBeforeTheFirstOutputLookingOneElseHalf() {
        assertThat(TableImport.proposedInputCount(List.of("A", "B", "C", "F"))).isEqualTo(3);
        assertThat(TableImport.proposedInputCount(List.of("A", "B", "C", "D", "F", "G"))).isEqualTo(4);
        assertThat(TableImport.proposedInputCount(List.of("X1", "X2", "X3", "Y"))).isEqualTo(3);
        assertThat(TableImport.proposedInputCount(List.of("a0", "a1", "b0", "b1", "s0", "s1"))).isEqualTo(4);
        assertThat(TableImport.proposedInputCount(List.of("A", "B"))).isEqualTo(1);
        assertThat(TableImport.proposedInputCount(List.of("Entrada", "Otra", "Salida", "Fin"))).isEqualTo(2);
        assertThat(TableImport.proposedInputCount(List.of("Uno", "Dos", "Tres"))).isEqualTo(1);
        assertThat(TableImport.proposedInputCount(List.of("Cin", "A", "B", "Cout", "Suma"))).isEqualTo(3);
        assertThat(TableImport.proposedInputCount(List.of("F"))).isEqualTo(1);
        assertThat(TableImport.proposedInputCount(List.of())).isEqualTo(1);
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
