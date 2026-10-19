package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JRootPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final List<String> events = new ArrayList<>();
    private final List<TruthTableSpec> wizardSpecs = new ArrayList<>();
    private final List<WizardOptions> wizardOptions = new ArrayList<>();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        open(Background.inline());
    }

    private void open(Background background) {
        panel = new TableImportPanel(new TableSources(parent -> {
            chooserCalls++;
            return Optional.ofNullable(chosen);
        }, () -> clipboard), target, background, (spec, options) -> {
            events.add("wizard");
            wizardSpecs.add(spec);
            wizardOptions.add(options);
        });
        panel.setOnClose(() -> {
            closed++;
            events.add("closed");
        });
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
        assertThat(target.newCircuits.getFirst().notice()).isEqualTo("✓ El circuito cumple la tabla.");
        assertThat(closed).as("the work is done: the dialog closes").isEqualTo(1);
    }

    @Test
    void theInputsAreGuessedFromTheRowsWhenNoNameLooksLikeAnOutput() {
        StringBuilder majority = new StringBuilder("A\tB\tC\tM\n");
        for (int row = 0; row < 8; row++) {
            majority.append(row >> 2 & 1).append('\t').append(row >> 1 & 1).append('\t').append(row & 1).append('\t')
                    .append(Integer.bitCount(row) >= 2 ? 1 : 0).append('\n');
        }
        clipboard = Optional.of(majority.toString());
        panel.pasteButton().doClick();

        assertThat(panel.inputCountSpinner().getValue()).isEqualTo(3);
        assertThat(panel.columnsText()).isEqualTo("Entradas: A, B, C · Salidas: M");
        assertThat(panel.problemsText()).isEmpty();
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
        assertThat(panel.problemsText())
                .startsWith("La combinación 0 0 aparece dos veces con B y Suma distintas (filas 2 y 3).");
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
        assertThat(panel.closeButton().isEnabled()).as("closing cancels").isTrue();
        assertThat(panel.closeButton().getText()).isEqualTo("Cancelar");
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
        assertThat(panel.closeButton().getText()).isEqualTo("Cerrar");
    }

    @Test
    void closingWhileTheCircuitIsBuiltCancelsItAndTheLateResultIsIgnored() throws IOException {
        open(new Background(queued::add, Runnable::run));
        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();
        queued.removeFirst().run(); // the file
        queued.removeFirst().run(); // the summary
        panel.controls().componentCheckBox().doClick();
        panel.controls().createButton().doClick();

        panel.closeButton().doClick();

        assertThat(closed).isEqualTo(1);
        queued.removeFirst().run();
        assertThat(target.saved).isEmpty();
        assertThat(target.newCircuits).isEmpty();
        assertThat(target.floating).isEmpty();
    }

    @Test
    void closingWhileAFileIsReadDropsTheFile() throws IOException {
        open(new Background(queued::add, Runnable::run));
        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();

        panel.requestClose();

        assertThat(closed).isEqualTo(1);
        queued.removeFirst().run();
        assertThat(panel.model().hasTable()).isFalse();
        assertThat(queued).as("no summary for a dropped file").isEmpty();
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

    @Test
    void aBlankSpacerColumnIsLeftOutOfTheProposalAndTheNames() throws IOException {
        chosen = write("espacio.csv", "A,,B,F\n0,,0,0\n0,,1,1\n1,,0,1\n1,,1,0\n");
        panel.openFileButton().doClick();

        assertThat(panel.inputCountSpinner().getValue()).isEqualTo(2);
        assertThat(panel.columnsText()).isEqualTo("Entradas: A, B · Salidas: F");
        assertThat(((SpinnerNumberModel) panel.inputCountSpinner().getModel()).getMaximum()).isEqualTo(2);
        assertThat(panel.problemsText()).isEmpty();
        assertThat(panel.controls().createButton().isEnabled()).isTrue();
    }

    @Test
    void aLongListOfProblemsShowsTheFirstTwentyAndCountsTheRest() throws IOException {
        StringBuilder csv = new StringBuilder("A,B,C,D,E,F\n");
        for (int row = 0; row < 32; row++) {
            csv.append(row >> 4 & 1).append(',').append(row >> 3 & 1).append(',').append(row >> 2 & 1).append(',')
                    .append(row >> 1 & 1).append(',').append(row & 1).append(",Q\n");
        }
        chosen = write("mala.csv", csv.toString());
        panel.openFileButton().doClick();

        List<String> lines = panel.problemsText().lines().toList();
        assertThat(lines).hasSize(21);
        assertThat(lines.getLast()).isEqualTo("… y 12 problemas más.");
    }

    @Test
    void typingTheTitleDoesNotRecomputeTheSummary() throws IOException {
        open(new Background(queued::add, Runnable::run));
        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();
        queued.removeFirst().run(); // the file
        queued.removeFirst().run(); // the summary
        String summary = panel.summaryText();

        panel.titleField().setText("Sumador completo");
        assertThat(queued).as("the title does not change the circuit").isEmpty();
        assertThat(panel.summaryText()).isEqualTo(summary);

        panel.titleField().setText(" ");
        assertThat(panel.summaryText()).isBlank();
        panel.titleField().setText("Sumador");
        assertThat(queued).isEmpty();
        assertThat(panel.summaryText()).isEqualTo(summary);

        panel.gateButton(WizardOptions.GateChoice.NAND_ONLY).doClick();
        assertThat(queued).hasSize(1);
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

    @Test
    void createSitsInTheBottomBarAndIsOnOnlyForALoadedTableWithValidOptions() throws IOException {
        open(new Background(queued::add, Runnable::run));
        JRootPane root = new JRootPane();
        root.getContentPane().add(panel);
        JButton create = panel.createButton();
        assertThat(SwingUtilities.isDescendingFrom(create, panel.controls())).isFalse();
        assertThat(SwingUtilities.isDescendingFrom(create, panel)).isTrue();
        assertThat(panel.buttonBarTexts()).containsExactly("Abrir en el asistente", "Crear", "Cerrar");
        assertThat(create.isEnabled()).as("no table yet").isFalse();
        assertThat(root.getDefaultButton()).isNull();

        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();
        assertThat(create.isEnabled()).as("while reading").isFalse();
        queued.removeFirst().run(); // the file
        assertThat(create.isEnabled()).isTrue();
        assertThat(root.getDefaultButton()).isSameAs(create);

        panel.controls().componentCheckBox().doClick();
        panel.controls().nameField().setText(" ");
        assertThat(create.isEnabled()).as("a blank component name").isFalse();
        assertThat(root.getDefaultButton()).isNull();
        panel.controls().nameField().setText("Sumador");
        assertThat(create.isEnabled()).isTrue();
        assertThat(root.getDefaultButton()).isSameAs(create);

        panel.titleField().setText("");
        assertThat(create.isEnabled()).as("a blank title").isFalse();
        panel.titleField().setText("sumador");

        queued.clear(); // the summaries
        create.doClick();
        assertThat(create.isEnabled()).as("while building").isFalse();
        assertThat(root.getDefaultButton()).isNull();
        queued.removeFirst().run();
        assertThat(target.newCircuits).hasSize(1);
        assertThat(create.isEnabled()).isTrue();
    }

    @Test
    void aTableWithProblemsCannotBeCreated() throws IOException {
        chosen = write("malo.csv", "A,B,F\n0,0,2\n");
        panel.openFileButton().doClick();
        assertThat(panel.problemsText()).isNotEmpty();
        assertThat(panel.createButton().isEnabled()).isFalse();
    }

    @Test
    void aRepeatedCombinationNamesTheOutputsThatDifferAndBothRows() {
        clipboard = Optional.of("A\tB\tC\tF\tG\n0\t0\t0\t0\t0\n0\t0\t1\t0\t0\n0\t1\t0\t0\t0\n0\t1\t1\t1\t0\n"
                + "1\t0\t0\t0\t0\n1\t0\t1\t0\t0\n1\t1\t0\t0\t0\n1\t1\t1\t0\t0\n0\t1\t1\t0\t0\n0\t1\t1\t0\t1\n");
        panel.pasteButton().doClick();

        assertThat(panel.problemsText()).isEqualTo("""
                La combinación 0 1 1 aparece dos veces con F distinta (filas 5 y 10).
                La combinación 0 1 1 aparece dos veces con F y G distintas (filas 5 y 11).""");

        Messages.initialize(Locale.ENGLISH);
        try {
            assertThat(panel.model().problemLines()).containsExactly(
                    "The combination 0 1 1 appears twice with a different F (rows 5 and 10).",
                    "The combination 0 1 1 appears twice with different F and G (rows 5 and 11).");
        } finally {
            Messages.initialize(Messages.SPANISH);
        }
    }

    @Test
    void openInTheWizardClosesTheDialogAndHandsOverTheTableWithItsOptions() throws IOException {
        chosen = write("incompleta.csv", "A;B;C;Y\n0;0;0;1\n1;1;1;0\n0;1;0;1\n1;0;1;0\n");
        panel.openFileButton().doClick();
        panel.gateButton(WizardOptions.GateChoice.NAND_ONLY).doClick();
        assertThat(panel.wizardButton().isEnabled()).isTrue();
        assertThat(panel.wizardReasonText()).isEmpty();

        panel.wizardButton().doClick();

        assertThat(events).as("the dialog closes first, without asking").containsExactly("closed", "wizard");
        TruthTableSpec spec = wizardSpecs.getFirst();
        assertThat(spec.title()).isEqualTo("incompleta");
        assertThat(spec.inputNames()).containsExactly("A", "B", "C");
        assertThat(spec.outputNames()).containsExactly("Y");
        assertThat(spec.outputs().getFirst()).containsExactly(LogicValue.ONE, LogicValue.DONT_CARE, LogicValue.ONE,
                LogicValue.DONT_CARE, LogicValue.DONT_CARE, LogicValue.ZERO, LogicValue.DONT_CARE, LogicValue.ZERO);
        assertThat(wizardOptions.getFirst().gates()).isEqualTo(WizardOptions.GateChoice.NAND_ONLY);
        assertThat(wizardOptions.getFirst().form()).isEqualTo(Minimization.Form.SUM_OF_PRODUCTS);
    }

    @Test
    void openInTheWizardIsOffWithTheReasonWhenTheSplitDoesNotFitTheWizard() throws IOException {
        assertThat(panel.wizardButton().isEnabled()).as("no table yet").isFalse();
        assertThat(panel.wizardReasonText()).isEmpty();

        chosen = write("sumador.csv", ADDER);
        panel.openFileButton().doClick();
        assertThat(panel.wizardButton().isEnabled()).isTrue();

        panel.inputCountSpinner().setValue(1);
        assertThat(panel.problemsText()).isNotEmpty();
        assertThat(panel.wizardButton().isEnabled()).as("1 input, 4 outputs").isFalse();
        assertThat(panel.wizardReasonText()).isEqualTo("El asistente funciona con 2 a 4 entradas y hasta 4 salidas");

        panel.inputCountSpinner().setValue(3);
        panel.titleField().setText(" ");
        assertThat(panel.wizardButton().isEnabled()).as("a blank title is a problem").isFalse();
        assertThat(panel.wizardReasonText()).as("the problem is listed, the split fits").isEmpty();

        panel.wizardButton().doClick();
        assertThat(events).isEmpty();
    }

    @Test
    void aTableWithMoreThanFourInputsCannotGoToTheWizard() throws IOException {
        StringBuilder parity = new StringBuilder("A,B,C,D,E,P\n");
        for (int row = 0; row < 32; row++) {
            for (int bit = 4; bit >= 0; bit--) {
                parity.append(row >> bit & 1).append(',');
            }
            parity.append(Integer.bitCount(row) & 1).append('\n');
        }
        chosen = write("paridad.csv", parity.toString());
        panel.openFileButton().doClick();
        panel.inputCountSpinner().setValue(5);
        assertThat(panel.problemsText()).isEmpty();
        assertThat(panel.createButton().isEnabled()).isTrue();
        assertThat(panel.wizardButton().isEnabled()).isFalse();
        assertThat(panel.wizardReasonText()).isEqualTo("El asistente funciona con 2 a 4 entradas y hasta 4 salidas");
    }
}
