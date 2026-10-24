package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class FragmentFormatTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final FragmentFormat format = new FragmentFormat(registry);

    private CircuitFragment sample() {
        return new CircuitFragment(
                List.of(registry.require(StandardComponentTypes.AND2).newComponent(new ElementId("c1"),
                        new GridPoint(0, 2), Rotation.R0, Map.of("delay", 3), "U1")),
                List.of(new Wire(new ElementId("w2"), new GridPoint(4, 4), new GridPoint(10, 4))),
                List.of(new TextLabel(new ElementId("l3"), new GridPoint(2, 0), "nota")));
    }

    @Test
    void roundTripKeepsEveryElement() throws CircuitFormatException {
        String json = format.write(sample());
        assertThat(json).contains("\"formatVersion\":3").contains("\"kind\":\"fragment\"");
        assertThat(format.read(json)).isEqualTo(sample());
    }

    @Test
    void readMovesAHandWrittenFragmentToTheOrigin() throws CircuitFormatException {
        CircuitFragment fragment = format.read("""
                {"formatVersion":2,"kind":"fragment","labels":[{"id":"l1","x":5,"y":7,"text":"hola"}]}
                """);
        assertThat(fragment.labels()).containsExactly(new TextLabel(new ElementId("l1"), GridPoint.ORIGIN, "hola"));
    }

    @ParameterizedTest
    @MethodSource("badInputs")
    void badInputFailsWithAReason(String json, CircuitFormatException.Reason reason) {
        assertThatThrownBy(() -> format.read(json))
                .isInstanceOfSatisfying(CircuitFormatException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    static Stream<Arguments> badInputs() {
        return Stream.of(
                arguments(null, CircuitFormatException.Reason.INVALID_JSON),
                arguments("not json", CircuitFormatException.Reason.INVALID_JSON),
                arguments("[1,2]", CircuitFormatException.Reason.NOT_VSIM_FILE),
                arguments("{\"formatVersion\":2,\"kind\":\"circuit\"}", CircuitFormatException.Reason.INVALID_ELEMENT),
                arguments("{\"formatVersion\":4,\"kind\":\"fragment\"}", CircuitFormatException.Reason.NEWER_FORMAT),
                arguments("{\"formatVersion\":2,\"kind\":\"fragment\",\"components\":"
                        + "[{\"id\":\"c1\",\"type\":\"lamp\",\"x\":0,\"y\":0}]}",
                        CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE),
                arguments("{\"formatVersion\":2,\"kind\":\"fragment\",\"wires\":"
                        + "[{\"id\":\"w1\",\"x1\":0,\"y1\":0,\"x2\":3,\"y2\":3}]}",
                        CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    @Test
    void definitionsTravelWithTheFragment() throws CircuitFormatException {
        Component instance = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER),
                new GridPoint(5, 7), Rotation.R0, Map.of(), "U1");
        CircuitFragment fragment = new CircuitFragment(List.of(instance), List.of(), List.of(),
                List.of(ExampleDefinitions.inverter()));
        FragmentFormat format = new FragmentFormat(StandardComponentTypes.registry());

        CircuitFragment read = format.read(format.write(fragment));

        assertThat(read.definitions()).containsExactly(ExampleDefinitions.inverter());
        assertThat(read.components()).singleElement().extracting(Component::position).isEqualTo(GridPoint.ORIGIN);
    }
}
