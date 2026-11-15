package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The machine tab reads the active state from the real simulation (review M5): a machine copy {@code m} inside a
 * user component placed as {@code w}, the tab entered at {@code w/m}, no fake state source; and the next arrow, which
 * follows a switch before any clock edge.
 */
class MachineCanvasSimulationTest {

    private static final String WRAPPER = "envoltura";

    /** A switch starts at 1 by default; the clock and {@code x} start at 0. */
    private static final Map<String, Object> SWITCH_OFF = Map.of("value", "0");

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private OpenDocument place;
    private MachineCanvas canvas;

    @AfterEach
    void close() {
        if (canvas != null) {
            canvas.dispose();
        }
        if (place != null) {
            place.close();
        }
        document.close();
    }

    @Test
    void theTabEnteredInsideAComponentShowsTheSimulatedStateOfThatCopy() {
        ComponentDefinition machine = MachineDefinitions.regenerated(
                MachineFixtures.definition(MachineFixtures.detector()));
        Component copy = new Component(new ElementId("m"), SubcircuitTypes.typeIdOf(machine.id()), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), "");
        ComponentDefinition wrapper = new ComponentDefinition(WRAPPER, "Envoltura", "", new GridSize(6, 4), List.of(),
                new CircuitFragment(List.of(copy), List.of(), List.of()), Optional.empty(), 1);
        document.execute(new PutDefinitionsCommand(List.of(machine, wrapper)));
        document.execute(new AddElementsCommand(List.of(new Component(new ElementId("w"),
                SubcircuitTypes.typeIdOf(WRAPPER), new GridPoint(10, 10), Rotation.R0, Map.of(), ""))));
        place = document.openBody(machine.id(), Optional.of("w/m"));
        canvas = new MachineCanvas(place, machine.id(), new MachineSelection(), () -> ColorScheme.LIGHT);

        UiTestSupport.awaitSimulation(document);
        canvas.showLatestState();

        assertThat(place.contextPath()).contains("w/m");
        assertThat(canvas.activeState()).hasValueSatisfying(state -> assertThat(state.stateId()).isEqualTo("s0"));
    }

    @Test
    void theNextArrowFollowsTheSwitchWithARealEngine() {
        ComponentDefinition machine = MachineDefinitions.regenerated(
                MachineFixtures.definition(MachineFixtures.detector()));
        document.execute(new PutDefinitionsCommand(List.of(machine)));
        Component piece = UiTestSupport.place(document, SubcircuitTypes.typeIdOf(machine.id()), 20, 0, Map.of());
        Component input = switchOn(piece, machine, "x");
        Component clock = switchOn(piece, machine, MachineLimits.CLOCK);
        place = document.openBody(machine.id(), Optional.of(piece.id().value()));
        canvas = new MachineCanvas(place, machine.id(), new MachineSelection(), () -> ColorScheme.LIGHT);

        document.interact(input.id());
        document.interact(clock.id());
        document.interact(clock.id());
        UiTestSupport.awaitSimulation(document);
        canvas.showLatestState();
        assertThat(canvas.activeState()).hasValueSatisfying(state -> {
            assertThat(state.stateId()).isEqualTo("s1");
            assertThat(state.nextTransitionId()).as("x = 1: on to S2").contains("t3");
        });

        document.interact(input.id());
        UiTestSupport.awaitSimulation(document);
        canvas.showLatestState();
        assertThat(canvas.activeState()).hasValueSatisfying(state -> {
            assertThat(state.stateId()).as("no edge yet").isEqualTo("s1");
            assertThat(state.nextTransitionId()).as("x = 0: back to S0").contains("t2");
        });
    }

    /** A switch at 0 wired to {@code pinName} of {@code piece} from 4 units to its left. */
    private Component switchOn(Component piece, ComponentDefinition machine, String pinName) {
        GridPoint pin = document.registry().typeOf(piece).pinLocation(piece,
                machine.pinIndexOf(pinName).orElseThrow());
        GridPoint end = pin.translate(-4, 0);
        ComponentType switchType = document.registry().require(StandardComponentTypes.SWITCH);
        Component probe = switchType.newComponent(new ElementId("probe"), GridPoint.ORIGIN, Rotation.R0, SWITCH_OFF,
                "");
        GridPoint offset = switchType.pinLocation(probe, 0);
        Component placed = UiTestSupport.place(document, StandardComponentTypes.SWITCH, end.x() - offset.x(),
                end.y() - offset.y(), SWITCH_OFF);
        UiTestSupport.addWire(document, pin.x(), pin.y(), end.x(), end.y());
        return placed;
    }
}
