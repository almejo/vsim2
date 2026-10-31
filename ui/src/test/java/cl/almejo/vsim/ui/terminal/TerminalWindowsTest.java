package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/** The Terminal window (spec §9): one per part, its bar, its three tabs, its geometry; never shown in a test. */
class TerminalWindowsTest {

    /** What the fake factory was asked to show. */
    private static final class FakeWindow implements TerminalWindowHandle {
        private final String title;
        private final JComponent content;
        private final Optional<Rectangle> restoredBounds;
        private final Runnable closedByUser;
        private Rectangle bounds = new Rectangle(0, 0, 640, 480);
        private boolean closed;
        private int broughtToFront;
        private String currentTitle;

        private FakeWindow(String title, JComponent content, Optional<Rectangle> restoredBounds,
                           Runnable closedByUser) {
            this.title = title;
            this.content = content;
            this.restoredBounds = restoredBounds;
            this.closedByUser = closedByUser;
            this.currentTitle = title;
        }

        @Override
        public void setTitle(String newTitle) {
            currentTitle = newTitle;
        }

        @Override
        public void toFront() {
            broughtToFront++;
        }

        @Override
        public Rectangle bounds() {
            return new Rectangle(bounds);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @TempDir
    Path folder;

    private OpenDocument document;
    private ComponentType terminalType;
    private final FakeTerminals terminals = new FakeTerminals();
    private final List<FakeWindow> shown = new ArrayList<>();
    private final List<String> copied = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private Optional<Path> chosenFile = Optional.empty();
    private TerminalFeeds feeds;
    private TerminalWindows windows;
    private final Object owner = new Object();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        terminalType = document.registry().require(StandardComponentTypes.TERMINAL);
        feeds = new TerminalFeeds(terminals);
        windows = new TerminalWindows(document, feeds, services(),
                new TerminalWindowPreferences(preferences.terminalWindowsNode()));
    }

    @AfterEach
    void close() {
        windows.dispose();
        document.close();
    }

    private TerminalWindowServices services() {
        TerminalWindowFactory factory = (ownerWindow, title, content, bounds, closedByUser) -> {
            FakeWindow window = new FakeWindow(title, content, bounds, closedByUser);
            shown.add(window);
            return window;
        };
        return new TerminalWindowServices(factory, copied::add, () -> Optional.of("pegado"),
                (parent, suggested) -> chosenFile, Runnable::run, Runnable::run, TerminalScreenRenderer.standard(),
                System::nanoTime);
    }

    private Component terminal(String label, Map<String, Object> parameters) {
        Map<String, Object> all = new HashMap<>(Map.of(TerminalTypes.ROWS, 2, TerminalTypes.COLUMNS, 10));
        all.putAll(parameters);
        Component component = terminalType.newComponent(document.document().newComponentId(), new GridPoint(0, 0),
                Rotation.R0, all, label);
        document.execute(new AddElementsCommand(List.of(component)));
        terminals.add(component.id().value(), 2, 10);
        return component;
    }

    private TerminalWindowOwner opener() {
        return new TerminalWindowOwner(owner, Optional.<Window>empty(), notices::add, () -> ColorScheme.LIGHT);
    }

    private TerminalWindowContent open(Component component) {
        windows.open(component.id().value(), opener());
        return windows.content(component.id().value()).orElseThrow();
    }

    private void arrive(Component component, String text) {
        terminals.terminal(component.id().value()).write(text);
        terminals.publish(component.id().value());
        feeds.poll();
    }

    @Test
    void theWindowShowsTheNameTheSizeAndTheModeAndAsksForTheHistory() {
        Component consola = terminal("consola", Map.of());

        TerminalWindowContent content = open(consola);

        assertThat(shown).hasSize(1);
        assertThat(shown.getFirst().title).isEqualTo("consola");
        assertThat(content.barText()).isEqualTo("consola · 2×10 · VT100‑xterm");
        assertThat(content.tabTitles()).containsExactly("Pantalla", "Monitor de bytes", "Salida esperada");
        assertThat(terminals.request(consola.id().value()).orElseThrow().historyLines())
                .as("the history the part keeps").isEqualTo(1000);
    }

    @Test
    void theScreenShowsTheTextAndTheHistoryAndCopiesBoth() {
        Component consola = terminal("consola", Map.of());
        TerminalWindowContent content = open(consola);

        arrive(consola, "uno\ndos\ntres");
        content.copyText();

        assertThat(content.screen().historyLines()).isEqualTo(1);
        assertThat(content.screen().visibleText()).isEqualTo("dos\ntres");
        assertThat(copied).containsExactly("uno\ndos\ntres");
    }

    @Test
    void theByteMonitorListsEveryByteAndDumpsThemInHex() {
        Component consola = terminal("consola", Map.of());
        TerminalWindowContent content = open(consola);

        arrive(consola, "Hi\n");

        assertThat(content.monitor().rowCount()).isEqualTo(3);
        assertThat(content.monitor().row(2)).containsExactly("30", "0A", "10", "LF");
        assertThat(content.monitor().countText()).isEqualTo("3 bytes");
        assertThat(content.monitor().hexDumpText()).isEqualTo("00000000  48 69 0a" + " ".repeat(42) + "|Hi.|");
        terminals.terminal(consola.id().value()).restart();
        arrive(consola, "x");
        assertThat(content.monitor().rowCount()).as("a restart empties the monitor").isEqualTo(1);
    }

    @Test
    void theExpectedTabComparesTheTranscriptWithTheExpectedText() {
        Component consola = terminal("consola", Map.of(TerminalTypes.EXPECTED, TerminalTypes.EXPECTED_WRITTEN,
                TerminalTypes.EXPECTED_TEXT, "? 5\nfact(5)=120\n"));
        TerminalWindowContent content = open(consola);

        arrive(consola, "? 5\nfact");
        assertThat(content.expected().statusText()).startsWith("… va bien: 8 de 16");
        arrive(consola, "(5)=120\n");
        assertThat(content.expected().statusText()).isEqualTo("✓ La salida es igual a la esperada.");
        assertThat(content.expected().actualText()).isEqualTo("? 5\nfact(5)=120\n");
    }

    @Test
    void theExpectedTextCanComeFromAFile() throws IOException {
        Path expected = folder.resolve("esperada.txt");
        Files.writeString(expected, "ab", StandardCharsets.UTF_8);
        Component consola = terminal("consola", Map.of(TerminalTypes.EXPECTED, TerminalTypes.EXPECTED_FROM_FILE,
                TerminalTypes.EXPECTED_FILE, expected.toString()));
        TerminalWindowContent content = open(consola);

        arrive(consola, "ax");

        assertThat(content.expected().expectedText()).isEqualTo("ab");
        assertThat(content.expected().statusText()).startsWith("✗ Difiere en el carácter 2");
    }

    @Test
    void aMissingExpectedFileIsTold() {
        Component consola = terminal("consola", Map.of(TerminalTypes.EXPECTED, TerminalTypes.EXPECTED_FROM_FILE,
                TerminalTypes.EXPECTED_FILE, folder.resolve("no-esta.txt").toString()));

        TerminalWindowContent content = open(consola);

        assertThat(content.expected().statusText()).startsWith("No se pudo leer");
    }

    @Test
    void clearingIsNotAnEdit() {
        Component consola = terminal("consola", Map.of());
        TerminalWindowContent content = open(consola);
        boolean modified = document.isModified();
        Object undo = document.history().undoDescription();

        content.clear();
        awaitSimulation(document);

        assertThat(document.isModified()).isEqualTo(modified);
        assertThat(document.history().undoDescription()).isEqualTo(undo);
    }

    @Test
    void savingWritesTheTextToTheChosenFile() throws IOException {
        Component consola = terminal("consola", Map.of());
        TerminalWindowContent content = open(consola);
        arrive(consola, "hola\nchao");
        chosenFile = Optional.of(folder.resolve("pantalla.txt"));

        content.saveText();

        assertThat(Files.readString(folder.resolve("pantalla.txt"), StandardCharsets.UTF_8)).isEqualTo("hola\nchao\n");
    }

    @Test
    void oneWindowPerPartAndOpeningAgainBringsItToTheFront() {
        Component consola = terminal("consola", Map.of());

        open(consola);
        open(consola);

        assertThat(shown).hasSize(1);
        assertThat(shown.getFirst().broughtToFront).isEqualTo(1);
        assertThat(windows.isOpen(consola.id().value())).isTrue();
    }

    @Test
    void deletingThePartClosesItsWindowAndStopsItsFrames() {
        Component consola = terminal("consola", Map.of());
        Component other = terminal("otra", Map.of());
        open(consola);
        open(other);

        document.execute(new RemoveElementsCommand(List.of(consola.id())));

        assertThat(shown.get(0).closed).isTrue();
        assertThat(shown.get(1).closed).isFalse();
        assertThat(terminals.isRequested(consola.id().value())).isFalse();
        windows.dispose();
        assertThat(shown.get(1).closed).as("the document closes").isTrue();
        assertThat(terminals.isRequested(other.id().value())).isFalse();
    }

    @Test
    void closingByTheUserForgetsTheWindowAndClosingTheOpenersWindowClosesIt() {
        Component consola = terminal("consola", Map.of());
        Component other = terminal("otra", Map.of());
        open(consola);
        open(other);

        shown.get(0).closedByUser.run();
        assertThat(windows.isOpen(consola.id().value())).isFalse();
        windows.closeOpenedBy(owner);
        assertThat(shown.get(1).closed).isTrue();
    }

    @Test
    void theGeometryIsSavedOnCloseAndRestoredOnTheNextOpen() {
        Component consola = terminal("consola", Map.of());
        open(consola);
        assertThat(shown.getFirst().restoredBounds).isEmpty();
        shown.getFirst().bounds = new Rectangle(30, 40, 500, 300);

        shown.getFirst().closedByUser.run();
        open(consola);

        assertThat(shown.get(1).restoredBounds).contains(new Rectangle(30, 40, 500, 300));
    }

    @Test
    void renamingThePartRenamesItsWindow() {
        Component consola = terminal("consola", Map.of());
        TerminalWindowContent content = open(consola);

        document.execute(new SetLabelCommand(consola.id(), "tty"));

        assertThat(shown.getFirst().currentTitle).isEqualTo("tty");
        assertThat(content.barText()).startsWith("tty · ");
    }

    @Test
    void typingOnTheScreenGoesToTheFirstTecladoLinkedToThisTerminal() {
        Component consola = terminal("consola", Map.of());
        ComponentType keyboardType = document.registry().require(StandardComponentTypes.KEYBOARD);
        Component unlinked = keyboardType.newComponent(document.document().newComponentId(), new GridPoint(0, 40),
                Rotation.R0, Map.of(), "otro");
        Component linked = keyboardType.newComponent(document.document().newComponentId(), new GridPoint(0, 60),
                Rotation.R0, Map.of(KeyboardTypes.LINKED_TERMINAL, "consola"), "teclado");
        document.execute(new AddElementsCommand(List.of(unlinked, linked)));
        awaitSimulation(document);
        TerminalWindowContent content = open(consola);

        JComponent screen = content.screen().typingTarget();
        for (java.awt.event.KeyListener listener : screen.getKeyListeners()) {
            listener.keyTyped(new KeyEvent(screen, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a'));
        }

        waitUntil(() -> queueSize(linked) == 1);
        assertThat(queueSize(unlinked)).isZero();
        assertThat(content.typingHint()).isEmpty();
    }

    @Test
    void withoutALinkedTecladoTheScreenSaysHowToTypeIntoIt() {
        Component consola = terminal("consola", Map.of());

        TerminalWindowContent content = open(consola);

        assertThat(content.typingHint()).contains(
                "Para escribir aquí, enlaza un Teclado a este Terminal en su propiedad «Terminal enlazado».");
    }

    private int queueSize(Component keyboard) {
        awaitSimulation(document);
        return document.latestSnapshot().componentState(keyboard.id().value())
                instanceof VisibleState.KeyboardState state ? state.size() : -1;
    }
}
