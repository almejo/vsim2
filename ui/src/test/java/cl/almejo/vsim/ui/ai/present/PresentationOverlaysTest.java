package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.ai.AiColors;
import cl.almejo.vsim.ui.ai.AiServerService;
import cl.almejo.vsim.ui.ai.EditorAutomationHost;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.theme.AppThemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The callout, the ghosts and the view travel of «Presentar» (spec §5) over a fake stream of presentation events:
 * where the callout goes after pan and zoom, in split views, with the change off screen, for a step that only
 * deletes and for a rotated part (Review Focus 4).
 */
class PresentationOverlaysTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private static final long FADE_NANOS = TimeUnit.MILLISECONDS.toNanos(StepCallout.FADE_MILLISECONDS);

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final EditorAutomationHost host = new EditorAutomationHost(List::of);
    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<PresentationListener> presentationListeners = new ArrayList<>();
    private final List<AiServerService.Listener> taskListeners = new ArrayList<>();
    private final List<Runnable> editorQueue = new ArrayList<>();
    private boolean queueEditorJobs;
    private OpenDocument document;
    private EditorView view;
    private PresentationOverlays overlays;
    private ManualFrameTicker ticker;
    private TaskInfo task;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setAnimateViewMoves(false);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        view = new EditorView(document, services, NO_WINDOW);
        view.canvas().setSize(400, 300);
        view.canvas().applyViewState(new ViewState(1, 0, 0)); // shows x -25..25, y -18.75..18.75
        ticker = new ManualFrameTicker().drive(view.canvas());
        overlays = PresentationOverlays.install(view,
                listener -> {
                    presentationListeners.add(listener);
                    return () -> presentationListeners.remove(listener);
                },
                listener -> {
                    taskListeners.add(listener);
                    return () -> taskListeners.remove(listener);
                },
                host::documentId,
                job -> {
                    if (queueEditorJobs) {
                        editorQueue.add(job);
                    } else {
                        job.run();
                    }
                },
                clock::get);
        task = new TaskInfo("task-1", host.documentId(document), "Sumador de 2 bits");
    }

    @AfterEach
    void close() {
        overlays.dispose();
        view.dispose();
        document.close();
    }

    private PresentationListener presentation() {
        assertThat(presentationListeners).hasSize(1);
        return presentationListeners.getFirst();
    }

    private void plan(TaskInfo planned) {
        presentation().planChanged(planned, List.of(
                new TaskStage("Medio sumador", List.of("Pongo las entradas", "Pongo la XOR", "Pongo la AND")),
                new TaskStage("Sumador completo", List.of())), 0, 1);
    }

    private Component newGate(int x, int y, Rotation rotation) {
        return document.registry().require(StandardComponentTypes.AND2)
                .newComponent(document.document().newComponentId(), new GridPoint(x, y), rotation, Map.of(), "");
    }

    private StepPreview preview(List<DocumentElement> ghosts, Set<ElementId> removed, Optional<Bounds> changed) {
        return new StepPreview("task-1", 0, 1, "Pongo la XOR", Optional.of("suma sin acarreo"), ghosts, removed,
                changed);
    }

    private StepPreview proposeGateAt(int x, int y) {
        Component gate = newGate(x, y, Rotation.R0);
        StepPreview preview = preview(List.of(gate), Set.of(),
                Optional.of(DrawnBounds.of(gate, document.registry())));
        plan(task);
        presentation().stepProposed(preview);
        return preview;
    }

    private Rectangle2DOnScreen onScreen(CircuitCanvas pane, Bounds bounds) {
        Point2D corner = pane.viewport().gridToScreen(bounds.x(), bounds.y());
        Point2D end = pane.viewport().gridToScreen(bounds.maximumX(), bounds.maximumY());
        return new Rectangle2DOnScreen(corner.getX(), corner.getY(), end.getX(), end.getY());
    }

    private record Rectangle2DOnScreen(double minimumX, double minimumY, double maximumX, double maximumY) {
    }

    private Optional<StepCallout.Placement> placementIn(CircuitCanvas pane) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scratch.createGraphics();
        try {
            return overlays.callout().placementIn(pane, graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage paint(CircuitCanvas pane) {
        BufferedImage image = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        pane.paint(graphics);
        graphics.dispose();
        return image;
    }

    @Test
    void aProposedStepShowsItsCalloutBesideTheChangeAndItsGhost() {
        StepPreview preview = proposeGateAt(-10, -2);

        StepCallout.Content content = overlays.callout().content().orElseThrow();
        assertThat(content.header()).isEqualTo("Paso 2 de 3 · propuesto");
        assertThat(content.text()).isEqualTo("Pongo la XOR");
        assertThat(content.reason()).contains("suma sin acarreo");
        assertThat(overlays.ghosts().shown()).contains(preview);
        StepCallout.Placement placement = placementIn(view.canvas()).orElseThrow();
        Rectangle2DOnScreen target = onScreen(view.canvas(), preview.changedBounds().orElseThrow());
        assertThat(placement.side()).isEqualTo(StepCallout.Side.RIGHT);
        assertThat(placement.box().x).isGreaterThan((int) target.maximumX());
        assertThat(placement.dot().getX()).isEqualTo(target.maximumX());
        assertThat(document.document().components()).isEmpty(); // a ghost only
    }

    @Test
    void theScreenShowsTheCalloutsLeaderInTheSchemesSelectionColour() {
        proposeGateAt(-10, -2);
        StepCallout.Placement placement = placementIn(view.canvas()).orElseThrow();

        BufferedImage screen = paint(view.canvas());

        assertThat(screen.getRGB((int) placement.dot().getX(), (int) placement.dot().getY()))
                .isEqualTo(ColorScheme.LIGHT.selection().getRGB());
    }

    @Test
    void theCalloutFollowsPanAndZoom() {
        StepPreview preview = proposeGateAt(-10, -2);
        StepCallout.Placement before = placementIn(view.canvas()).orElseThrow();

        view.canvas().applyViewState(new ViewState(2, -8, 0));

        StepCallout.Placement after = placementIn(view.canvas()).orElseThrow();
        Rectangle2DOnScreen target = onScreen(view.canvas(), preview.changedBounds().orElseThrow());
        assertThat(after.box()).isNotEqualTo(before.box());
        assertThat(after.dot().getX()).isBetween(target.minimumX(), target.maximumX());
        assertThat(after.dot().getY()).isBetween(target.minimumY(), target.maximumY());
    }

    @Test
    void aStepOnAnotherCircuitShowsNothingHere() {
        plan(new TaskInfo("task-1", new DocumentId("circuit-other"), "Otro"));
        Component gate = newGate(0, 0, Rotation.R0);

        presentation().stepProposed(preview(List.of(gate), Set.of(), Optional.empty()));

        assertThat(overlays.callout().content()).isEmpty();
        assertThat(overlays.ghosts().shown()).isEmpty();
    }

    @Test
    void theEventsArriveOffTheEditorThreadAndAreShownOnIt() {
        queueEditorJobs = true;
        proposeGateAt(0, 0);

        assertThat(overlays.callout().content()).isEmpty();
        editorQueue.forEach(Runnable::run);

        assertThat(overlays.callout().content()).isPresent();
    }

    @Test
    void anAppliedStepLosesItsGhostGlowsAndSaysItIsNoLongerProposed() {
        StepPreview proposed = proposeGateAt(0, 0);
        Component applied = (Component) proposed.ghostElements().getFirst();
        document.document().add(applied);

        presentation().stepApplied(proposed);

        assertThat(overlays.ghosts().shown()).isEmpty();
        assertThat(overlays.callout().content().orElseThrow().header()).isEqualTo("Paso 2 de 3");
        assertThat(overlays.glow().current().orElseThrow().wires()).contains(applied.id());
    }

    @Test
    void aDiscardedStepTakesItsCalloutAndGhostAway() {
        proposeGateAt(0, 0);

        presentation().stepDiscarded("task-1", StepStatus.STALE);

        assertThat(overlays.callout().content()).isEmpty();
        assertThat(overlays.ghosts().shown()).isEmpty();
    }

    @Test
    void theNextStepReplacesTheCallout() {
        proposeGateAt(0, 0);
        Component gate = newGate(5, 5, Rotation.R0);

        presentation().stepProposed(new StepPreview("task-1", 0, 2, "Pongo la AND", Optional.empty(),
                List.of(gate), Set.of(), Optional.empty()));

        StepCallout.Content content = overlays.callout().content().orElseThrow();
        assertThat(content.header()).isEqualTo("Paso 3 de 3 · propuesto");
        assertThat(content.text()).isEqualTo("Pongo la AND");
        assertThat(content.reason()).isEmpty();
    }

    @Test
    void whenTheTaskEndsOrStopsTheCalloutGoes() {
        proposeGateAt(0, 0);

        taskListeners.forEach(listener -> listener.taskEnded(task, Set.of()));

        assertThat(overlays.callout().content()).isEmpty();
        assertThat(overlays.ghosts().shown()).isEmpty();

        proposeGateAt(0, 0);
        taskListeners.forEach(listener -> listener.taskStopped(task));

        assertThat(overlays.callout().content()).isEmpty();
    }

    @Test
    void aStepOnlyDeletingShowsTheDoomedPartsDimmedAndPointsAtThem() {
        Component doomed = place(document, StandardComponentTypes.NOT, -5, -2, Map.of());
        plan(task);
        BufferedImage before = paint(view.canvas());

        overlays.ghosts().show(preview(List.of(), Set.of(doomed.id()), Optional.empty()));
        BufferedImage dimmed = paint(view.canvas());

        Bounds part = DrawnBounds.of(doomed, document.registry());
        Rectangle2DOnScreen area = onScreen(view.canvas(), part);
        int changedInside = 0;
        for (int x = (int) area.minimumX(); x < area.maximumX(); x++) {
            for (int y = (int) area.minimumY(); y < area.maximumY(); y++) {
                if (before.getRGB(x, y) != dimmed.getRGB(x, y)) {
                    changedInside++;
                }
            }
        }
        assertThat(changedInside).isPositive();
        assertThat(dimmed.getRGB(380, 280)).isEqualTo(before.getRGB(380, 280)); // nothing else
        assertThat(document.document().components()).containsExactly(doomed); // still there

        presentation().stepProposed(preview(List.of(), Set.of(doomed.id()), Optional.empty()));

        assertThat(overlays.callout().target()).contains(part);
        assertThat(placementIn(view.canvas())).isPresent();
    }

    @Test
    void aRotatedPartIsPointedAtWhereItIsDrawn() {
        Component rotated = newGate(-4, -4, Rotation.R90);
        plan(task);

        presentation().stepProposed(preview(List.of(rotated), Set.of(), Optional.empty()));

        Bounds drawn = DrawnBounds.of(rotated, document.registry());
        assertThat(drawn).isNotEqualTo(DrawnBounds.of(rotated.withRotation(Rotation.R0), document.registry()));
        assertThat(overlays.callout().target()).contains(drawn);
    }

    @Test
    void anOffScreenChangeTakesTheActivePaneThereAndAVisibleOneDoesNot() {
        proposeGateAt(-10, -2);
        assertThat(view.canvas().viewState()).isEqualTo(new ViewState(1, 0, 0));

        StepPreview far = proposeGateAt(200, 100);

        Bounds target = far.changedBounds().orElseThrow();
        assertThat(view.canvas().shows(target)).isTrue();
        assertThat(placementIn(view.canvas())).isPresent();
    }

    @Test
    void withMotionTheTravelIsSmoothAndWithReducedMotionItJumps() {
        preferences.setAnimateViewMoves(true);
        StepPreview far = proposeGateAt(200, 100);

        assertThat(view.canvas().isTravelling()).isTrue();
        ticker.finish();
        assertThat(view.canvas().shows(far.changedBounds().orElseThrow())).isTrue();

        preferences.setAnimateViewMoves(false);
        StepPreview back = proposeGateAt(-300, 0);

        assertThat(view.canvas().isTravelling()).isFalse();
        assertThat(view.canvas().shows(back.changedBounds().orElseThrow())).isTrue();
    }

    @Test
    void inASplitViewTheCalloutShowsInEveryPaneThatShowsTheChangeAndNoPaneTravels() {
        view.canvasPanes().split(CanvasPanes.Orientation.HORIZONTAL, 0.5);
        List<CircuitCanvas> panes = view.panes();
        assertThat(panes).hasSize(2);
        for (CircuitCanvas pane : panes) {
            pane.setSize(400, 300);
        }
        CircuitCanvas active = view.canvas();
        CircuitCanvas other = panes.stream().filter(pane -> pane != active).findFirst().orElseThrow();
        active.applyViewState(new ViewState(1, 0, 0));
        other.applyViewState(new ViewState(1, 200, 100));

        StepPreview there = proposeGateAt(200, 100); // only the other pane shows it

        assertThat(active.viewState()).isEqualTo(new ViewState(1, 0, 0));
        assertThat(placementIn(other)).isPresent();
        assertThat(placementIn(active)).isEmpty();
        assertThat(other.shows(there.changedBounds().orElseThrow())).isTrue();

        other.applyViewState(new ViewState(1, 0, 0));
        proposeGateAt(-10, -2); // both show it

        assertThat(placementIn(other)).isPresent();
        assertThat(placementIn(active)).isPresent();
    }

    @Test
    void aPaneOpenedLaterPaintsTheCalloutAndDisposingTakesEveryOverlayBack() {
        int overlaysBefore = view.canvas().overlayCount();
        overlays.dispose();
        assertThat(view.canvas().overlayCount()).isLessThan(overlaysBefore);
        assertThat(presentationListeners).isEmpty();
        assertThat(taskListeners).isEmpty();
        int bare = view.canvas().overlayCount();

        overlays = PresentationOverlays.install(view,
                listener -> {
                    presentationListeners.add(listener);
                    return () -> presentationListeners.remove(listener);
                },
                listener -> {
                    taskListeners.add(listener);
                    return () -> taskListeners.remove(listener);
                },
                host::documentId, Runnable::run, clock::get);
        view.canvasPanes().split(CanvasPanes.Orientation.VERTICAL, 0.5);
        for (CircuitCanvas pane : view.panes()) {
            assertThat(pane.overlayCount()).as("pane").isEqualTo(overlaysBefore);
        }
        PresentationListener stale = presentationListeners.getFirst();

        overlays.dispose();

        for (CircuitCanvas pane : view.panes()) {
            assertThat(pane.overlayCount()).isEqualTo(bare);
        }
        stale.stepProposed(preview(List.of(newGate(0, 0, Rotation.R0)), Set.of(), Optional.empty()));
        assertThat(overlays.callout().content()).isEmpty(); // nothing after dispose
    }

    @Test
    void withMotionTheCalloutFadesInAndOutAndWithReducedMotionItDoesNot() {
        preferences.setAnimateViewMoves(true);
        proposeGateAt(0, 0);

        assertThat(overlays.callout().alpha()).isZero();
        assertThat(overlays.callout().isTimerRunning()).isTrue();
        clock.addAndGet(FADE_NANOS / 2);
        assertThat(overlays.callout().alpha()).isBetween(0.4, 0.6);
        clock.addAndGet(FADE_NANOS);
        overlays.callout().tick();
        assertThat(overlays.callout().alpha()).isEqualTo(1.0);
        assertThat(overlays.callout().isTimerRunning()).isFalse();

        presentation().stepDiscarded("task-1", StepStatus.STOPPED);
        assertThat(overlays.callout().alpha()).isEqualTo(1.0); // fading out
        clock.addAndGet(FADE_NANOS + 1);
        overlays.callout().tick();
        assertThat(overlays.callout().content()).isEmpty();
        assertThat(overlays.callout().isTimerRunning()).isFalse();

        preferences.setAnimateViewMoves(false);
        proposeGateAt(0, 0);
        assertThat(overlays.callout().alpha()).isEqualTo(1.0);
        assertThat(overlays.callout().isTimerRunning()).isFalse();
        presentation().stepDiscarded("task-1", StepStatus.STOPPED);
        assertThat(overlays.callout().content()).isEmpty();
    }

    @Test
    void theStageOriginIsReachedUnlessAlreadyOnScreen() {
        overlays.travelToStageOrigin(new GridPoint(-20, -15));
        assertThat(view.canvas().viewState()).isEqualTo(new ViewState(1, 0, 0));

        overlays.travelToStageOrigin(new GridPoint(120, 0));

        assertThat(view.canvas().shows(PresentationTravel.stageArea(new GridPoint(120, 0)))).isTrue();
    }

    @Test
    void theCalloutReadsOnEveryTheme() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                assertThat(ReadableColors.contrast(StepCallout.textColor(), StepCallout.background()))
                        .as("text on " + theme.getName())
                        .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
                assertThat(ReadableColors.contrast(StepCallout.mutedColor(), StepCallout.background()))
                        .as("reason on " + theme.getName())
                        .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
                assertThat(StepCallout.mutedColor()).isEqualTo(AiColors.muted());
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void aStepWithoutAnywhereToPointShowsTheCalloutInTheActivePaneCorner() {
        plan(task);

        presentation().stepProposed(preview(List.of(), Set.of(), Optional.empty()));

        StepCallout.Placement placement = placementIn(view.canvas()).orElseThrow();
        assertThat(placement.side()).isEqualTo(StepCallout.Side.CORNER);
        Rectangle box = placement.box();
        assertThat(box.x).isGreaterThanOrEqualTo(0);
        assertThat(box.y).isGreaterThanOrEqualTo(0);
    }
}
