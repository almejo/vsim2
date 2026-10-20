package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.PassThroughCircuit;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.paint.TextFonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrintInputTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:30:00Z"), ZoneOffset.UTC);

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void close() {
        TextFonts.set(TextFont.DEFAULT);
        openDocument.close();
    }

    private PrintInput capture(Set<ElementId> selection) {
        return PrintInput.capture(openDocument, selection, ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK);
    }

    private Wire addWire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY));
        openDocument.execute(new AddWireCommand(wire));
        return wire;
    }

    private Frame addFrame(int x, int y, int width, int height, String title) {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(x, y), width, height, title,
                FrameTint.BLUE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        return frame;
    }

    @Test
    void editingTheLiveDocumentAfterTheCaptureDoesNotChangeTheCopy() {
        Wire wire = addWire(10, 10, 20, 10);
        PrintInput input = capture(Set.of());
        List<DocumentElement> before = input.document().elements();
        openDocument.execute(new MoveElementsCommand(List.of(wire.id()), 5, 5));
        addWire(40, 40, 50, 40);
        assertThat(input.document().elements()).isEqualTo(before).containsExactly(wire);
        assertThat(PrintScopes.area(input, new PrintScope.WholeCircuit()).orElseThrow().elements())
                .containsExactly(wire.id());
    }

    @Test
    void theRegistryResolvesUserComponentsAgainstTheCopyEvenWhenTheLiveDocumentDropsThem() {
        PassThroughCircuit circuit = PassThroughCircuit.addTo(openDocument);
        PrintInput input = capture(Set.of());
        while (openDocument.history().canUndo()) {
            openDocument.history().undo();
        }
        assertThat(openDocument.registry().findTypeOf(circuit.instance())).isEmpty();
        assertThat(input.registry().findTypeOf(circuit.instance())).isPresent();
        assertThat(input.document().definitions()).hasSize(1);
    }

    @Test
    void theSchemeAndTheFontAreTheOnesOfTheMomentOfTheCapture() {
        addWire(10, 10, 20, 10);
        ColorScheme scheme = CircuitImage.printScheme(ColorScheme.LIGHT);
        TextFont font = new TextFont("Serif", 14);
        PrintInput input = PrintInput.capture(openDocument, Set.of(), scheme, font, CLOCK);
        TextFonts.set(new TextFont("Monospaced", 20));
        PrintInput later = PrintInput.capture(openDocument, Set.of(), ColorScheme.LIGHT, TextFonts.current(), CLOCK);
        assertThat(input.scheme()).isEqualTo(scheme);
        assertThat(input.textFont()).isEqualTo(font);
        assertThat(later.textFont()).isEqualTo(new TextFont("Monospaced", 20));
    }

    @Test
    void aSnapshotOfTheSameRevisionGivesValues() {
        addWire(10, 10, 20, 10);
        UiTestSupport.awaitSimulation(openDocument);
        PrintInput input = capture(Set.of());
        assertThat(input.hasValues()).isTrue();
        assertThat(input.valuesProblem()).isEmpty();
        assertThat(input.netlistToken()).isEqualTo(input.document().revision());
        assertThat(input.snapshot().token()).isEqualTo(input.netlistToken());
        assertThat(input.netlist()).isSameAs(openDocument.netlist());
    }

    @Test
    void aStaleSnapshotGivesNoValuesAndSaysWhy() {
        addWire(10, 10, 20, 10);
        SimulationSnapshot stale = new SimulationSnapshot(0, openDocument.netlistToken() - 1, 0, false, Map.of(),
                Map.of(), List.of(), Optional.empty());
        PrintInput input = PrintInput.capture(new PrintInput.LiveCircuit(openDocument.document(),
                openDocument.registry(), openDocument.netlist(), openDocument.netlistToken(), stale, "Circuito",
                Optional.empty()), Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK);
        assertThat(input.hasValues()).isFalse();
        assertThat(input.valuesProblem()).contains(Messages.text("print.values.stale"));
        // the netlist still belongs to the copy (open pins), only the values are not trusted
        assertThat(input.netlistToken()).isEqualTo(input.document().revision());
        assertThat(input.snapshot().token()).isNotEqualTo(input.netlistToken());
    }

    @Test
    void aNetlistOfAnotherRevisionIsNotTrustedEither() {
        addWire(10, 10, 20, 10);
        UiTestSupport.awaitSimulation(openDocument);
        long oldToken = openDocument.netlistToken() - 1;
        SimulationSnapshot snapshot = new SimulationSnapshot(0, oldToken, 0, false, Map.of(), Map.of(), List.of(),
                Optional.empty());
        PrintInput input = PrintInput.capture(new PrintInput.LiveCircuit(openDocument.document(),
                openDocument.registry(), openDocument.netlist(), oldToken, snapshot, "Circuito",
                Optional.empty()), Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK);
        assertThat(input.hasValues()).isFalse();
        assertThat(input.netlistToken()).isNotEqualTo(input.document().revision());
    }

    @Test
    void theCaptureCreatesNoCommandKeepsModifiedAndTheSelection() {
        Wire wire = addWire(10, 10, 20, 10);
        openDocument.history().markSaved();
        long revision = openDocument.document().revision();
        long changes = openDocument.changeCount();
        Optional<String> undoDescription = openDocument.history().undoDescription();
        Set<ElementId> selection = Set.of(wire.id());
        PrintInput input = capture(selection);
        assertThat(openDocument.isModified()).isFalse();
        assertThat(openDocument.changeCount()).isEqualTo(changes);
        assertThat(openDocument.document().revision()).isEqualTo(revision);
        assertThat(openDocument.history().undoDescription()).isEqualTo(undoDescription);
        assertThat(input.selection()).containsExactly(wire.id());
    }

    @Test
    void theSelectionKeepsOnlyElementsOfTheDocument() {
        Wire wire = addWire(10, 10, 20, 10);
        PrintInput input = capture(Set.of(wire.id(), new ElementId("c999")));
        assertThat(input.selection()).containsExactly(wire.id());
    }

    @Test
    void theNamesAndTheDateComeFromTheDocumentAndTheClock() {
        addWire(10, 10, 20, 10);
        PrintInput input = capture(Set.of());
        assertThat(input.circuitName()).isEqualTo(openDocument.displayName());
        assertThat(input.fileName()).isEmpty();
        assertThat(input.date()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void framesAreListedInTreeOrderWithTheirDepth() {
        Frame outer = addFrame(0, 0, 60, 40, "Sumador");
        Frame inner = addFrame(5, 5, 20, 10, "Acarreo");
        Frame other = addFrame(100, 0, 20, 20, "Salida");
        PrintInput input = capture(Set.of());
        assertThat(input.frames()).containsExactly(
                new FrameEntry(outer.id(), "Sumador", 0),
                new FrameEntry(inner.id(), "Acarreo", 1),
                new FrameEntry(other.id(), "Salida", 0));
    }

    @Test
    void repeatedAndEmptyTitlesAreListedDistinctly() {
        Frame first = addFrame(0, 0, 20, 20, "Sumador");
        Frame second = addFrame(30, 0, 20, 20, "Sumador");
        Frame untitled = addFrame(60, 0, 20, 20, "");
        Frame blank = addFrame(90, 0, 20, 20, "   ");
        PrintInput input = capture(Set.of());
        String untitledText = Messages.text("print.frame.untitled");
        assertThat(input.frames()).containsExactly(
                new FrameEntry(first.id(), Messages.format("print.frame.repeated", "Sumador", 1), 0),
                new FrameEntry(second.id(), Messages.format("print.frame.repeated", "Sumador", 2), 0),
                new FrameEntry(untitled.id(), Messages.format("print.frame.repeated", untitledText, 1), 0),
                new FrameEntry(blank.id(), Messages.format("print.frame.repeated", untitledText, 2), 0));
        assertThat(input.frames()).extracting(FrameEntry::displayTitle).doesNotHaveDuplicates();
    }

    @Test
    void aSingleUntitledFrameIsCalledMarcoSinTitulo() {
        Frame untitled = addFrame(0, 0, 20, 20, "");
        assertThat(capture(Set.of()).frames()).containsExactly(
                new FrameEntry(untitled.id(), Messages.text("print.frame.untitled"), 0));
    }

    @Test
    void aComponentIsCopiedWithItsDefinitionsAndOrder() {
        Component led = UiTestSupport.place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        Wire wire = addWire(0, 0, 5, 0);
        PrintInput input = capture(Set.of());
        assertThat(input.document().elements()).containsExactly(led, wire);
        assertThat(input.document().orderOf(wire.id())).isEqualTo(openDocument.document().orderOf(wire.id()));
    }
}
