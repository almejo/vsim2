package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.SubcircuitTypes;
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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review focus 4 (spec §10.7): Terminal windows are kept by (circuit, component, instance, part): the same id on the
 * circuit and inside a component opens two windows, a second tab of the same instance brings the open one to the
 * front, a tab that switches to another instance closes the windows of the one it left, and each place remembers its
 * own window bounds.
 */
class TerminalKeysInsideTest {

    private static final ElementId TERMINAL = new ElementId("c3");

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> shownTitles = new ArrayList<>();
    private final List<Boolean> closed = new ArrayList<>();
    private final List<Integer> broughtToFront = new ArrayList<>();
    private final TerminalExtension extension = new TerminalExtension(services());
    private final List<Runnable> disposers = new ArrayList<>();
    private final List<EditorView> views = new ArrayList<>();
    private final List<OpenDocument> bodies = new ArrayList<>();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        Component inner = StandardComponentTypes.registry().require(StandardComponentTypes.TERMINAL)
                .newComponent(TERMINAL, new GridPoint(0, 0), Rotation.R0, Map.of(), "pantalla");
        circuit.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition("consola", "Consola", "",
                new GridSize(4, 4), List.of(), new CircuitFragment(List.of(inner), List.of(), List.of())))));
        place("c5", SubcircuitTypes.typeIdOf("consola"), 0, 0);
        place("c9", SubcircuitTypes.typeIdOf("consola"), 0, 40);
        circuit.execute(new AddElementsCommand(List.of(StandardComponentTypes.registry()
                .require(StandardComponentTypes.TERMINAL).newComponent(TERMINAL, new GridPoint(80, 0), Rotation.R0,
                        Map.of(), "afuera"))));
    }

    @AfterEach
    void closeAll() {
        disposers.reversed().forEach(Runnable::run);
        views.forEach(EditorView::dispose);
        bodies.forEach(OpenDocument::close);
        circuit.close();
    }

    @Test
    void sameIdInTwoPlacesOpensTwoWindows() {
        EditorView main = view(circuit);
        EditorView inside = view(body(Optional.of("c5")));

        open(main);
        open(inside);

        assertThat(shownTitles).containsExactly("afuera", "pantalla");
        assertThat(closed).containsExactly(false, false);
    }

    @Test
    void aSecondTabOfTheSameInstanceBringsItsWindowToTheFront() {
        EditorView first = view(body(Optional.of("c5")));
        EditorView second = view(body(Optional.of("c5")));
        EditorView other = view(body(Optional.of("c9")));

        open(first);
        open(second);
        open(other);

        assertThat(shownTitles).containsExactly("pantalla", "pantalla");
        assertThat(broughtToFront).containsExactly(0);
    }

    @Test
    void switchingTheTabToAnotherInstanceClosesTheWindowsOfTheOneItLeft() {
        OpenDocument body = body(Optional.of("c5"));
        EditorView inside = view(body);
        open(inside);

        body.setContextPath(Optional.of("c9"));

        assertThat(closed).containsExactly(true);
        open(inside);
        assertThat(shownTitles).hasSize(2);
    }

    @Test
    void eachPlaceRemembersItsOwnWindowBounds() {
        TerminalWindowPreferences remembered = new TerminalWindowPreferences(preferences.terminalWindowsNode());
        OpenDocument body = body(Optional.of("c5"));

        remembered.saveBounds(TerminalWindows.storageKey(body), TERMINAL.value(), new Rectangle(1, 2, 3, 4));

        assertThat(TerminalWindows.storageKey(body)).isNotEqualTo(TerminalWindows.storageKey(circuit));
        assertThat(remembered.bounds(TerminalWindows.storageKey(circuit), TERMINAL.value())).isEmpty();
        assertThat(remembered.bounds(TerminalWindows.storageKey(body), TERMINAL.value()))
                .contains(new Rectangle(1, 2, 3, 4));
    }

    /**
     * A component tab asks for its Terminal by its id there («tty»); the simulation answers under the instance's path
     * («c5/tty»), and the frames still reach the window.
     */
    @Test
    void aTerminalInsideReceivesTheFramesOfItsInstance() {
        FakeTerminals terminals = new FakeTerminals();
        FakeTerminals.FakeTerminal terminal = terminals.add("c5/tty", 4, 10);
        TerminalFrameSource underInstance = new TerminalFrameSource() {
            @Override
            public void request(cl.almejo.vsim.core.simulation.TerminalRequest request) {
                terminals.request(new cl.almejo.vsim.core.simulation.TerminalRequest("c5/" + request.componentId(),
                        request.historyLines(), request.bytesSince()));
            }

            @Override
            public void stopRequest(String componentId) {
                terminals.stopRequest("c5/" + componentId);
            }

            @Override
            public Optional<cl.almejo.vsim.core.simulation.TerminalWindowFrame> latest(String componentId) {
                return terminals.latest("c5/" + componentId);
            }

            @Override
            public void acknowledge(String componentId, long owner, long sequence) {
                terminals.acknowledge("c5/" + componentId, owner, sequence);
            }
        };
        TerminalFeeds feeds = new TerminalFeeds(underInstance);
        List<TerminalUpdate> updates = new ArrayList<>();
        feeds.subscribe("tty", 0, updates::add);
        terminal.write("ab");
        terminals.publish("c5/tty");

        feeds.poll();

        assertThat(updates).hasSize(1);
    }

    private void open(EditorView view) {
        view.editTerminal(view.openDocument().document().component(TERMINAL).orElseThrow());
    }

    private EditorView view(OpenDocument place) {
        EditorServices editorServices = new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), preferences, new ComponentLibrary(), List.of());
        EditorView view = new EditorView(place, editorServices, NO_WINDOW);
        views.add(view);
        disposers.add(extension.install(view));
        return view;
    }

    private OpenDocument body(Optional<String> context) {
        OpenDocument body = circuit.openBody("consola", context);
        bodies.add(body);
        return body;
    }

    private void place(String id, String typeId, int x, int y) {
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId(id), typeId, new GridPoint(x, y),
                Rotation.R0, Map.of(), ""))));
    }

    private TerminalServices services() {
        ManualFrameTicker ticker = new ManualFrameTicker();
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
                    broughtToFront.add(index);
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
        return new TerminalServices(windows, () -> ticker, tone -> { }, Runnable::run, Runnable::run,
                (owner, name, file) -> false);
    }
}
