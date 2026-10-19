package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TableImportPanelTest {

    private static final String ADDER = """
            Cin,A,B,Cout,Suma
            0,0,0,0,0
            0,0,1,0,1
            0,1,0,0,1
            0,1,1,1,0
            1,0,0,0,1
            1,0,1,1,0
            1,1,0,1,0
            1,1,1,1,1
            """;

    @TempDir
    Path directory;
    private final FakeCreationTarget target = new FakeCreationTarget();
    private final List<Runnable> queued = new ArrayList<>();
    private Path chosen;
    private Optional<String> clipboard = Optional.empty();
    private int chooserCalls;
    private TableImportPanel panel;
    private int closed;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        open(Background.inline());
    }

    private void open(Background background) {
        panel = new TableImportPanel(new TableSources(parent -> {
            chooserCalls++;
            return Optional.ofNullable(chosen);
        }, () -> clipboard), target, background);
        panel.setOnClose(() -> closed++);
    }

    private Path write(String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    void aCsvFileBecomesATableWithTheProposedInputsAndItsCircuitIsCreated() throws IOException {
        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();

        assertThat(chooserCalls).isEqualTo(1);
        assertThat(panel.titleField().getText()).isEqualTo("sumador");
        assertThat(panel.inputCountSpinner().getValue()).isEqualTo(3);
        assertThat(panel.columnsText()).isEqualTo("Entradas: Cin, A, B · Salidas: Cout, Suma");
        assertThat(panel.problemsText()).isEmpty();
        assertThat(panel.noticeText()).isEmpty();
        assertThat(panel.summaryText()).startsWith("3 entradas, 2 salidas → ").endsWith("compuertas");
        assertThat(panel.controls().createButton().isEnabled()).isTrue();

        panel.controls().createButton().doClick();

        assertThat(target.newCircuits).singleElement().satisfies(created -> {
            assertThat(created.name()).isEqualTo("sumador");
            assertThat(TableCircuits.check(panel.model().spec().orElseThrow(), created.fragment(),
                    StandardComponentTypes.registry()).passed()).isTrue();
        });
        assertThat(panel.controls().resultText()).isEqualTo("✓ El circuito cumple la tabla.");
    }

    @Test
    void pastedCellsAreTabSeparatedAndTheTitleIsTabla() {
        clipboard = Optional.of("A\tB\tY\r\n0\t0\t0\r\n0\t1\t1\r\n1\t0\t1\r\n1\t1\t0\r\n");
        panel.pasteButton().doClick();

        assertThat(panel.titleField().getText()).isEqualTo("Tabla");
        assertThat(panel.columnsText()).isEqualTo("Entradas: A, B · Salidas: Y");
        assertThat(panel.summaryText()).isEqualTo("2 entradas, 1 salida → 2 términos, 5 compuertas");
    }

    @Test
    void anEmptyClipboardSaysWhatToCopy() {
        panel.pasteButton().doClick();
        assertThat(panel.problemsText()).startsWith("El portapapeles no tiene texto");
        assertThat(panel.controls().createButton().isEnabled()).isFalse();
    }

    @Test
    void anXlsxWorkbookIsReadFromItsFirstSheet() throws IOException {
        chosen = directory.resolve("tabla.xlsx");
        try (OutputStream output = Files.newOutputStream(chosen);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet(List.of(List.of("A", "B", "F"), List.of("0", "0", "1"), List.of("0", "1", "0"),
                    List.of("1", "0", "1"), List.of("1", "1", "1"))).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        panel.openFileButton().doClick();

        assertThat(panel.problemsText()).isEmpty();
        assertThat(panel.columnsText()).isEqualTo("Entradas: A, B · Salidas: F");
        assertThat(panel.titleField().getText()).isEqualTo("tabla");
        assertThat(panel.model().spec()).isPresent();
    }

    @Test
    void aFileThatIsNotAWorkbookIsAProblemNotAnException() throws IOException {
        chosen = write("roto.xlsx", "esto no es un zip");
        panel.openFileButton().doClick();
        assertThat(panel.problemsText()).isEqualTo("No se pudo leer como planilla de Excel (.xlsx).");
        assertThat(panel.controls().createButton().isEnabled()).isFalse();
    }

    @Test
    void problemsAreListedByCellAndCreateIsOff() throws IOException {
        chosen = write("mala.csv", """
                A,B,Y
                0,0,0
                0,2,1
                1,0,7
                1,1,0,1
                """);
        panel.openFileButton().doClick();

        assertThat(panel.problemsText()).isEqualTo(String.join("\n",
                "Celda B3: «2» no sirve como entrada: debe ser 0 o 1.",
                "Celda C4: «7» no es un valor: usa 0, 1 o X.",
                "Fila 5: La fila tiene 4 celdas, pero la tabla tiene 3 columnas."));
        assertThat(panel.controls().createButton().isEnabled()).isFalse();
        assertThat(panel.summaryText()).isBlank();
    }

    @Test
    void missingCombinationsAreTakenAsXWithANotice() throws IOException {
        chosen = write("incompleta.csv", "A;B;C;Y\n0;0;0;1\n1;1;1;0\n0;1;0;1\n1;0;1;0\n");
        panel.openFileButton().doClick();
        assertThat(panel.noticeText()).isEqualTo("Faltaban 4 combinaciones: se tomaron como X.");
        assertThat(panel.controls().createButton().isEnabled()).isTrue();
    }

    @Test
    void choosingHowManyColumnsAreInputsChangesTheTable() throws IOException {
        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();
        panel.inputCountSpinner().setValue(4);
        assertThat(panel.columnsText()).isEqualTo("Entradas: Cin, A, B, Cout · Salidas: Suma");
        assertThat(panel.model().spec().orElseThrow().outputNames()).containsExactly("Suma");
        assertThat(panel.noticeText()).isEqualTo("Faltaban 8 combinaciones: se tomaron como X.");

        panel.inputCountSpinner().setValue(2);
        assertThat(panel.columnsText()).isEqualTo("Entradas: Cin, A · Salidas: B, Cout, Suma");
        assertThat(panel.model().spec()).isEmpty();
        assertThat(panel.problemsText()).startsWith("Celda C3: Esta combinación ya estaba en la fila 2");
    }

    @Test
    void aBlankTitleTurnsCreateOff() throws IOException {
        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();
        panel.titleField().setText(" ");
        assertThat(panel.problemsText()).isEqualTo("Escribe un título para la tabla.");
        assertThat(panel.controls().createButton().isEnabled()).isFalse();
    }

    @Test
    void anEightByEightTableIsSummarizedAndBuilt() throws IOException {
        StringBuilder csv = new StringBuilder("A,B,C,D,E,F,G,H,P,Q,R,S,T,U,V,W\n");
        for (int row = 0; row < 256; row++) {
            List<String> cells = new ArrayList<>();
            for (int bit = 7; bit >= 0; bit--) {
                cells.add(String.valueOf(row >> bit & 1));
            }
            for (int output = 0; output < 8; output++) {
                int value = Integer.bitCount(row & (0x81 << output % 4 | output)) % 2;
                cells.add(output == 7 && row % 5 == 0 ? "X" : String.valueOf(value));
            }
            csv.append(String.join(",", cells)).append('\n');
        }
        chosen = write("grande.csv", csv.toString());
        panel.openFileButton().doClick();
        panel.inputCountSpinner().setValue(8);

        assertThat(panel.problemsText()).isEmpty();
        assertThat(panel.summaryText()).matches("8 entradas, 8 salidas → \\d+ términos, \\d+ compuertas");

        panel.controls().createButton().doClick();
        assertThat(target.newCircuits).hasSize(1);
        assertThat(panel.controls().resultText()).isEqualTo("✓ El circuito cumple la tabla.");
    }

    @Test
    void whileReadingOrCreatingTheButtonsAreOff() throws IOException {
        open(new Background(queued::add, Runnable::run));
        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();
        assertThat(panel.openFileButton().isEnabled()).isFalse();
        assertThat(panel.pasteButton().isEnabled()).isFalse();
        assertThat(panel.closeButton().isEnabled()).isFalse();
        queued.removeFirst().run(); // the file
        assertThat(panel.openFileButton().isEnabled()).isTrue();
        assertThat(panel.summaryText()).isEqualTo("Calculando…");
        queued.removeFirst().run(); // the summary
        assertThat(panel.summaryText()).startsWith("3 entradas");

        panel.controls().createButton().doClick();
        assertThat(panel.controls().busyIndicatorShown()).isTrue();
        assertThat(panel.openFileButton().isEnabled()).isFalse();
        assertThat(panel.gateButton(WizardOptions.GateChoice.NAND_ONLY).isEnabled()).isFalse();
        assertThat(panel.inputCountSpinner().isEnabled()).isFalse();
        assertThat(target.newCircuits).isEmpty();
        queued.removeFirst().run();
        assertThat(target.newCircuits).hasSize(1);
        assertThat(panel.openFileButton().isEnabled()).isTrue();
        assertThat(panel.closeButton().isEnabled()).isTrue();
    }

    @Test
    void aSummaryThatArrivesLateIsDropped() throws IOException {
        open(new Background(queued::add, Runnable::run));
        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();
        queued.removeFirst().run();
        panel.gateButton(WizardOptions.GateChoice.NAND_ONLY).doClick();
        assertThat(queued).hasSize(2);
        queued.get(1).run();
        String latest = panel.summaryText();
        queued.get(0).run();
        assertThat(panel.summaryText()).isEqualTo(latest);
    }

    @Test
    void insertingClosesTheDialog() throws IOException {
        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();
        panel.controls().destinationButton(CreationForm.Destination.INSERT).doClick();
        panel.controls().createButton().doClick();
        assertThat(target.floating).hasSize(1);
        assertThat(closed).isEqualTo(1);
    }

    private static String sheet(List<List<String>> rows) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int row = 0; row < rows.size(); row++) {
            xml.append("<row r=\"").append(row + 1).append("\">");
            for (int column = 0; column < rows.get(row).size(); column++) {
                xml.append("<c r=\"").append((char) ('A' + column)).append(row + 1)
                        .append("\" t=\"inlineStr\"><is><t>").append(rows.get(row).get(column))
                        .append("</t></is></c>");
            }
            xml.append("</row>");
        }
        return xml.append("</sheetData></worksheet>").toString();
    }
}
