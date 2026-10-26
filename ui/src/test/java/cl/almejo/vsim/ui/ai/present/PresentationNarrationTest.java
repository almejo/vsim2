package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JRootPane;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.ai.present.FakePresentationEvents.step;
import static org.assertj.core.api.Assertions.assertThat;

/** What the AI says in «Presentar» (IA.3 Task 7): a subtitle over the circuit and a line in the stage's card. */
class PresentationNarrationTest {

    private static final DocumentId THIS_CIRCUIT = new DocumentId("this");
    private static final TaskInfo TASK = new TaskInfo("t1", THIS_CIRCUIT, "Sumador de 4 bits");
    private static final List<TaskStage> STAGES = List.of(
            new TaskStage("Sumador de 1 bit", List.of("Pongo las XOR", "Pongo las AND")),
            new TaskStage("Sumador de 4 bits", List.of("Pongo cuatro sumadores")));
    private static final String LINE = "Ahora copio el circuito para hacer un componente nuevo";

    private final AppPreferences saved = AppPreferences.inMemory();
    private final FakePresentationEvents events = new FakePresentationEvents();
    private final RecordingControls controls = new RecordingControls();
    private final Deque<Runnable> editorQueue = new ArrayDeque<>();
    private OpenDocument document;
    private EditorView view;
    private PresentationExtension extension;
    private Runnable dispose;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        saved.setAnimateViewMoves(false);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(document, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), saved.layered(), new ComponentLibrary(), List.of()), NO_WINDOW);
        JRootPane root = new JRootPane();
        root.getContentPane().add(view);
        root.setSize(1200, 800);
        layOut(root);
        extension = new PresentationExtension(events, controls, any -> THIS_CIRCUIT, editorQueue::add);
        dispose = extension.install(view);
    }

    @AfterEach
    void close() {
        if (dispose != null) {
            dispose.run();
        }
        view.dispose();
        document.close();
    }

    private PresentationOverlays overlays() {
        return extension.overlaysOf(view).orElseThrow();
    }

    private PresentationFeatures features() {
        return extension.featuresOf(view).orElseThrow();
    }

    private void runEditorThread() {
        while (!editorQueue.isEmpty()) {
            editorQueue.poll().run();
        }
    }

    private void presentATask() {
        saved.setAiPresenting(true);
        events.start(TASK);
        events.plan(TASK, STAGES, 0, 0);
        runEditorThread();
    }

    private void say(int stageIndex, String text, boolean presenting) {
        events.narrate(new Narration(TASK.taskId(), stageIndex, text, presenting));
        runEditorThread();
    }

    @Test
    void aLineShowsAsASubtitleAndInTheCurrentStagesCardInGreyItalics() {
        presentATask();

        say(0, LINE, true);

        assertThat(overlays().caption().text()).contains(LINE);
        StageCard current = features().panel().cards().getFirst();
        StageCard.Line line = current.lines().stream().filter(candidate -> candidate.getText().contains(LINE))
                .findFirst().orElseThrow();
        assertThat(line.tone()).isEqualTo(StageCard.Tone.MUTED);
        assertThat(line.getFont().getStyle() & Font.ITALIC).isEqualTo(Font.ITALIC);
        assertThat(features().panel().cards().get(1).lineTexts()).noneMatch(text -> text.contains(LINE));
    }

    @Test
    void aNewLineReplacesTheShownOne() {
        presentATask();
        say(0, LINE, true);

        say(0, "Ahora conecto las salidas", true);

        assertThat(overlays().caption().text()).contains("Ahora conecto las salidas");
        assertThat(features().panel().cards().getFirst().lineTexts())
                .anyMatch(text -> text.contains("Ahora conecto las salidas"))
                .noneMatch(text -> text.contains(LINE));
    }

    @Test
    void theSubtitleStaysThroughTheStepItIntroducesAndGoesAtTheNextOne() {
        presentATask();
        say(0, LINE, true);

        events.propose(step(TASK.taskId(), 0, 0, "Pongo las XOR"));
        runEditorThread();
        assertThat(overlays().caption().text()).contains(LINE);
        events.apply(step(TASK.taskId(), 0, 0, "Pongo las XOR"));
        runEditorThread();
        assertThat(overlays().caption().text()).contains(LINE);

        events.apply(step(TASK.taskId(), 0, 1, "Pongo las AND"));
        runEditorThread();
        assertThat(overlays().caption().text()).isEmpty();
    }

    @Test
    void inWorkingModeThereIsNoSubtitle() {
        events.start(TASK);
        events.plan(TASK, STAGES, 0, 0);
        runEditorThread();

        say(0, LINE, false);

        assertThat(overlays().caption().text()).isEmpty();
        assertThat(features().isPanelShown()).isFalse();
    }

    @Test
    void theSubtitleGoesWhenTheTaskEndsOrStops() {
        presentATask();
        say(0, LINE, true);

        events.end(TASK);
        runEditorThread();

        assertThat(overlays().caption().text()).isEmpty();
    }

    @Test
    void theSubtitleIsNeverUnderTheFloatingPanelAndOnlyWhilePresenting() {
        presentATask();
        say(0, LINE, true);
        CircuitCanvas pane = view.canvas();
        assertThat(features().isPanelShown()).isTrue();
        Optional<Rectangle> panel = features().placement().coveredIn(pane);
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            Optional<NarrationCaption.Placement> placement = overlays().caption().placementIn(pane, graphics);
            assertThat(placement).isPresent();
            panel.ifPresent(covered -> assertThat(placement.get().band().intersects(covered)).isFalse());

            saved.setAiPresenting(false); // the user leaves «Presentar»: the subtitle goes with the panel
            runEditorThread();
            assertThat(overlays().caption().placementIn(pane, graphics)).isEmpty();
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void disposeTakesTheSubtitleAway() {
        presentATask();
        say(0, LINE, true);
        NarrationCaption caption = overlays().caption();

        dispose.run();
        dispose = null;

        assertThat(caption.text()).isEmpty();
        assertThat(events.presentationListeners).isEmpty();
    }
}
