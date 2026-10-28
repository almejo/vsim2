package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableEvaluator;
import cl.almejo.vsim.core.truthtable.TruthTableScope;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6.1-A with every production extension installed (spec §7.3): the menu, the examples as untitled copies, the ripple
 * of the carry of the 4-bit adder in B's step mode and C's truth table of one «Sumador completo» instance.
 */
class ExamplesEndToEndTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private final List<OpenDocument> opened = new ArrayList<>();
    private LibraryService service;
    private EditorServices services;

    @BeforeEach
    void create() {
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        service = new LibraryService(preferences, library, registry, Runnable::run);
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                library, Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
    }

    @AfterEach
    void close() {
        opened.forEach(OpenDocument::close);
        service.close();
    }

    private OpenDocument track(OpenDocument document) {
        opened.add(document);
        return document;
    }

    /** What choosing the example does: a fresh copy named after the example, wrapped as an untitled document. */
    private OpenDocument openExample(String id) throws Exception {
        return openExample(id, Map.of());
    }

    /** Same, with some switches set first (label to the switch's {@code value} parameter). */
    private OpenDocument openExample(String id, Map<String, String> switchValues) throws Exception {
        CircuitDocument document = BundledExamples.open(id, registry);
        document.setName(Messages.text("example." + id));
        switchValues.forEach((label, value) ->
                document.replace(component(document, label).withParameters(Map.of("value", value))));
        return track(new OpenDocument(document, registry, null, false));
    }

    private static Component component(CircuitDocument document, String label) {
        return document.components().stream()
                .filter(component -> component.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component labeled " + label));
    }

    private static Value led(OpenDocument document, String label) {
        VisibleState state = document.latestSnapshot()
                .componentState(component(document.document(), label).id().value());
        return state instanceof VisibleState.LedState ledState ? ledState.value() : Value.HIGH_IMPEDANCE;
    }

    /** What the {@code index}-th 7-segment display (left to right) shows in the latest snapshot; Z before one. */
    private static Value display(OpenDocument document, int index) {
        Component display = document.document().components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY))
                .sorted(Comparator.comparingInt((Component component) -> component.position().x()))
                .toList().get(index);
        VisibleState state = document.latestSnapshot().componentState(display.id().value());
        return state instanceof VisibleState.SevenSegmentsState segments ? segments.value() : Value.HIGH_IMPEDANCE;
    }

    /** Waits for the first step report after {@code afterSequence} (-1: any). */
    private static StepReport nextReport(OpenDocument document, long afterSequence) {
        waitUntil(() -> document.latestStep().map(report -> report.sequence() > afterSequence).orElse(false));
        return document.latestStep().orElseThrow();
    }

    private static JMenu examplesMenu(EditorView view) {
        for (java.awt.Component child : view.menu(MenuId.FILE).getMenuComponents()) {
            if (child instanceof JMenu menu && "Ejemplos".equals(menu.getText())) {
                return menu;
            }
        }
        throw new AssertionError("no Ejemplos menu");
    }

    @Test
    void theExamplesMenuListsTheSixteenExamples() {
        OpenDocument empty = track(OpenDocument.createEmpty(registry));
        EditorView view = new EditorView(empty, services, NO_WINDOW);
        try {
            assertThat(texts(examplesMenu(view))).hasSize(16)
                    .contains("Sumador de 4 bits", "ALU de 4 bits", "Mini ALU", "Acumulador", "Programa en ROM",
                            "RAM que se llena");
        } finally {
            view.dispose();
        }
    }

    @Test
    void theFourBitAdderOpensUntitledUnmodifiedAndAddsWhenASwitchIsPressed() throws Exception {
        OpenDocument example = openExample("adder-4-bits");
        assertThat(example.displayName()).isEqualTo("Sumador de 4 bits");
        assertThat(example.path()).isEmpty();
        assertThat(example.isModified()).isFalse();
        assertThat(example.needsSaveAs()).as("Guardar asks for a file name").isTrue();
        assertThat(example.document().definition("sumador-completo")).isPresent();

        // A = 0000, B = 0000: the sum display reads 0 once the circuit has settled.
        waitUntil(() -> display(example, 2).equals(Value.of(4, 0)));
        // A = 1111 (press the four A switches), then Cin: 1111 + 0000 + 1 = 1 0000.
        for (String label : List.of("A0", "A1", "A2", "A3")) {
            example.interact(component(example.document(), label).id());
        }
        waitUntil(() -> display(example, 2).equals(Value.of(4, 15)));
        assertThat(led(example, "Cout")).isEqualTo(Value.ZERO);
        example.interact(component(example.document(), "Cin").id());
        waitUntil(() -> led(example, "Cout").equals(Value.ONE));
        assertThat(display(example, 2)).isEqualTo(Value.of(4, 0));
        assertThat(example.isModified()).as("a click changes the switch's «Valor» (2026-10-28)").isTrue();
    }

    @Test
    void theFourBitAdderRipplesItsCarryOnePressAtATimeInStepMode() throws Exception {
        // A = 1111, B = 0000: turning Cin on sends a carry through the four «Sumador completo» instances.
        OpenDocument example = openExample("adder-4-bits", Map.of("A0", "1", "A1", "1", "A2", "1", "A3", "1"));
        EditorView view = new EditorView(example, services, NO_WINDOW);
        try {
            Action advance = view.shortcutActions().get("simulation.advance");
            assertThat(advance).isNotNull();
            assertThat(advance.getValue(Action.ACCELERATOR_KEY)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
            // settled before the mode: the sum display (the third, A + B) can read F while other sums still have
            // events pending, and those would mix with the carry's steps (same rule as B's settleThenEnterStepMode)
            waitUntil(() -> example.latestSnapshot().token() == example.netlistToken()
                    && display(example, 2).equals(Value.of(4, 15)) && example.latestNextEventTime().isEmpty());

            example.setStepMode(true);
            assertThat(example.stepMode()).isTrue();
            long before = example.latestStep().map(StepReport::sequence).orElse(-1L);
            example.interact(component(example.document(), "Cin").id());
            long changesAfterTheClick = example.changeCount();
            StepReport report = nextReport(example, before);
            List<Long> times = new ArrayList<>();
            while (report.result().nextEventTime().isPresent()) {
                assertThat(times).as("the carry settles").hasSizeLessThan(50);
                example.advanceToNextEvent();
                report = nextReport(example, report.sequence());
                times.add(report.result().time());
            }
            assertThat(times).as("one simulation time per press").isSorted().doesNotHaveDuplicates()
                    .hasSizeGreaterThanOrEqualTo(4);
            waitUntil(() -> led(example, "Cout").equals(Value.ONE));
            assertThat(display(example, 2)).as("1111 + 0000 + 1 = 1 0000").isEqualTo(Value.of(4, 0));
            assertThat(example.changeCount()).as("stepping is not editing").isEqualTo(changesAfterTheClick);
        } finally {
            view.dispose();
        }
    }

    @Test
    void oneInstanceOfTheFullAdderHasAnEightRowTable() throws Exception {
        OpenDocument example = openExample("adder-4-bits");
        Component instance = example.document().components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf("sumador-completo")))
                .findFirst()
                .orElseThrow();
        Preparation preparation = TruthTableScope.prepare(example.document(), Set.of(instance.id()),
                example.registry());
        assertThat(preparation).isInstanceOf(Preparation.Ready.class);
        TruthTable table = TruthTableEvaluator.evaluate((Preparation.Ready) preparation, percent -> { }, () -> false)
                .orElseThrow();
        // one instance: the definition's pins in pinout order (C), which «Sumador completo» sets to Cin, A, B / S, Cout
        assertThat(table.inputs()).extracting(Column::name).containsExactly("Cin", "A", "B");
        assertThat(table.outputs()).extracting(Column::name).containsExactly("S", "Cout");
        assertThat(table.rows()).hasSize(8).allMatch(Row::settled);
    }
}
