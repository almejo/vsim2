package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** When the magnifier shows (spec §8.2): the toggle, Alt held or a menu request, on a user-component instance only. */
class MagnifierHoverTest {

    private static final String INSTANCE = "c30";
    private static final Point2D OUTSIDE_THE_PANEL = new Point2D.Double(5, 5);
    private static final Point2D ON_THE_PANEL = new Point2D.Double(500, 500);

    private final List<String> calls = new ArrayList<>();
    private final MagnifierPresenter presenter = new MagnifierPresenter() {
        @Override
        public void show(CircuitCanvas pane, Component instance, Point2D screenPoint) {
            calls.add("show " + instance.id().value());
        }

        @Override
        public void hide() {
            calls.add("hide");
        }

        @Override
        public boolean covers(Point2D screenLocation) {
            return screenLocation.equals(ON_THE_PANEL);
        }
    };
    private boolean toggled;
    private EditorFixture fixture;
    private CircuitCanvas pane;
    private MagnifierHover hover;
    private Point2D onInstance;
    private Point2D onGate;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create();
        pane = fixture.view().canvas();
        fixture.openDocument().execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = new Component(new ElementId(INSTANCE), SubcircuitTypes.typeIdOf(
                ExampleDefinitions.INVERTER), new GridPoint(10, 10), Rotation.R0, Map.of(), "");
        Component gate = fixture.openDocument().registry().require(StandardComponentTypes.BUFFER).newComponent(
                new ElementId("c31"), new GridPoint(40, 10), Rotation.R0, Map.of(), "");
        fixture.openDocument().execute(new AddElementsCommand(List.of(instance, gate)));
        onInstance = center(instance);
        onGate = center(gate);
        hover = new MagnifierHover(() -> toggled, presenter);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private Point2D center(Component component) {
        Bounds bounds = ElementBounds.of(component, fixture.openDocument().registry());
        return pane.viewport().gridToScreen(bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0);
    }

    @Test
    void withTheToggleOffNothingShows() {
        hover.pointerMoved(pane, onInstance);

        assertThat(calls).isEmpty();
    }

    @Test
    void withTheToggleOnHoveringAnInstanceShowsItAndLeavingHidesIt() {
        toggled = true;

        hover.pointerMoved(pane, onInstance);
        hover.pointerMoved(pane, onInstance);
        hover.pointerExited(OUTSIDE_THE_PANEL);

        assertThat(calls).containsExactly("show " + INSTANCE, "hide");
    }

    @Test
    void aBuiltInPartShowsNothing() {
        toggled = true;

        hover.pointerMoved(pane, onGate);

        assertThat(calls).isEmpty();
    }

    @Test
    void movingFromTheInstanceToAnotherPartHidesTheMagnifier() {
        toggled = true;
        hover.pointerMoved(pane, onInstance);

        hover.pointerMoved(pane, onGate);

        assertThat(calls).containsExactly("show " + INSTANCE, "hide");
    }

    @Test
    void altHeldShowsTheMagnifierWithTheToggleOffAndReleasingHidesIt() {
        hover.altChanged(true);
        hover.pointerMoved(pane, onInstance);
        hover.altChanged(false);

        assertThat(calls).containsExactly("show " + INSTANCE, "hide");
    }

    @Test
    void escapeHidesItAndItDoesNotComeBackUntilThePointerLeavesTheInstance() {
        toggled = true;
        hover.pointerMoved(pane, onInstance);

        hover.escapePressed();
        hover.pointerMoved(pane, onInstance);
        hover.pointerMoved(pane, onGate);
        hover.pointerMoved(pane, onInstance);

        assertThat(calls).containsExactly("show " + INSTANCE, "hide", "show " + INSTANCE);
    }

    @Test
    void aPressHidesTheMagnifier() {
        toggled = true;
        hover.pointerMoved(pane, onInstance);

        hover.pointerPressed();

        assertThat(calls).containsExactly("show " + INSTANCE, "hide");
    }

    @Test
    void theMenuItemShowsTheInstanceWithTheToggleOffAndItGoesWhenThePointerLeavesIt() {
        Component instance = fixture.openDocument().document().component(new ElementId(INSTANCE)).orElseThrow();

        hover.requestedFromMenu(pane, instance, onInstance);
        hover.pointerMoved(pane, onInstance);
        hover.pointerMoved(pane, onGate);

        assertThat(calls).containsExactly("show " + INSTANCE, "hide");
    }

    @Test
    void leavingTheCanvasIntoThePanelKeepsItUntilThePointerLeavesThePanel() {
        toggled = true;
        hover.pointerMoved(pane, onInstance);

        hover.pointerExited(ON_THE_PANEL);
        assertThat(calls).containsExactly("show " + INSTANCE);
        hover.pointerLeftPanel();

        assertThat(calls).containsExactly("show " + INSTANCE, "hide");
    }

    @Test
    void theMenuItemsPanelWaitsForThePointerToComeBackFromTheMenu() {
        Component instance = fixture.openDocument().document().component(new ElementId(INSTANCE)).orElseThrow();
        Point2D whereTheMenuItemWas = new Point2D.Double(onGate.getX(), onGate.getY() + 200);

        hover.requestedFromMenu(pane, instance, onInstance);
        hover.pointerMoved(pane, whereTheMenuItemWas);
        hover.pointerMoved(pane, onInstance);
        assertThat(calls).containsExactly("show " + INSTANCE);
        hover.pointerMoved(pane, whereTheMenuItemWas);

        assertThat(calls).containsExactly("show " + INSTANCE, "hide");
    }

    @Test
    void instanceAtFindsOnlyUserComponents() {
        assertThat(MagnifierHover.instanceAt(pane, onInstance).map(found -> found.id().value()))
                .contains(INSTANCE);
        assertThat(MagnifierHover.instanceAt(pane, onGate)).isEqualTo(Optional.empty());
    }
}
