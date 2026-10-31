package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
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

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
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
    private EditorView view;
    private Runnable dispose;

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
                () -> Optional.of("hola"), (parent, suggested) -> Optional.empty(), Runnable::run, Runnable::run,
                TerminalScreenRenderer.standard(), System::nanoTime);
        return new TerminalServices(windows, () -> ticker, tone -> { }, Runnable::run, Runnable::run);
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
}
