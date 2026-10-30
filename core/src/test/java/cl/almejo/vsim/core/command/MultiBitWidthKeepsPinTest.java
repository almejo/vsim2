package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Changing the width of a multi-bit LED or Switch moves the part so its pin stays on the grid point it was on. */
class MultiBitWidthKeepsPinTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    static Stream<Arguments> cases() {
        int[][] widths = {{8, 1}, {1, 8}, {8, 4}, {4, 8}, {3, 16}};
        List<String> typeIds = List.of(StandardComponentTypes.LED, StandardComponentTypes.SWITCH);
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String typeId : typeIds) {
            for (int[] pair : widths) {
                for (int turns = 0; turns < 4; turns++) {
                    cases.add(Arguments.of(typeId, pair[0], pair[1], turns));
                }
            }
        }
        return cases.build();
    }

    @ParameterizedTest(name = "{0} {1} -> {2} bits, {3} quarter turns")
    @MethodSource("cases")
    void thePinStaysOnItsGridPointTheWireStaysOnItAndOneUndoRestoresEverything(String typeId, int from, int to,
                                                                              int turns) {
        CircuitDocument document = new CircuitDocument();
        ComponentType type = registry.require(typeId);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("width", from);
        Component placed = type.newComponent(document.newComponentId(), new GridPoint(10, 10), Rotation.R0,
                parameters, "");
        document.add(placed);
        ElementId id = placed.id();
        for (int turn = 0; turn < turns; turn++) {
            new RotateElementsCommand(List.of(id), true, registry).apply(document);
        }
        Component before = document.component(id).orElseThrow();
        GridPoint pin = type.pinLocation(before, 0);
        Wire wire = new Wire(document.newWireId(), pin, pin.translate(0, 5));
        document.add(wire);

        ChangeParametersCommand command = new ChangeParametersCommand(id, type, Map.of("width", to));
        command.apply(document);

        Component after = document.component(id).orElseThrow();
        assertThat(after.parameters().get("width")).isEqualTo(to);
        assertThat(type.pinLocation(after, 0)).isEqualTo(pin);
        assertThat(document.wire(wire.id()).orElseThrow().start()).isEqualTo(pin);

        command.revert(document);

        assertThat(document.component(id).orElseThrow()).isEqualTo(before);
    }
}
