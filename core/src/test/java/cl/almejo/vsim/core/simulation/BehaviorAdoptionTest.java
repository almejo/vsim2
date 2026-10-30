package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link Behavior#adoptStateFrom}: a part rebuilt by a parameter change can keep what it held (a screen's text). */
class BehaviorAdoptionTest {

    /** A part that remembers its lines and whom it adopted them from. */
    private static final class Screen implements Behavior {

        final List<String> lines = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        Behavior adoptedFrom;

        @Override
        public void initialize(BehaviorContext context) {
            events.add("initialize");
        }

        @Override
        public void inputChanged(BehaviorContext context, int pinIndex) {
        }

        @Override
        public void adoptStateFrom(Behavior previous) {
            events.add("adopt");
            adoptedFrom = previous;
            if (previous instanceof Screen screen) {
                lines.addAll(screen.lines);
            }
        }
    }

    private final List<Screen> built = new ArrayList<>();
    private final ComponentType screenType = type("screen");
    private final ComponentType otherType = type("other");
    private final SimulationEngine engine = new SimulationEngine();

    private ComponentType type(String id) {
        return new ComponentType(id, ComponentCategory.INPUT_OUTPUT, new GridSize(4, 4), List.of(),
                List.of(ParameterSpec.integer("rows", 24, 1, 100), ParameterSpec.bool("green", true).asDisplayOnly()),
                parameters -> {
                    Screen screen = new Screen();
                    built.add(screen);
                    return screen;
                });
    }

    private FlatCircuit circuit(ComponentType type, Map<String, Object> parameters) {
        return new FlatCircuit(List.of(new FlatComponent("s", type, parameters)), List.of());
    }

    @Test
    void aParameterChangeHandsTheOldBehaviourToTheNewOneBeforeItStarts() {
        engine.update(circuit(screenType, Map.of("rows", 24)));
        Screen first = built.get(0);
        first.lines.add("hola");

        engine.update(circuit(screenType, Map.of("rows", 30)));

        Screen second = built.get(1);
        assertThat(second.adoptedFrom).isSameAs(first);
        assertThat(second.lines).containsExactly("hola");
        assertThat(second.events).containsExactly("adopt", "initialize");
    }

    @Test
    void aDisplayOnlyChangeKeepsTheBehaviourSoNothingIsAdopted() {
        engine.update(circuit(screenType, Map.of("rows", 24)));

        engine.update(circuit(screenType, Map.of("rows", 24, "green", false)));

        assertThat(built).hasSize(1);
    }

    @Test
    void aRestartStartsFromNothing() {
        engine.update(circuit(screenType, Map.of("rows", 24)));
        built.get(0).lines.add("hola");

        engine.restart(circuit(screenType, Map.of("rows", 24)), Map.of());

        assertThat(built).hasSize(2);
        assertThat(built.get(1).adoptedFrom).isNull();
        assertThat(built.get(1).lines).isEmpty();
    }

    @Test
    void anotherTypeUnderTheSameIdAdoptsNothing() {
        engine.update(circuit(screenType, Map.of("rows", 24)));

        engine.update(circuit(otherType, Map.of("rows", 24)));

        assertThat(built.get(1).adoptedFrom).isNull();
    }

    @Test
    void aNewComponentAdoptsNothing() {
        engine.update(circuit(screenType, Map.of("rows", 24)));

        assertThat(built.get(0).adoptedFrom).isNull();
        assertThat(built.get(0).events).containsExactly("initialize");
    }
}
