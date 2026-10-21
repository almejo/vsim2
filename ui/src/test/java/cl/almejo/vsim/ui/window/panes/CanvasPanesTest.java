package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasHost;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CanvasPanesTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private final List<CircuitCanvas> created = new ArrayList<>();
    private CanvasPanes panes;
    private CircuitCanvas first;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        first = newCanvas();
        panes = new CanvasPanes(this::newCanvas, first);
        panes.setSize(800, 600);
        panes.doLayout();
        first.applyViewState(new ViewState(2, 30, 40));
    }

    @AfterEach
    void close() {
        panes.dispose();
        openDocument.close();
    }

    private CircuitCanvas newCanvas() {
        CircuitCanvas canvas = new CircuitCanvas(openDocument,
                new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
                new CanvasHost() {
                    @Override
                    public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                                Point2D screenPosition) {
                    }

                    @Override
                    public void configure(Component component) {
                    }
                }, selection);
        created.add(canvas);
        return canvas;
    }

    private static void assertSameView(ViewState actual, ViewState expected) {
        assertThat(actual.zoom()).isCloseTo(expected.zoom(), within(1e-9));
        assertThat(actual.centerX()).isCloseTo(expected.centerX(), within(1e-6));
        assertThat(actual.centerY()).isCloseTo(expected.centerY(), within(1e-6));
    }

    private static void press(CircuitCanvas canvas) {
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, 5, 5, 1, false, MouseEvent.BUTTON1);
        for (var listener : canvas.getMouseListeners()) {
            listener.mousePressed(event);
        }
    }

    @Test
    void aSplitGivesTwoThenFourPanesInReadingOrderShowingTheSplitPanesView() {
        ViewState before = first.viewState();
        panes.split(Orientation.HORIZONTAL, 0.5);
        assertThat(panes.panes()).hasSize(2).startsWith(first);
        CircuitCanvas bottom = panes.panes().get(1);
        assertThat(panes.positionOf(bottom)).contains(PanePosition.BOTTOM_LEFT);
        assertSameView(bottom.viewState(), before);
        assertSameView(first.viewState(), before);
        assertThat(first.getHeight() + bottom.getHeight()).isEqualTo(600 - PaneDivider.thickness());
        assertThat(bottom.getY()).isGreaterThan(first.getY() + first.getHeight() - 1);

        panes.split(Orientation.VERTICAL, 0.25);
        assertThat(panes.panes()).hasSize(4);
        assertThat(panes.panes().get(0)).isSameAs(first);
        assertThat(panes.panes().get(2)).isSameAs(bottom);
        assertThat(panes.positionOf(panes.panes().get(1))).contains(PanePosition.TOP_RIGHT);
        assertThat(panes.positionOf(panes.panes().get(3))).contains(PanePosition.BOTTOM_RIGHT);
        assertSameView(panes.panes().get(1).viewState(), before);
        assertThat(first.getWidth()).isLessThan(panes.panes().get(1).getWidth());
        assertThat(panes.active()).isSameAs(first);
        assertThat(panes.paneLayout().horizontalDivider()).hasValue(0.5);
        assertThat(panes.paneLayout().verticalDivider()).hasValue(0.25);
    }

    @Test
    void everyPaneSharesTheOneSelection() {
        panes.split(Orientation.VERTICAL, 0.5);
        CircuitCanvas right = panes.panes().get(1);
        assertThat(right.selection()).isSameAs(first.selection()).isSameAs(selection);
    }

    @Test
    void theActivePaneIsTheOneLastPressedOrFocusedAndOnlyItHasTheAccentBorder() {
        assertThat(first.getBorder()).isNull(); // a single pane has no border
        panes.split(Orientation.VERTICAL, 0.5);
        CircuitCanvas right = panes.panes().get(1);
        List<CircuitCanvas> announced = new ArrayList<>();
        panes.addActiveListener(announced::add);
        assertThat(first.getBorder()).isNotNull();
        assertThat(right.getBorder()).isNull();

        press(right);
        assertThat(panes.active()).isSameAs(right);
        assertThat(right.getBorder()).isNotNull();
        assertThat(first.getBorder()).isNull();

        FocusEvent focus = new FocusEvent(first, FocusEvent.FOCUS_GAINED);
        for (FocusListener listener : first.getFocusListeners()) {
            listener.focusGained(focus);
        }
        assertThat(panes.active()).isSameAs(first);
        assertThat(announced).containsExactly(right, first);
    }

    @Test
    void removingADividerClosesTheSideThatGoesAndTheActivePaneSurvivesOrMovesToItsNeighbour() {
        panes.split(Orientation.HORIZONTAL, 0.5);
        panes.split(Orientation.VERTICAL, 0.5);
        CircuitCanvas topRight = panes.panes().get(1);
        CircuitCanvas bottomLeft = panes.panes().get(2);
        CircuitCanvas bottomRight = panes.panes().get(3);
        List<List<CircuitCanvas>> changes = new ArrayList<>();
        panes.addPanesListener(changes::add);
        panes.activate(bottomRight);

        panes.removeDivider(Orientation.HORIZONTAL);
        assertThat(panes.panes()).containsExactly(first, topRight);
        assertThat(panes.active()).isSameAs(topRight);
        assertThat(bottomLeft.getParent()).isNull();
        assertThat(changes).containsExactly(List.of(first, topRight));
        assertThat(panes.paneLayout().horizontalDivider()).isEmpty();

        panes.removeDivider(Orientation.VERTICAL, Side.BEFORE);
        assertThat(panes.panes()).containsExactly(topRight);
        assertThat(panes.positionOf(topRight)).contains(PanePosition.TOP_LEFT);
        assertThat(panes.active()).isSameAs(topRight);
        assertThat(topRight.getBorder()).isNull();
    }

    @Test
    void removeAllKeepsTheActivePaneAlone() {
        panes.split(Orientation.HORIZONTAL, 0.5);
        panes.split(Orientation.VERTICAL, 0.5);
        CircuitCanvas bottomRight = panes.panes().get(3);
        panes.activate(bottomRight);
        panes.removeAll();
        assertThat(panes.panes()).containsExactly(bottomRight);
        assertThat(panes.paneLayout().isSplit()).isFalse();
        assertThat(bottomRight.getBounds()).isEqualTo(new java.awt.Rectangle(0, 0, 800, 600));
    }

    @Test
    void aLayoutComesBackWithItsDividersAndViews() {
        ViewState one = new ViewState(1, 0, 0);
        ViewState two = new ViewState(1.5, 10, 0);
        ViewState three = new ViewState(0.5, 0, 10);
        ViewState four = new ViewState(3, 10, 10);
        PaneLayout layout = new PaneLayout(OptionalDouble.of(0.4), OptionalDouble.of(0.6),
                List.of(one, two, three, four));
        List<Runnable> layoutChanges = new ArrayList<>();
        panes.addLayoutListener(() -> layoutChanges.add(() -> { }));
        panes.apply(layout);
        assertThat(panes.panes()).hasSize(4).startsWith(first);
        assertSameView(panes.panes().get(0).viewState(), one);
        assertSameView(panes.panes().get(1).viewState(), two);
        assertSameView(panes.panes().get(2).viewState(), three);
        assertSameView(panes.panes().get(3).viewState(), four);
        assertThat(panes.paneLayout().horizontalDivider()).hasValue(0.4);
        assertThat(panes.paneLayout().verticalDivider()).hasValue(0.6);
        assertThat(layoutChanges).isNotEmpty();

        panes.apply(PaneLayout.single(two));
        assertThat(panes.panes()).containsExactly(first);
        assertSameView(first.viewState(), two);
    }

    @Test
    void aDividerMovesAndThePanesFollow() {
        panes.split(Orientation.VERTICAL, 0.5);
        int widthBefore = first.getWidth();
        panes.moveDivider(Orientation.VERTICAL, 0.25);
        assertThat(first.getWidth()).isLessThan(widthBefore);
        assertThat(panes.paneLayout().verticalDivider()).hasValue(0.25);
    }

    @Test
    void aPaneSplitBeforeTheAreaHasASizeShowsItsViewOnceItGetsOne() {
        CanvasPanes unsized = new CanvasPanes(this::newCanvas, newCanvas());
        try {
            unsized.apply(new PaneLayout(OptionalDouble.of(0.5), OptionalDouble.empty(),
                    List.of(new ViewState(2, 5, 6), new ViewState(1, 7, 8))));
            unsized.setSize(400, 400);
            unsized.doLayout();
            assertSameView(unsized.panes().get(1).viewState(), new ViewState(1, 7, 8));
            assertSameView(unsized.panes().get(0).viewState(), new ViewState(2, 5, 6));
        } finally {
            unsized.dispose();
        }
    }
}
