package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.WireMode;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cl.almejo.vsim.ui.CanvasClicks.click;
import static cl.almejo.vsim.ui.CanvasClicks.doubleClick;
import static cl.almejo.vsim.ui.CanvasClicks.move;
import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.pinAt;
import static org.assertj.core.api.Assertions.assertThat;

class GuideGrabAcrossPanesTest {

    private OpenDocument openDocument;
    private EditorView view;
    private CircuitCanvas left;
    private CircuitCanvas right;

    @BeforeEach
    void open() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new GuidesExtension()));
        view = new EditorView(openDocument, services, NO_WINDOW);
        CanvasPanes panes = view.canvasPanes();
        panes.setSize(800, 300);
        panes.split(CanvasPanes.Orientation.VERTICAL, 0.5);
        panes.doLayout();
        left = view.panes().get(0);
        right = view.panes().get(1);
        left.applyViewState(new ViewState(1, left.getWidth() / 16.0, left.getHeight() / 16.0));
        right.applyViewState(new ViewState(1, 100, 20));
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void sameScenarioWithoutAGuideEnds() {
        view.services().preferences().setWireMode(WireMode.CONTINUOUS);
        pinAt(openDocument, 5, 5);
        click(left, 40, 40);
        move(right, 100, 96);
        doubleClick(right, 100, 96);
        assertThat(left.drawsBetweenClicks()).as("path ended").isFalse();
        assertThat(openDocument.document().wires()).isNotEmpty();
    }

    @Test
    void aClickOnAGuideInTheOtherPaneBelongsToThePath() {
        view.services().preferences().setWireMode(WireMode.CONTINUOUS);
        pinAt(openDocument, 5, 5);
        GridPoint end = right.viewport().snap(100, 96);
        openDocument.document().setGuides(List.of(new Guide(Guide.Orientation.HORIZONTAL, end.y())));
        click(left, 40, 40);
        move(right, 100, 96);
        doubleClick(right, 100, 96);
        assertThat(left.drawsBetweenClicks()).as("path ended").isFalse();
        assertThat(openDocument.document().wires()).isNotEmpty();
    }
}
