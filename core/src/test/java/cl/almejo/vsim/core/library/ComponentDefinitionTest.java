package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentDefinitionTest {

    private final ComponentDefinition inverter = ExampleDefinitions.inverter();
    private final ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();

    private static ComponentDefinition withId(ComponentDefinition source, String id) {
        return new ComponentDefinition(id, source.name(), source.description(), source.size(), source.pinout(),
                source.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Has Space", "a/b", "-start", "end-", "UPPER", "acentó"})
    void idsMustBeSlugs(String id) {
        assertThatThrownBy(() -> withId(inverter, id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namesAreStrippedAndMustNotBeBlank() {
        assertThat(inverter.withName("  Negador  ").name()).isEqualTo("Negador");
        assertThatThrownBy(() -> inverter.withName("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pinsMustPointAtTheMatchingInterfaceComponent() {
        DefinitionPin wrongSource = new DefinitionPin(PinSpec.input("a", 0, 2), new ElementId("c2"));
        assertThatThrownBy(() -> new ComponentDefinition("bad", "Malo", "", new GridSize(4, 4),
                List.of(wrongSource), inverter.body()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input-pin");
        DefinitionPin outputAtInput = new DefinitionPin(PinSpec.output("y", 4, 2), new ElementId("c1"));
        assertThatThrownBy(() -> new ComponentDefinition("bad", "Malo", "", new GridSize(4, 4),
                List.of(outputAtInput), inverter.body()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDefinitionMayHaveNoPins() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        // «Crear componente» warns first («El componente no tendrá entradas ni salidas») and may then create it.
        ComponentDefinition closed = new ComponentDefinition("cerrado", "Cerrado", "", new GridSize(6, 2),
                List.of(), inverter.body());
        assertThat(closed.pinout()).isEmpty();
    }

    @Test
    void aBodyCannotCarryDefinitionsOfItsOwn() {
        CircuitFragment carrying = new CircuitFragment(inverter.body().components(), List.of(), List.of(),
                List.of(inverter));
        assertThatThrownBy(() -> new ComponentDefinition("bad", "Malo", "", new GridSize(4, 4),
                inverter.pinout(), carrying))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usedDefinitionIdsListsNestedDefinitionsOnce() {
        assertThat(inverter.usedDefinitionIds()).isEmpty();
        assertThat(doubleInverter.usedDefinitionIds()).containsExactly(ExampleDefinitions.INVERTER);
    }

    @Test
    void subcircuitTypesExposeThePinoutAndLeadBackToTheDefinition() {
        ComponentType type = SubcircuitTypes.typeOf(inverter);
        assertThat(type.id()).isEqualTo("subcircuit:inverter");
        assertThat(type.category()).isEqualTo(ComponentCategory.CUSTOM);
        assertThat(type.size()).isEqualTo(new GridSize(4, 4));
        assertThat(type.pins()).extracting(PinSpec::name).containsExactly("a", "y");
        assertThat(type.parameters()).isEmpty();
        assertThat(type.createBehavior(Map.of()).isInteractive()).isFalse();
        assertThat(SubcircuitTypes.definitionOf(type)).containsSame(inverter);
        assertThat(SubcircuitTypes.typeOf(ExampleDefinitions.inverter())).isEqualTo(type);
        assertThat(SubcircuitTypes.definitionIdOf("subcircuit:inverter")).contains("inverter");
        assertThat(SubcircuitTypes.definitionIdOf("subcircuit:")).isEmpty();
        assertThat(SubcircuitTypes.definitionIdOf("and2")).isEmpty();
    }

    @Test
    void bundlesListDependenciesFirstAndRejectGaps() {
        DefinitionBundle bundle = DefinitionBundle.of(doubleInverter,
                id -> id.equals(ExampleDefinitions.INVERTER) ? Optional.of(inverter) : Optional.empty());
        assertThat(bundle.dependencies()).containsExactly(inverter);
        assertThat(bundle.all()).containsExactly(inverter, doubleInverter);
        assertThatThrownBy(() -> new DefinitionBundle(doubleInverter, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown definition inverter");
        assertThatThrownBy(() -> new DefinitionBundle(inverter, List.of(inverter)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closureRejectsRecursionAndKnownClosureSkipsUnknownIds() {
        List<Component> components = new ArrayList<>(inverter.body().components());
        components.add(new Component(new ElementId("c9"), SubcircuitTypes.typeIdOf("loop"), new GridPoint(20, 20),
                Rotation.R0, Map.of(), ""));
        ComponentDefinition loop = new ComponentDefinition("loop", "Bucle", "", new GridSize(4, 4), inverter.pinout(),
                new CircuitFragment(components, List.of(), List.of()));
        assertThatThrownBy(() -> DefinitionGraph.closure(List.of("loop"), id -> Optional.of(loop)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recursive definition");
        assertThat(DefinitionGraph.knownClosure(List.of(ExampleDefinitions.DOUBLE_INVERTER, "missing"),
                id -> id.equals(ExampleDefinitions.DOUBLE_INVERTER) ? Optional.of(doubleInverter) : Optional.empty()))
                .containsExactly(doubleInverter);
    }

    @Test
    void eachPinIsExactlyAsWideAsItsInterfacePin() {
        ComponentDefinition byteBuffer = ExampleDefinitions.byteBuffer();
        assertThat(byteBuffer.pinout()).extracting(pin -> pin.spec().width()).containsExactly(8, 8);
        DefinitionPin wideOnNarrow = new DefinitionPin(PinSpec.input("a", 0, 2, 8), new ElementId("c1"));
        assertThatThrownBy(() -> new ComponentDefinition("bad", "Malo", "", new GridSize(4, 4),
                List.of(wideOnNarrow, inverter.pinout().get(1)), inverter.body()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 bits");
        DefinitionPin narrowOnWide = new DefinitionPin(PinSpec.input("a", 0, 2), new ElementId("c1"));
        assertThatThrownBy(() -> new ComponentDefinition("bad", "Malo", "", new GridSize(4, 4),
                List.of(narrowOnWide, byteBuffer.pinout().get(1)), byteBuffer.body()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
