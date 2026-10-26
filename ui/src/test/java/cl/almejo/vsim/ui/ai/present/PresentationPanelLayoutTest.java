package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.ai.present.FakePresentationEvents.step;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The floating panel's layout in a real window layout (the user's first look: the header alone on the left, the rest
 * cut off on the right, blank buttons, Next and Stop still there at the end), and moving it by its header.
 */
class PresentationPanelLayoutTest {

    private static final DocumentId THIS_CIRCUIT = new DocumentId("this");
    private static final TaskInfo TASK = new TaskInfo("t1", THIS_CIRCUIT,
            "Sumador de 4 bits paso a paso con acarreo en cascada y nota de prueba");
    private static final List<TaskStage> STAGES = List.of(
            new TaskStage("Semisumador", List.of(
                    "Coloco las entradas A y B a la izquierda, separadas, con sus etiquetas",
                    "Agrego la XOR que calcula la suma sin acarreo", "Agrego la AND que calcula el acarreo",
                    "Agrego las salidas S y C", "Creo el componente «Semisumador» sin reemplazar la selección")),
            new TaskStage("Sumador completo de 1 bit con dos semisumadores y una OR", List.of()),
            new TaskStage("Sumador de 4 bits", List.of()),
            new TaskStage("Nota de prueba", List.of()));

    private final AppPreferences saved = AppPreferences.inMemory();
    private final FakePresentationEvents events = new FakePresentationEvents();
    private final RecordingControls controls = new RecordingControls();
    private final Deque<Runnable> editorQueue = new ArrayDeque<>();
    private OpenDocument document;
    private EditorFixture fixture;
    private EditorView view;
    private JRootPane root;
    private PresentationExtension extension;
    private Runnable dispose;

    @BeforeEach
    void create() {
        Messages.initialize(java.util.Locale.ENGLISH);
        fixture = EditorFixture.create(saved.layered());
        document = fixture.openDocument();
        view = fixture.view();
        root = new JRootPane();
        root.getContentPane().add(view);
        resize(1200, 800);
        extension = new PresentationExtension(events, controls, any -> THIS_CIRCUIT, editorQueue::add);
        dispose = extension.install(view);
        saved.setAiPresenting(true);
        events.start(TASK);
        events.plan(TASK, STAGES, 0, 0);
        events.propose(step("t1", 0, 0, STAGES.getFirst().steps().getFirst()));
        runEditorThread();
    }

    @AfterEach
    void close() {
        if (dispose != null) {
            dispose.run();
        }
        fixture.close();
    }

    private PresentationFeatures features() {
        return extension.featuresOf(view).orElseThrow();
    }

    private PresentationPanel panel() {
        return features().panel();
    }

    private void runEditorThread() {
        while (!editorQueue.isEmpty()) {
            editorQueue.poll().run();
        }
        features().placement().place();
        layOut(panel());
    }

    private void resize(int width, int height) {
        root.setSize(width, height);
        layOut(root);
        if (extension != null) {
            features().placement().place();
            layOut(panel());
        }
    }

    private Rectangle paneInLayered() {
        CircuitCanvas pane = view.canvas();
        return SwingUtilities.convertRectangle(pane.getParent(), pane.getBounds(), root.getLayeredPane());
    }

    @Test
    void thePanelAndEverythingInItStayInsideTheActivePaneWhateverItsSize() {
        for (Dimension size : List.of(new Dimension(1200, 800), new Dimension(900, 600), new Dimension(560, 420))) {
            resize(size.width, size.height);
            Rectangle pane = paneInLayered();
            Rectangle bounds = panel().getBounds();

            assertThat(pane.contains(bounds)).as("panel %s in pane %s", bounds, pane).isTrue();
            assertThat(bounds.width).isBetween(200, PresentationPanel.PREFERRED_WIDTH);
            for (Component child : visibleDescendants(panel())) {
                Rectangle inPanel = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), panel());
                assertThat(new Rectangle(0, 0, bounds.width, bounds.height).contains(inPanel))
                        .as("%s at %s in a panel %dx%d", child.getClass().getSimpleName(), inPanel, bounds.width,
                                bounds.height)
                        .isTrue();
            }
        }
    }

    @Test
    void theHeaderIsOneRowAtTheTopAndThePaceSwitchSitsBelowIt() {
        JComponent bar = panel().headerBar();
        List<JComponent> row = List.of(panel().grip(), panel().header(), panel().stageLabel(), panel().menuButton(),
                panel().foldButton());
        for (JComponent part : row) {
            Rectangle inPanel = SwingUtilities.convertRectangle(part.getParent(), part.getBounds(), panel());
            assertThat(inPanel.y).isLessThan(bar.getY() + bar.getHeight());
            assertThat(inPanel.y + inPanel.height).isLessThanOrEqualTo(bar.getY() + bar.getHeight());
        }
        Rectangle grip = SwingUtilities.convertRectangle(panel().grip().getParent(), panel().grip().getBounds(),
                panel());
        Rectangle fold = SwingUtilities.convertRectangle(panel().foldButton().getParent(),
                panel().foldButton().getBounds(), panel());
        assertThat(grip.x).isLessThan(fold.x);
        Rectangle pace = SwingUtilities.convertRectangle(panel().continuousButton().getParent(),
                panel().continuousButton().getBounds(), panel());
        assertThat(pace.y).isGreaterThanOrEqualTo(bar.getY() + bar.getHeight());
        assertThat(bar.getY()).isLessThan(16);
    }

    @Test
    void everyVisibleButtonShowsItsWholeText() {
        for (Dimension size : List.of(new Dimension(1200, 800), new Dimension(900, 600))) {
            resize(size.width, size.height);
            assertButtonsShowTheirText();
        }
        events.end(TASK);
        runEditorThread();
        assertButtonsShowTheirText();
    }

    private void assertButtonsShowTheirText() {
        for (Component child : visibleDescendants(panel())) {
            if (child instanceof AbstractButton button) {
                assertThat(button.getText()).as("a button's text").isNotBlank();
                assertThat(button.getPreferredSize().width).as("«%s» fits", button.getText())
                        .isLessThanOrEqualTo(button.getWidth());
            }
        }
    }

    @Test
    void whenTheTaskEndedOnlyWhatMakesSenseThenShows() {
        events.check("t1", 0, true, "All 4 expected rows match what VSim measured for the truth table of S and C");
        events.end(TASK);
        runEditorThread();

        assertThat(panel().nextButton().isVisible()).isFalse();
        assertThat(panel().stopButton().isVisible()).isFalse();
        assertThat(panel().toStageEndButton().isVisible()).isFalse();
        assertThat(panel().continuousButton().getParent().isVisible()).isFalse();
        assertThat(panel().undoStageButton().isVisible()).isTrue();
        assertThat(panel().keepOnlyResultButton().isVisible()).isTrue();
        assertThat(panel().closeButton().isVisible()).isTrue();
        StageCard.Line summary = panel().cards().getFirst().lines().get(1);
        assertThat(summary.getToolTipText(null)).contains("truth table of S and C");
    }

    @Test
    void theHeaderShowsAMoveCursorAndDraggingItMovesThePanelInsideThePane() {
        assertThat(panel().headerBar().getCursor().getType()).isEqualTo(Cursor.MOVE_CURSOR);
        assertThat(panel().grip().getText()).isNotBlank();
        Point before = panel().getLocation();

        drag(panel().grip(), -300, 200);
        assertThat(panel().getLocation()).isEqualTo(new Point(before.x - 300, before.y + 200));

        drag(panel().header(), -5000, 5000); // far out: kept inside the pane
        Rectangle pane = paneInLayered();
        assertThat(pane.contains(panel().getBounds())).isTrue();
        assertThat(panel().getX()).isEqualTo(pane.x);
        assertThat(panel().getY() + panel().getHeight()).isEqualTo(pane.y + pane.height);
    }

    @Test
    void aMovedPanelKeepsToItsNearestCornerWhenThePaneResizesAndADoubleClickPutsItBack() {
        drag(panel().headerBar(), -2000, 2000); // the bottom left corner
        Rectangle pane = paneInLayered();
        assertThat(panel().getX()).isEqualTo(pane.x);

        resize(1000, 700);
        pane = paneInLayered();
        assertThat(panel().getX()).isEqualTo(pane.x);
        assertThat(panel().getY() + panel().getHeight()).isEqualTo(pane.y + pane.height);

        doubleClick(panel().headerBar());
        assertThat(panel().getX() + panel().getWidth()).isEqualTo(pane.x + pane.width - FloatingPlacement.MARGIN);
        assertThat(panel().getY()).isEqualTo(pane.y + FloatingPlacement.MARGIN);
    }

    @Test
    void theMenuMovesThePanelToEachCorner() {
        List<JMenuItem> moves = panel().moveItems();
        assertThat(moves).extracting(JMenuItem::getText).containsExactly("Top right", "Top left", "Bottom right",
                "Bottom left");

        moves.get(3).doClick();
        Rectangle pane = paneInLayered();
        assertThat(panel().getX()).isEqualTo(pane.x + FloatingPlacement.MARGIN);
        assertThat(panel().getY() + panel().getHeight()).isEqualTo(pane.y + pane.height - FloatingPlacement.MARGIN);

        moves.get(1).doClick();
        assertThat(panel().getLocation()).isEqualTo(new Point(pane.x + FloatingPlacement.MARGIN,
                pane.y + FloatingPlacement.MARGIN));
    }

    @Test
    void theRectangleThePanelCoversIsGivenInEachPanesPixels() {
        Rectangle covered = features().placement().coveredIn(view.canvas()).orElseThrow();
        Rectangle pane = paneInLayered();
        assertThat(covered).isEqualTo(new Rectangle(panel().getX() - pane.x, panel().getY() - pane.y,
                panel().getWidth(), panel().getHeight()));

        events.stop(TASK);
        runEditorThread();
        panel().closeButton().doClick(); // a stopped task's panel stays until closed (review M5)
        assertThat(features().placement().coveredIn(view.canvas())).isEmpty();
    }

    private void drag(JComponent handle, int deltaX, int deltaY) {
        Point start = new Point(5, 5);
        Point screen = new Point(1000, 1000);
        handle.dispatchEvent(mouse(handle, MouseEvent.MOUSE_PRESSED, start, screen, 1));
        handle.dispatchEvent(mouse(handle, MouseEvent.MOUSE_DRAGGED, new Point(start.x + deltaX / 2,
                start.y + deltaY / 2), new Point(screen.x + deltaX / 2, screen.y + deltaY / 2), 0));
        handle.dispatchEvent(mouse(handle, MouseEvent.MOUSE_DRAGGED, new Point(start.x + deltaX, start.y + deltaY),
                new Point(screen.x + deltaX, screen.y + deltaY), 0));
        handle.dispatchEvent(mouse(handle, MouseEvent.MOUSE_RELEASED, new Point(start.x + deltaX, start.y + deltaY),
                new Point(screen.x + deltaX, screen.y + deltaY), 1));
        layOut(panel());
    }

    private void doubleClick(JComponent handle) {
        Point at = new Point(5, 5);
        handle.dispatchEvent(mouse(handle, MouseEvent.MOUSE_CLICKED, at, new Point(1000, 1000), 2));
        layOut(panel());
    }

    private static MouseEvent mouse(JComponent source, int id, Point at, Point onScreen, int clicks) {
        return new MouseEvent(source, id, System.currentTimeMillis(), id == MouseEvent.MOUSE_DRAGGED
                ? InputEvent.BUTTON1_DOWN_MASK : 0, at.x, at.y, onScreen.x, onScreen.y, clicks, false,
                MouseEvent.BUTTON1);
    }

    /** The visible components of {@code container}, but those inside a scrolled viewport's view and scroll bars. */
    private static List<Component> visibleDescendants(Container container) {
        List<Component> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (!child.isVisible() || child instanceof JScrollBar) {
                continue;
            }
            found.add(child);
            if (child instanceof Container inner && !(child instanceof JViewport)) {
                found.addAll(visibleDescendants(inner));
            }
        }
        return found;
    }
}
