package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.guides.GuidesExtension;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import cl.almejo.vsim.ui.window.panes.SplitViewsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Presentation mode hides the editing grips of every pane (the user, 2026-11-08): the guide rulers and their corner,
 * the «⋮» split grips and the border of the active pane. Placed guides (they follow Ver ▸ Guías) and the dividers
 * between panes stay; leaving puts every grip back.
 */
class FullScreenEditingGripsTest {

    /** The grips the mode hides, by class (they are private to their packages). */
    private static final List<String> GRIPS = List.of("RulerCorner", "RulerHandle", "SplitGripHandle");

    private FullScreenFixture fixture;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = new FullScreenFixture(List.of(new GuidesExtension(), new SplitViewsExtension()));
        view = fixture.addTab();
        view.canvasPanes().split(CanvasPanes.Orientation.HORIZONTAL, 0.5);
        fixture.layOut();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    /** How many handles of each class {@code pane} has. */
    private static Map<String, Long> handles(CircuitCanvas pane) {
        return pane.edgeHandles().stream().map(EdgeHandle::getClass).map(Class::getSimpleName)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private List<CircuitCanvas> panes() {
        return view.canvasPanes().panes();
    }

    @Test
    void enteringHidesTheRulersTheSplitGripsAndTheActivePaneBorder() {
        assertThat(handles(view.canvas())).containsKeys("RulerCorner", "RulerHandle", "SplitGripHandle");
        assertThat(view.canvasPanes().active().getBorder()).isNotNull();
        fixture.features.enter();
        fixture.layOut();
        for (CircuitCanvas pane : panes()) {
            assertThat(handles(pane)).doesNotContainKeys(GRIPS.toArray(String[]::new));
            assertThat(handles(pane)).as("placed guides can still be grabbed").containsKey("GuideGrab");
            assertThat(pane.getBorder()).isNull();
        }
        assertThat(panes()).as("the divider between the panes stays").hasSize(2);
    }

    @Test
    void leavingPutsEveryGripBack() {
        Map<CircuitCanvas, Map<String, Long>> before = panes().stream()
                .collect(Collectors.toMap(Function.identity(), FullScreenEditingGripsTest::handles));
        CircuitCanvas active = view.canvasPanes().active();
        fixture.features.enter();
        fixture.features.leave();
        fixture.layOut();
        for (CircuitCanvas pane : panes()) {
            assertThat(handles(pane)).isEqualTo(before.get(pane));
        }
        assertThat(active.getBorder()).isNotNull();
    }

    @Test
    void aPaneSplitInTheModeHasNoGripsUntilLeaving() {
        fixture.features.enter();
        view.canvasPanes().split(CanvasPanes.Orientation.VERTICAL, 0.5);
        fixture.layOut();
        assertThat(panes()).hasSizeGreaterThan(2);
        for (CircuitCanvas pane : panes()) {
            assertThat(handles(pane)).doesNotContainKeys(GRIPS.toArray(String[]::new));
            assertThat(pane.getBorder()).isNull();
        }
        fixture.features.leave();
        for (CircuitCanvas pane : panes()) {
            assertThat(handles(pane)).containsKeys("RulerCorner", "RulerHandle", "SplitGripHandle");
        }
        assertThat(view.canvasPanes().active().getBorder()).isNotNull();
    }

    @Test
    void turningTheGuidesOnInTheModeShowsTheLinesButNotTheRulers() {
        fixture.preferences.setShowGuides(false);
        fixture.features.enter();
        fixture.preferences.setShowGuides(true);
        assertThat(handles(view.canvas())).containsKey("GuideGrab").doesNotContainKeys("RulerCorner", "RulerHandle");
        fixture.features.leave();
        assertThat(handles(view.canvas())).containsKeys("RulerCorner", "RulerHandle");
    }

    @Test
    void guidesTurnedOffStayOffAfterLeaving() {
        fixture.preferences.setShowGuides(false);
        fixture.features.enter();
        fixture.features.leave();
        assertThat(handles(view.canvas())).doesNotContainKeys("RulerCorner", "RulerHandle", "GuideGrab");
        assertThat(handles(view.canvas())).containsKey("SplitGripHandle");
    }
}
