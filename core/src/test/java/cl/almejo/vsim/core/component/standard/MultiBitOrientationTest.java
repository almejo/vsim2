package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Switch or LED keeps its direction in one place: the element's rotation at one bit, the «side» at more bits.
 * Moving it from one to the other keeps the pin where it was.
 */
class MultiBitOrientationTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private Component part(String typeId, Rotation rotation, Map<String, Object> parameters) {
        return registry.require(typeId).newComponent(new ElementId("c1"), new GridPoint(10, 10), rotation,
                parameters, "");
    }

    private Component normalized(Component component) {
        return MultiBitOrientation.normalized(component, registry.require(component.typeId()));
    }

    private GridPoint pin(Component component) {
        return registry.require(component.typeId()).pinLocation(component, 0);
    }

    private String side(Component component) {
        ComponentType type = registry.require(component.typeId());
        return MultiBitLayout.sideOf(type, component.parameters());
    }

    @Test
    void aTurnedMultiBitSwitchBecomesUprightWithItsPinOnTheTurnedSideWhereThePinWas() {
        Component turned = part(StandardComponentTypes.SWITCH, Rotation.R90, Map.of("width", 8));

        Component upright = normalized(turned);

        assertThat(upright.rotation()).isEqualTo(Rotation.R0);
        assertThat(side(upright)).isEqualTo("bottom");
        assertThat(pin(upright)).isEqualTo(pin(turned));
    }

    @Test
    void aTurnedMultiBitLedTurnsFromItsOwnSide() {
        Component turned = part(StandardComponentTypes.LED, Rotation.R180, Map.of("width", 4, "side", "top"));

        Component upright = normalized(turned);

        assertThat(upright.rotation()).isEqualTo(Rotation.R0);
        assertThat(side(upright)).isEqualTo("bottom");
        assertThat(pin(upright)).isEqualTo(pin(turned));
    }

    @Test
    void aOneBitSwitchWithASideTakesItAsARotation() {
        Component narrow = part(StandardComponentTypes.SWITCH, Rotation.R0, Map.of("side", "bottom"));

        Component turned = normalized(narrow);

        assertThat(turned.rotation()).isEqualTo(Rotation.R90);
        assertThat(side(turned)).isEqualTo("right");
        assertThat(pin(turned)).isEqualTo(pin(narrow));
    }

    @Test
    void partsAlreadyInTheirFormAndOtherTypesAreLeftAlone() {
        Component upright = part(StandardComponentTypes.SWITCH, Rotation.R0, Map.of("width", 8, "side", "top"));
        Component narrow = part(StandardComponentTypes.LED, Rotation.R270, Map.of());
        Component gate = part(StandardComponentTypes.AND, Rotation.R90, Map.of());

        assertThat(normalized(upright)).isSameAs(upright);
        assertThat(normalized(narrow)).isSameAs(narrow);
        assertThat(normalized(gate)).isSameAs(gate);
    }
}
