package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Changing the width of a turned Switch or LED keeps its direction and its body upright; undo gives it all back. */
class ChangeMultiBitWidthTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentType switchType = registry.require(StandardComponentTypes.SWITCH);
    private final CircuitDocument document = new CircuitDocument();

    private Component place(Rotation rotation, Map<String, Object> parameters) {
        Component component = switchType.newComponent(document.newComponentId(), new GridPoint(10, 10), rotation,
                parameters, "");
        document.add(component);
        return component;
    }

    private ChangeParametersCommand widthChange(Component component, int width) {
        Map<String, Object> parameters = new HashMap<>(component.parameters());
        parameters.put("width", width);
        return new ChangeParametersCommand(component.id(), switchType, parameters);
    }

    @Test
    void wideningATurnedSwitchKeepsThePinDirectionAsTheSideAndTheBodyUpright() {
        Component narrow = place(Rotation.R90, Map.of());
        GridPoint pinBefore = switchType.pinLocation(narrow, 0);

        widthChange(narrow, 8).apply(document);

        Component wide = document.component(narrow.id()).orElseThrow();
        assertThat(wide.rotation()).isEqualTo(Rotation.R0);
        assertThat(MultiBitLayout.sideOf(switchType, wide.parameters())).isEqualTo("bottom");
        assertThat(switchType.pinLocation(wide, 0)).isEqualTo(pinBefore);
    }

    @Test
    void narrowingASwitchWithItsPinBelowTurnsTheOneBitSwitchDown() {
        Component wide = place(Rotation.R0, Map.of("width", 8, "side", "bottom"));

        widthChange(wide, 1).apply(document);

        Component narrow = document.component(wide.id()).orElseThrow();
        assertThat(narrow.rotation()).isEqualTo(Rotation.R90);
        assertThat(MultiBitLayout.sideOf(switchType, narrow.parameters())).isEqualTo("right");
        assertThat(switchType.pinLocation(narrow, 0)).isEqualTo(switchType.pinLocation(wide, 0));
    }

    @Test
    void undoGivesBackTheRotationThePlaceAndTheParameters() {
        Component narrow = place(Rotation.R270, Map.of());
        ChangeParametersCommand change = widthChange(narrow, 8);

        change.apply(document);
        change.revert(document);

        assertThat(document.component(narrow.id()).orElseThrow()).isEqualTo(narrow);
    }

    @Test
    void wideningAnUprightSwitchKeepsItsPlaceAsBefore() {
        Component narrow = place(Rotation.R0, Map.of());

        widthChange(narrow, 8).apply(document);

        Component wide = document.component(narrow.id()).orElseThrow();
        assertThat(wide.position()).isEqualTo(narrow.position());
        assertThat(MultiBitLayout.sideOf(switchType, wide.parameters())).isEqualTo("right");
    }
}
