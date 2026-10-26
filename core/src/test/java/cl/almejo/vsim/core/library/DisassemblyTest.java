package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DisassemblyTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry()
            .extendedWith(SubcircuitTypes.resolverFor(document));

    /** An inverter instance at (10, 10): pins at (10, 12) and (14, 12); the body's pins land on (12, 12), (16, 12). */
    private Component inverterAt10() {
        document.putDefinition(ExampleDefinitions.inverter());
        ComponentType type = registry.require(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER));
        Component instance = type.newComponent(document.newComponentId(), new GridPoint(10, 10), Rotation.R0,
                Map.of(), "");
        document.add(instance);
        return instance;
    }

    @Test
    void outsideWiresAreBridgedToThePastedPins() {
        Component instance = inverterAt10();
        wire(document, 4, 12, 10, 12);
        wire(document, 14, 12, 20, 12); // already runs over the body's output pin: no bridge needed

        Disassembly.Plan plan = Disassembly.plan(document, instance, ExampleDefinitions.inverter(), registry);

        assertThat(plan.blocked()).isFalse();
        assertThat(plan.offset()).isEqualTo(new GridPoint(10, 10));
        assertThat(plan.unconnectedPins()).isEmpty();
        assertThat(plan.bridges()).extracting(Wire::start, Wire::end)
                .containsExactly(tuple(new GridPoint(10, 12), new GridPoint(12, 12)));
    }

    @Test
    void aBodyLandingOnAnotherNetIsBlocked() {
        Component instance = inverterAt10();
        wire(document, 4, 12, 10, 12);
        wire(document, 12, 4, 12, 12); // a foreign wire ending where the body's input lands

        assertThat(Disassembly.plan(document, instance, ExampleDefinitions.inverter(), registry).blocked()).isTrue();
    }

    @Test
    void anOutsideWireOfTheInputRunningUnderTheOutputIsBlocked() {
        Component instance = inverterAt10();
        wire(document, 4, 12, 10, 12);
        wire(document, 10, 12, 10, 20);
        wire(document, 10, 20, 16, 20);
        wire(document, 16, 12, 16, 20); // the input's net reaches (16, 12), where the body's output lands

        assertThat(Disassembly.plan(document, instance, ExampleDefinitions.inverter(), registry).blocked()).isTrue();
    }

    @Test
    void anOutsideWireRunningThroughTheInstancePinAndTheBodyPinIsNotBlocked() {
        Component instance = inverterAt10();
        wire(document, 4, 12, 10, 12);
        // passes mid-wire through the output pin (14, 12) and the body's output (16, 12)
        wire(document, 13, 12, 20, 12);

        Disassembly.Plan plan = Disassembly.plan(document, instance, ExampleDefinitions.inverter(), registry);

        assertThat(plan.blocked()).isFalse();
        assertThat(plan.bridges()).extracting(Wire::start, Wire::end)
                .containsExactly(tuple(new GridPoint(10, 12), new GridPoint(12, 12)));
    }

    @Test
    void aBridgeFromAPinOnTheMiddleOfAnOutsideWireJoinsThatWire() {
        Component instance = inverterAt10();
        wire(document, 4, 12, 10, 12);
        wire(document, 14, 6, 14, 18); // a bus passing mid-wire through the output pin (14, 12)

        Disassembly.Plan plan = Disassembly.plan(document, instance, ExampleDefinitions.inverter(), registry);

        assertThat(plan.blocked()).isFalse();
        assertThat(plan.bridges()).extracting(Wire::start, Wire::end).containsExactly(
                tuple(new GridPoint(10, 12), new GridPoint(12, 12)), tuple(new GridPoint(14, 12), new GridPoint(16, 12)));
    }
}
