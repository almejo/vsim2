package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
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
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6.1-A with every production extension installed. Task 11b (after fronts B and C) adds the step-mode ripple of the
 * carry in the 4-bit adder and the truth table of one «Sumador completo» instance.
 */
class ExamplesEndToEndTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

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
        Messages.initialize(Messages.SPANISH);
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
        CircuitDocument document = BundledExamples.open(id, registry);
        document.setName(Messages.text("example." + id));
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

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
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
    void theExamplesMenuListsTheTwelveExamples() {
        OpenDocument empty = track(OpenDocument.createEmpty(registry));
        EditorView view = new EditorView(empty, services, NO_WINDOW);
        try {
            assertThat(texts(examplesMenu(view))).hasSize(12).contains("Sumador de 4 bits", "ALU de 4 bits");
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
        assertThat(example.isModified()).as("running is not editing").isFalse();
    }
}
