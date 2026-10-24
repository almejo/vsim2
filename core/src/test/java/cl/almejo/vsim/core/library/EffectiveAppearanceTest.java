package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Overrides change geometry without replacing the definition used for simulation. */
class EffectiveAppearanceTest {
    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentDefinition definition = ExampleDefinitions.inverter();

    EffectiveAppearanceTest() {
        document.putDefinition(definition);
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void eachRotationCompilesUsingTheOverriddenPins(Rotation rotation) {
        Component instance = instance(new ElementId("c20"), rotation);
        document.add(instance);
        GridPoint expected = instance.position().plus(rotation.apply(new GridPoint(0, 1)));
        Wire wire = new Wire(new ElementId("w30"), expected, expected.translate(-3, 0));
        document.add(wire);

        ComponentType effective = registry.typeOf(instance);
        Netlist netlist = new NetlistCompiler(registry).compile(document);

        assertThat(effective.size()).isEqualTo(new GridSize(6, 6));
        assertThat(effective.pinLocation(instance, 0)).isEqualTo(expected);
        assertThat(netlist.nets()).anySatisfy(net -> {
            assertThat(net.pins()).contains(new PinRef(instance.id(), 0));
            assertThat(net.wires()).contains(wire.id());
        });
        assertThat(SubcircuitTypes.definitionOf(effective)).containsSame(definition);
    }

    @Test
    void equalCanonicalOverridesShareOneTypeAndReplacedDefinitionsDoNotReuseIt() {
        Component first = instance(new ElementId("c20"), Rotation.R0);
        Component second = instance(new ElementId("c21"), Rotation.R0);
        ComponentType before = registry.typeOf(first);
        assertThat(registry.typeOf(second)).isSameAs(before);

        ComponentDefinition renamed = definition.withName("Nombre actualizado");
        document.putDefinition(renamed);

        ComponentType after = registry.typeOf(first);
        assertThat(after).isNotSameAs(before);
        assertThat(SubcircuitTypes.definitionOf(after)).containsSame(renamed);
    }

    @Test
    void aFrozenCopyOfTheRegistryResolvesTheSameOverride() {
        Component instance = instance(new ElementId("c20"), Rotation.R90);
        document.add(instance);
        CircuitDocument copy = document.copy();
        ComponentTypeRegistry copiedRegistry = registry.copy();
        copiedRegistry.addResolver(SubcircuitTypes.resolverFor(copy));

        assertThat(copiedRegistry.typeOf(instance).pins()).isEqualTo(registry.typeOf(instance).pins());
        assertThat(copiedRegistry.typeOf(instance).bounds(instance)).isEqualTo(registry.typeOf(instance).bounds(instance));
    }

    private Component instance(ElementId id, Rotation rotation) {
        ComponentAppearance appearance = new ComponentAppearance(new GridSize(6, 6), Optional.empty(), List.of(
                new PinPlacement(definition.pinout().get(1).source(), new GridPoint(6, 5)),
                new PinPlacement(definition.pinout().get(0).source(), new GridPoint(0, 1))));
        return new Component(id, SubcircuitTypes.typeIdOf(definition.id()), new GridPoint(10, 10), rotation,
                Map.of(), "").withAppearance(Optional.of(appearance));
    }
}
