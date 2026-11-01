package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/** The Terminal and Teclado features of an editor window: double click, context menu, auto-open, cleanup. */
class TerminalExtensionTest {

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<String> shownTitles = new ArrayList<>();
    private final List<Boolean> closed = new ArrayList<>();
    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private final List<String> questions = new ArrayList<>();
    private Optional<Path> chosenFile = Optional.empty();
    private EditorView view;
    private Runnable dispose;

    @TempDir
    Path folder;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        if (dispose != null) {
            dispose.run();
        }
        document.close();
    }

    private TerminalServices services() {
        TerminalWindowFactory factory = (owner, title, content, bounds, closedByUser) -> {
            shownTitles.add(title);
            int index = closed.size();
            closed.add(false);
            return new TerminalWindowHandle() {
                @Override
                public void setTitle(String newTitle) {
                }

                @Override
                public void toFront() {
                }

                @Override
                public Rectangle bounds() {
                    return new Rectangle(0, 0, 100, 100);
                }

                @Override
                public void close() {
                    closed.set(index, true);
                }
            };
        };
        TerminalWindowServices windows = new TerminalWindowServices(factory, text -> { },
                () -> Optional.of("hola"), (parent, suggested) -> chosenFile, Runnable::run, Runnable::run,
                TerminalScreenRenderer.standard(), System::nanoTime);
        return new TerminalServices(windows, () -> ticker, tone -> { }, Runnable::run, Runnable::run,
                (owner, name, file) -> questions.add(name + " → " + file) && false);
    }

    private void install() {
        EditorServices editorServices = new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of());
        view = new EditorView(document, editorServices, NO_WINDOW);
        dispose = new TerminalExtension(services()).install(view);
    }

    private Component terminal(String label, Map<String, Object> parameters) {
        ComponentType type = document.registry().require(StandardComponentTypes.TERMINAL);
        Component component = type.newComponent(document.document().newComponentId(), new GridPoint(0, 0),
                Rotation.R0, parameters, label);
        document.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    private List<String> menuTexts(Component component) {
        JPopupMenu menu = view.contextMenu(Optional.of(component), new Point2D.Double(1, 1));
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item) {
                texts.add(item.getText());
            }
        }
        return texts;
    }

    private void click(Component component, String text) {
        JPopupMenu menu = view.contextMenu(Optional.of(component), new Point2D.Double(1, 1));
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                item.doClick();
                return;
            }
        }
        throw new AssertionError("no item " + text);
    }

    private Component uart(String label, String screen) {
        ComponentType type = document.registry().require(StandardComponentTypes.UART_16550);
        Component component = type.newComponent(document.document().newComponentId(), new GridPoint(0, 0),
                Rotation.R0, Map.of(UartTypes.SCREEN, screen), label);
        document.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    @Test
    void anIntegratedUartsMenuHasTheTerminalsAndTheTecladosItems() {
        install();
        Component uart0 = uart("uart0", UartTypes.SCREEN_INTEGRATED);

        assertThat(menuTexts(uart0)).contains("Abrir en ventana", "Limpiar pantalla", "Pegar texto", "Vaciar cola");
        click(uart0, "Abrir en ventana");
        assertThat(shownTitles).containsExactly("uart0");
    }

    @Test
    void aUartWithTxAndRxPinsHasNoneOfThem() {
        install();
        Component uart0 = uart("uart0", UartTypes.SCREEN_PINS);

        assertThat(menuTexts(uart0)).doesNotContain("Abrir en ventana", "Limpiar pantalla", "Pegar texto",
                "Vaciar cola");
    }

    @Test
    void aDoubleClickOnTheScreenOpensTheWindow() {
        install();
        Component consola = terminal("consola", Map.of());

        view.editTerminal(consola);

        assertThat(shownTitles).containsExactly("consola");
    }

    @Test
    void theTerminalsMenuOpensItsWindowAndClearsItsScreen() {
        install();
        Component consola = terminal("consola", Map.of());

        assertThat(menuTexts(consola)).contains("Abrir en ventana", "Limpiar pantalla");
        click(consola, "Abrir en ventana");
        assertThat(shownTitles).containsExactly("consola");
        boolean modified = document.isModified();
        click(consola, "Limpiar pantalla");
        awaitSimulation(document);
        assertThat(document.isModified()).isEqualTo(modified);
    }

    @Test
    void aTerminalWithACharacterRomCanRereadItFromItsMenuWithoutAnEdit() {
        install();
        Component internal = terminal("interno", Map.of());
        Component external = terminal("rom", Map.of(TerminalTypes.GLYPHS, TerminalTypes.GLYPHS_EXTERNAL));

        assertThat(menuTexts(internal)).doesNotContain("Releer la ROM de caracteres");
        assertThat(menuTexts(external)).contains("Abrir en ventana", "Limpiar pantalla",
                "Releer la ROM de caracteres");
        boolean modified = document.isModified();
        click(external, "Releer la ROM de caracteres");
        awaitSimulation(document);
        assertThat(document.isModified()).isEqualTo(modified);
    }

    @Test
    void theTecladosMenuPastesTextAndEmptiesItsQueue() {
        install();
        Component keyboard = place(document, StandardComponentTypes.KEYBOARD, 0, 40, Map.of());
        awaitSimulation(document);

        assertThat(menuTexts(keyboard)).contains("Pegar texto", "Vaciar cola");
        click(keyboard, "Pegar texto");
        waitUntil(() -> queueSize(keyboard) == 4);
        click(keyboard, "Vaciar cola");
        waitUntil(() -> queueSize(keyboard) == 0);
    }

    @Test
    void aTerminalShownInAWindowOpensItWithTheDocumentAndWhenPlaced() {
        terminal("al abrir", Map.of(TerminalTypes.SHOW, "window"));
        install();
        assertThat(shownTitles).containsExactly("al abrir");

        terminal("nueva", Map.of(TerminalTypes.SHOW, "window"));
        terminal("solo canvas", Map.of());

        assertThat(shownTitles).containsExactly("al abrir", "nueva");
    }

    @Test
    void theFramesArePolledOnlyWhileTheCircuitHasATerminal() {
        install();
        assertThat(ticker.isRunning()).isFalse();

        terminal("consola", Map.of());

        assertThat(ticker.isRunning()).isTrue();
    }

    @Test
    void disposingClosesTheWindowsAndTakesEveryHookBack() {
        install();
        int overlaysBefore = view.canvas().paneHooks().toolTipOverrides();
        Component consola = terminal("consola", Map.of());
        view.editTerminal(consola);

        dispose.run();
        dispose = null;

        assertThat(closed).containsExactly(true);
        assertThat(ticker.isRunning()).isFalse();
        assertThat(view.canvas().paneHooks().toolTipOverrides()).isLessThan(overlaysBefore);
        assertThat(view.hasTerminalEditor()).as("a double click configures again").isFalse();
    }

    private int queueSize(Component keyboard) {
        awaitSimulation(document);
        return document.latestSnapshot().componentState(keyboard.id().value())
                instanceof VisibleState.KeyboardState state ? state.size() : -1;
    }

    private static <T extends java.awt.Component> T named(Container root, Class<T> kind, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (kind.isInstance(child) && name.equals(child.getName())) {
                return kind.cast(child);
            }
            if (child instanceof Container container) {
                T nested = named(container, kind, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private Map<String, Object> resolvedParameters(Component component) {
        Component now = document.document().component(component.id()).orElseThrow();
        return document.registry().typeOf(now).resolveParameters(now.parameters());
    }

    @Test
    void theOutputFileIsSetOnlyWithTheChooserAndInOneUndoStep() {
        install();
        Component consola = terminal("consola", Map.of());
        view.selection().select(consola.id());
        JTextField shown = named(view.labelFeatures().properties(), JTextField.class, TerminalOutputRows.FILE_NAME);
        assertThat(shown).isNotNull();
        assertThat(shown.isEditable()).isFalse();
        assertThat(shown.getText()).isEqualTo("(ninguno)");
        Path file = folder.resolve("salida.txt");
        chosenFile = Optional.of(file);

        named(view.labelFeatures().properties(), JButton.class, TerminalOutputRows.CHOOSE_NAME).doClick();

        assertThat(resolvedParameters(consola)).containsEntry(TerminalTypes.SAVE_OUTPUT,
                TerminalTypes.SAVE_OUTPUT_TO_FILE).containsEntry(TerminalTypes.OUTPUT_FILE, file.toString());
        assertThat(questions).as("a file the user chose is not asked about").isEmpty();
        document.history().undo();
        assertThat(resolvedParameters(consola)).containsEntry(TerminalTypes.SAVE_OUTPUT, TerminalTypes.SAVE_OUTPUT_NO)
                .containsEntry(TerminalTypes.OUTPUT_FILE, "");
    }

    @Test
    void aSavedCircuitsOutputFileIsAskedAboutOnceAndNeverTouchedWithoutAYes() throws Exception {
        Path file = folder.resolve("tesis.txt");
        Files.writeString(file, "years of work");
        terminal("consola", Map.of(TerminalTypes.SAVE_OUTPUT, TerminalTypes.SAVE_OUTPUT_TO_FILE,
                TerminalTypes.OUTPUT_FILE, file.toString()));

        install();
        awaitSimulation(document);
        ticker.advance(100);

        assertThat(questions).containsExactly("consola → " + file);
        assertThat(Files.readString(file)).isEqualTo("years of work");
    }

    private String hint() {
        javax.swing.JTextArea label = named(view.labelFeatures().properties(), javax.swing.JTextArea.class,
                TerminalOutputRows.HINT_NAME);
        return label == null ? null : label.getText();
    }

    @Test
    void theOutputHintSaysWhetherThisRunIsWritingToTheFile() {
        install();
        Component consola = terminal("consola", Map.of());
        view.selection().select(consola.id());
        assertThat(hint()).as("not saving: nothing to say").isNull();

        chosenFile = Optional.of(folder.resolve("salida.txt"));
        named(view.labelFeatures().properties(), JButton.class, TerminalOutputRows.CHOOSE_NAME).doClick();

        assertThat(hint()).isEqualTo(Messages.text("terminal.output.hint.armed"));
    }

    @Test
    void theOutputHintSaysItIsNotWritingWhenTheUserSaidNoOrNoFileIsNamed() throws Exception {
        Path file = folder.resolve("tesis.txt");
        Component declined = terminal("consola", Map.of(TerminalTypes.SAVE_OUTPUT, TerminalTypes.SAVE_OUTPUT_TO_FILE,
                TerminalTypes.OUTPUT_FILE, file.toString()));
        Component nameless = terminal("otra", Map.of(TerminalTypes.SAVE_OUTPUT, TerminalTypes.SAVE_OUTPUT_TO_FILE));
        install();
        awaitSimulation(document);

        view.selection().select(declined.id());
        assertThat(hint()).isEqualTo(Messages.text("terminal.output.hint.unarmed"));
        view.selection().select(nameless.id());
        assertThat(hint()).isEqualTo(Messages.text("terminal.output.hint.noFile"));
    }
}
