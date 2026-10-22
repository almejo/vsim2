package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.ProposedPreference;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The status line of a window while the AI works on its circuit (spec §4, §4.1, §8). */
class AiStatusFeaturesTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private final AppPreferences saved = AppPreferences.inMemory();
    private final AppPreferences windowPreferences = saved.layered();
    private final FakeAiTasks tasks = new FakeAiTasks();
    private final AiServerService service = new AiServerService(saved, new AutomationApi(new NoDocuments()), tasks,
            Runnable::run, Runnable::run);
    private final PreferenceProposals proposals = new PreferenceProposals(saved);
    private final EditorAutomationHost host = new EditorAutomationHost(List::of);
    private final AtomicLong clock = new AtomicLong(1_000);
    private OpenDocument document;
    private EditorView view;
    private AiStatusFeatures features;
    private DocumentId thisCircuit;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                windowPreferences, new ComponentLibrary(), List.of());
        view = new EditorView(document, services, NO_WINDOW);
        features = new AiStatusFeatures(view, service, host::documentId, proposals, clock::get);
        thisCircuit = host.documentId(document);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        document.close();
    }

    /** IA.3 Task 7: in working mode the AI's lines go only to the strip's tooltip, the last few, as plain text. */
    @Test
    void inWorkingModeWhatTheAiSaysGoesToTheStripsTooltipOnly() {
        tasks.start(FakeAiTasks.task("task-1", thisCircuit, "Flip-flop D"));
        assertThat(features.text().getToolTipText()).isNull();

        tasks.narrate(new Narration("task-1", 0, "Pongo <b>dos</b> NAND & las cruzo", false));
        for (int line = 2; line <= AiStatusFeatures.NARRATIONS_KEPT + 1; line++) {
            tasks.narrate(new Narration("task-1", 0, "Línea " + line, false));
        }
        tasks.narrate(new Narration("task-9", 0, "De otra tarea", false));

        String tooltip = features.text().getToolTipText();
        assertThat(features.text().getText()).isEqualTo("IA trabajando: Flip-flop D…");
        assertThat(tooltip).startsWith("<html>").contains("Lo que dijo la IA:")
                .contains("Línea " + (AiStatusFeatures.NARRATIONS_KEPT + 1))
                .doesNotContain("dos", "De otra tarea", "<b>");
        assertThat(features.narrations()).hasSize(AiStatusFeatures.NARRATIONS_KEPT);

        tasks.start(FakeAiTasks.task("task-2", thisCircuit, "Otra"));
        assertThat(features.text().getToolTipText()).isNull();
    }

    @Test
    void theTooltipEscapesWhatTheAiSays() {
        tasks.start(FakeAiTasks.task("task-1", thisCircuit, "Flip-flop D"));

        tasks.narrate(new Narration("task-1", 0, "Pongo <b>dos</b> NAND & las cruzo", false));

        assertThat(features.text().getToolTipText()).contains("Pongo &lt;b&gt;dos&lt;/b&gt; NAND &amp; las cruzo")
                .doesNotContain("<b>");
    }

    @Test
    void nothingShowsUntilTheAiWorksOnThisCircuit() {
        assertThat(features.strip().isVisible()).isFalse();

        tasks.start(FakeAiTasks.task("task-9", new DocumentId("circuit-other"), "Otro circuito"));

        assertThat(features.strip().isVisible()).isFalse();
    }

    @Test
    void whileATaskRunsTheStatusSaysSoWithDetenerAndDeshacer() {
        tasks.start(FakeAiTasks.task("task-1", thisCircuit, "Flip-flop D"));

        assertThat(features.strip().isVisible()).isTrue();
        assertThat(features.text().getText()).isEqualTo("IA trabajando: Flip-flop D…");
        assertThat(features.stopButton().isVisible()).isTrue();
        assertThat(features.stopButton().getText()).isEqualTo("Detener");
        assertThat(features.undoButton().getText()).isEqualTo("Deshacer lo que hizo la IA");
        assertThat(features.closeButton().isVisible()).isFalse();
        assertThat(Arrays.asList(view.statusBar().getComponents())).contains(features.strip());
    }

    @Test
    void theStripsButtonsCarryTheAiIconsAndCloseIsTheCross() {
        assertThat(features.stopButton().getIcon()).isSameAs(AiIcons.icon(AiIcons.STOP));
        assertThat(features.showButton().getIcon()).isSameAs(AiIcons.icon(AiIcons.SHOW));
        assertThat(features.undoButton().getIcon()).isSameAs(AiIcons.icon(AiIcons.UNDO));
        assertThat(features.closeButton().getIcon()).isSameAs(AiIcons.icon(AiIcons.CLOSE));
        assertThat(features.closeButton().getToolTipText()).isEqualTo("Cerrar");
    }

    @Test
    void detenerStopsTheTaskAndLeavesTheAppliedEditsAlone() {
        place(document, StandardComponentTypes.NOT, 0, 0, Map.of());
        long changes = document.changeCount();
        TaskInfo task = FakeAiTasks.task("task-1", thisCircuit, "Sumador");
        tasks.start(task);

        features.stopButton().doClick();

        assertThat(tasks.stopped).containsExactly("task-1");
        assertThat(document.changeCount()).isEqualTo(changes);
        assertThat(document.document().components()).hasSize(1);

        tasks.stop(task);

        assertThat(features.text().getText()).isEqualTo("Detuviste a la IA: Sumador");
        assertThat(features.stopButton().isVisible()).isFalse();
        assertThat(features.undoButton().isVisible()).isTrue();
    }

    /** IA.3: a task VSim stopped because the AI went silent says so, not «Detuviste a la IA». */
    @Test
    void aTaskStoppedBecauseTheAiWentSilentSaysSo() {
        TaskInfo task = FakeAiTasks.task("task-1", thisCircuit, "Sumador");
        tasks.start(task);

        tasks.stop(task, cl.almejo.vsim.automation.TaskStopReason.AI_SILENT);

        assertThat(features.text().getText()).isEqualTo("La IA dejó de responder: se detuvo la tarea «Sumador»");
        assertThat(features.stopButton().isVisible()).isFalse();
        assertThat(features.undoButton().isVisible()).isTrue();
    }

    @Test
    void whenTheTaskEndsWhatItChangedGlowsAndTheOverrideIsDropped() {
        Component part = place(document, StandardComponentTypes.NOT, 0, 0, Map.of());
        SettingsOverride override = SettingsOverride.of(windowPreferences).orElseThrow();
        TaskInfo task = FakeAiTasks.task("task-1", thisCircuit, "Latch");
        tasks.start(task);
        override.set(EditorSetting.SHOW_GUIDES, "false");

        tasks.end(task, Set.of(part.id().value()));

        assertThat(features.text().getText()).isEqualTo("La IA terminó: Latch");
        assertThat(features.glow().current()).isPresent();
        assertThat(features.glow().current().orElseThrow().wires()).containsExactly(new ElementId(part.id().value()));
        assertThat(override.isEmpty()).isTrue();
        assertThat(windowPreferences.showGuides()).isTrue();

        features.closeButton().doClick();
        assertThat(features.strip().isVisible()).isFalse();
    }

    @Test
    void whenTheTaskEndsOffScreenTheViewTravelsToWhatItChangedAndVerLoQueHizoIsOffered() {
        windowPreferences.setAnimateViewMoves(true);
        Component far = place(document, StandardComponentTypes.NOT, 300, 200, Map.of());
        view.canvas().setSize(400, 300);
        ManualFrameTicker ticker = new ManualFrameTicker().drive(view.canvas());
        TaskInfo task = FakeAiTasks.task("task-1", thisCircuit, "Latch");
        tasks.start(task);
        assertThat(view.canvas().isTravelling()).isFalse();

        tasks.end(task, Set.of(far.id().value()));

        assertThat(view.canvas().isTravelling()).isTrue();
        ticker.finish();
        assertThat(view.canvas().shows(DrawnBounds.of(far, document.registry()))).isTrue();
        assertThat(features.showButton().isVisible()).isTrue();
        assertThat(features.showButton().getText()).isEqualTo("Ver lo que hizo");
    }

    @Test
    void whenWhatTheTaskChangedIsAlreadyOnScreenTheViewStaysStill() {
        windowPreferences.setAnimateViewMoves(true);
        Component near = place(document, StandardComponentTypes.NOT, 4, 4, Map.of());
        view.canvas().setSize(400, 300);
        new ManualFrameTicker().drive(view.canvas());
        ViewState before = view.canvas().viewState();
        TaskInfo task = FakeAiTasks.task("task-1", thisCircuit, "Latch");
        tasks.start(task);

        tasks.end(task, Set.of(near.id().value()));

        assertThat(view.canvas().isTravelling()).isFalse();
        assertThat(view.canvas().viewState()).isEqualTo(before);
        assertThat(features.showButton().isVisible()).isTrue();
    }

    @Test
    void withReducedMotionTheViewJumpsToWhatTheTaskChanged() {
        windowPreferences.setAnimateViewMoves(false);
        Component far = place(document, StandardComponentTypes.NOT, 300, 200, Map.of());
        view.canvas().setSize(400, 300);
        TaskInfo task = FakeAiTasks.task("task-1", thisCircuit, "Latch");
        tasks.start(task);

        tasks.end(task, Set.of(far.id().value()));

        assertThat(view.canvas().isTravelling()).isFalse();
        assertThat(view.canvas().shows(DrawnBounds.of(far, document.registry()))).isTrue();
    }

    @Test
    void verLoQueHizoTravelsThereAgainAfterTheUserLookedElsewhere() {
        windowPreferences.setAnimateViewMoves(false);
        Component far = place(document, StandardComponentTypes.NOT, 300, 200, Map.of());
        view.canvas().setSize(400, 300);
        TaskInfo task = FakeAiTasks.task("task-1", thisCircuit, "Latch");
        tasks.start(task);
        tasks.end(task, Set.of(far.id().value()));
        view.canvas().applyViewState(new ViewState(1, 0, 0));
        assertThat(view.canvas().shows(DrawnBounds.of(far, document.registry()))).isFalse();

        features.showButton().doClick();

        assertThat(view.canvas().shows(DrawnBounds.of(far, document.registry()))).isTrue();
    }

    @Test
    void verLoQueHizoIsNotOfferedWhileTheTaskRunsNorWhenItChangedNothingStillThere() {
        TaskInfo task = FakeAiTasks.task("task-1", thisCircuit, "Latch");
        tasks.start(task);
        assertThat(features.showButton().isVisible()).isFalse();

        tasks.end(task, Set.of("gone"));

        assertThat(features.showButton().isVisible()).isFalse();
    }

    @Test
    void deshacerTellsHowManyStepsWereUndoneAndWhereItStopped() {
        TaskInfo task = FakeAiTasks.task("task-1", thisCircuit, "Latch");
        tasks.start(task);
        tasks.end(task, Set.of());
        tasks.nextOutcome = new UndoOutcome(3, true);

        features.undoButton().doClick();

        assertThat(tasks.undone).containsExactly("task-1");
        assertThat(view.statusBar().text()).contains("Me detuve en un cambio tuyo; pasos de la IA deshechos: 3");
        assertThat(features.strip().isVisible()).isFalse();
    }

    @Test
    void aProposalIsAnnouncedInTheStatusBar() {
        proposals.propose(ProposedPreference.THEME, "DARK", "");

        assertThat(view.statusBar().text())
                .contains("La IA propone un cambio: míralo en Herramientas ▸ IA (MCP)…");
    }

    @Test
    void aTaskTitleIsShownAsPlainText() {
        tasks.start(FakeAiTasks.task("task-1", thisCircuit, "<html><b>X</b>"));

        assertThat(features.text().getClientProperty("html.disable")).isEqualTo(true);
    }

    @Test
    void disposingTakesBackTheStripAndEveryListener() {
        int serviceListeners = service.listenerCount();
        int proposalListeners = proposals.listenerCount();
        AiStatusFeatures another = new AiStatusFeatures(view, service, host::documentId, proposals, clock::get);

        another.dispose();

        assertThat(service.listenerCount()).isEqualTo(serviceListeners);
        assertThat(proposals.listenerCount()).isEqualTo(proposalListeners);
        assertThat(Arrays.asList(view.statusBar().getComponents())).doesNotContain(another.strip());
    }

    private static final class NoDocuments implements AutomationHost {
        @Override
        public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
            return CompletableFuture.completedFuture(work.apply(List::of));
        }
    }
}
