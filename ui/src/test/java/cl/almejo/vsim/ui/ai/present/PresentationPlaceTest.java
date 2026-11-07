package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PlaceRef;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.ai.TaskPlaces;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round E (5q): the «Presentar» panel shows on the tab of the place its task works in (the tab the robot marks), moves
 * with it, falls back to the circuit's tab when that place has no open tab, and never changes the tab the user sees.
 */
class PresentationPlaceTest {

    private static final DocumentId THIS_CIRCUIT = new DocumentId("circuit-1");
    private static final TaskInfo TASK = new TaskInfo("t1", THIS_CIRCUIT, "Probar el inversor");
    private static final List<TaskStage> STAGES = List.of(new TaskStage("Leer", List.of("Leo el inversor")));
    private static final PlaceRef INVERTER_PIECE = new PlaceRef(ExampleDefinitions.INVERTER, Optional.of("c5"));
    private static final String MAIN_CARD = "main";
    private static final String INVERTER_CARD = "inverter";

    /** The robot's places, moved by hand. */
    private static final class FakeTaskPlaces implements TaskPlaces {
        private final Map<String, PlaceRef> places = new HashMap<>();
        private final List<Runnable> listeners = new ArrayList<>();

        void workIn(String taskId, PlaceRef place) {
            places.put(taskId, place);
            List.copyOf(listeners).forEach(Runnable::run);
        }

        void release(String taskId) {
            places.remove(taskId);
            List.copyOf(listeners).forEach(Runnable::run);
        }

        @Override
        public Optional<PlaceRef> placeOf(String taskId) {
            return Optional.ofNullable(places.get(taskId));
        }

        @Override
        public Runnable addListener(Runnable listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }
    }

    private final AppPreferences saved = AppPreferences.inMemory();
    private final FakePresentationEvents events = new FakePresentationEvents();
    private final RecordingControls controls = new RecordingControls();
    private final Deque<Runnable> editorQueue = new ArrayDeque<>();
    private final FakeTaskPlaces taskPlaces = new FakeTaskPlaces();
    private final List<Runnable> disposals = new ArrayList<>();
    private final List<EditorView> bodyViews = new ArrayList<>();
    private final List<OpenDocument> bodies = new ArrayList<>();
    private EditorFixture fixture;
    private OpenDocument circuit;
    private EditorView mainView;
    private EditorView inverterView;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private PresentationExtension extension;

    @BeforeEach
    void openTheCircuitAndItsInverterTab() {
        fixture = EditorFixture.create(saved.layered());
        circuit = fixture.openDocument();
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c5"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""))));
        mainView = fixture.view();
        inverterView = bodyView(INVERTER_PIECE.context());
        JRootPane root = new JRootPane();
        cards.add(mainView, MAIN_CARD);
        cards.add(inverterView, INVERTER_CARD);
        root.getContentPane().add(cards);
        root.setSize(1200, 800);
        layOut(root);
        // the AI knows the circuit by one id; a body place is the circuit's place, never a circuit of its own
        extension = new PresentationExtension(events, controls,
                document -> document == circuit ? THIS_CIRCUIT : new DocumentId("circuit-of-a-body"),
                editorQueue::add, System::nanoTime, taskPlaces);
        disposals.add(extension.install(mainView));
        disposals.add(extension.install(inverterView));
        saved.setAiPresenting(true);
    }

    @AfterEach
    void close() {
        disposals.forEach(Runnable::run);
        bodyViews.forEach(EditorView::dispose);
        bodies.forEach(OpenDocument::close);
        fixture.close();
    }

    private EditorView bodyView(Optional<String> context) {
        OpenDocument body = circuit.openBody(ExampleDefinitions.INVERTER, context);
        bodies.add(body);
        EditorView view = new EditorView(body, fixture.services(), NO_WINDOW);
        bodyViews.add(view);
        return view;
    }

    private void runEditorThread() {
        while (!editorQueue.isEmpty()) {
            editorQueue.poll().run();
        }
    }

    /** The user brings tab {@code card} to the front (the cards hear it on the event thread). */
    private void showTab(String card) throws Exception {
        cardLayout.show(cards, card);
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private void startTheTask() {
        events.start(TASK);
        events.plan(TASK, STAGES, 0, 0);
        runEditorThread();
    }

    private boolean shownOn(EditorView view) {
        return extension.featuresOf(view).orElseThrow().isPanelShown();
    }

    @Test
    void aTaskWorkingInAComponentShowsItsPanelOnThatComponentsTab() throws Exception {
        showTab(INVERTER_CARD);
        startTheTask();

        taskPlaces.workIn("t1", INVERTER_PIECE);

        assertThat(shownOn(inverterView)).isTrue();
        assertThat(extension.featuresOf(inverterView).orElseThrow().panel().headerText())
                .isEqualTo("IA · Probar el inversor");
        assertThat(shownOn(mainView)).as("not on the circuit's tab").isFalse();
    }

    @Test
    void thePanelMovesWithTheTaskAndNeverChangesTheTabTheUserSees() throws Exception {
        startTheTask();
        taskPlaces.workIn("t1", PlaceRef.main());
        assertThat(shownOn(mainView)).isTrue();

        taskPlaces.workIn("t1", INVERTER_PIECE);

        assertThat(shownOn(mainView)).as("it left the circuit's tab").isFalse();
        assertThat(shownOn(inverterView)).as("its tab is not in front").isFalse();
        assertThat(mainView.isVisible()).as("the user's tab stays in front").isTrue();
        assertThat(inverterView.isVisible()).isFalse();

        showTab(INVERTER_CARD);
        assertThat(shownOn(inverterView)).as("the user goes to the robot's tab").isTrue();

        taskPlaces.workIn("t1", PlaceRef.main());
        showTab(MAIN_CARD);
        assertThat(shownOn(mainView)).as("and back").isTrue();
        assertThat(shownOn(inverterView)).isFalse();
    }

    @Test
    void aPlaceWithoutAnOpenTabShowsThePanelOnTheCircuitsTab() {
        startTheTask();

        taskPlaces.workIn("t1", new PlaceRef(ExampleDefinitions.INVERTER, Optional.of("c9")));

        assertThat(shownOn(mainView)).isTrue();
        assertThat(shownOn(inverterView)).isFalse();
    }

    @Test
    void anEndedTaskKeepsItsPanelWhereItLastWorked() throws Exception {
        showTab(INVERTER_CARD);
        startTheTask();
        taskPlaces.workIn("t1", INVERTER_PIECE);

        taskPlaces.release("t1");
        events.end(TASK);
        runEditorThread();

        assertThat(shownOn(inverterView)).isTrue();
        assertThat(shownOn(mainView)).isFalse();
    }

    @Test
    void aTabOpenedDuringTheTaskTakesThePanelWithEverythingItSaidSoFar() throws Exception {
        startTheTask();
        taskPlaces.workIn("t1", new PlaceRef(ExampleDefinitions.INVERTER, Optional.empty()));
        assertThat(shownOn(inverterView)).as("its tab is not in front").isFalse();

        EditorView opened = bodyView(Optional.empty());
        cards.add(opened, "opened");
        disposals.add(0, extension.install(opened));
        showTab("opened");

        assertThat(shownOn(opened)).isTrue();
        assertThat(extension.featuresOf(opened).orElseThrow().panel().headerText())
                .isEqualTo("IA · Probar el inversor");
    }

    @Test
    void closingThePlacesTabGivesThePanelBackToTheCircuitsTab() throws Exception {
        startTheTask();
        taskPlaces.workIn("t1", INVERTER_PIECE);
        assertThat(shownOn(mainView)).isFalse();

        disposals.remove(1).run(); // the inverter's tab closes

        assertThat(shownOn(mainView)).isTrue();
    }
}
