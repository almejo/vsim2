package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.read.ComponentTypeView.ParameterView;
import cl.almejo.vsim.automation.read.ComponentTypeView.PinSpecView;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogReaderTest {

    private final ReadFixture fixture = new ReadFixture();

    @Test
    void everyStandardTypeComesWithItsNamesAndTheFlipFlopWithItsPins() {
        CatalogView catalog = CatalogReader.describeAll(fixture.source(), Optional.empty());

        assertThat(catalog.document()).isEqualTo(ReadFixture.ID);
        assertThat(catalog.version().value()).isEqualTo(7);
        assertThat(catalog.types()).extracting(ComponentTypeView::typeId)
                .containsAll(fixture.registry.all().stream().map(type -> type.id()).toList());
        ComponentTypeView flipFlop = type(catalog, StandardComponentTypes.FLIP_FLOP_DATA);
        assertThat(flipFlop.spanishName()).isEqualTo("es flip-flop-data");
        assertThat(flipFlop.englishName()).isEqualTo("en flip-flop-data");
        assertThat(flipFlop.category()).isEqualTo("SEQUENTIAL");
        assertThat(flipFlop.userComponent()).isFalse();
        assertThat(flipFlop.pins()).containsExactly(
                new PinSpecView(0, "d", "INPUT", 1, false, new GridPoint(0, 2)),
                new PinSpecView(1, "q", "OUTPUT", 1, false, new GridPoint(4, 2)),
                new PinSpecView(2, "clock", "INPUT", 1, false, new GridPoint(2, 4)));
    }

    @Test
    void oneTypeComesWithTheDescriptionForItsParametersButTheCatalogueLeavesItOut() {
        ComponentTypeView nand = CatalogReader.describe(fixture.source(), StandardComponentTypes.AND,
                Map.of(StandardComponentTypes.NEGATE, true));
        CatalogView catalog = CatalogReader.describeAll(fixture.source(), Optional.empty());

        assertThat(nand.description()).isEqualTo("reglas de and negada");
        assertThat(catalog.types()).allSatisfy(type -> assertThat(type.description()).isEmpty());
    }

    @Test
    void withoutBundlesAUserComponentIsDescribedByItsDefinition() {
        ComponentNames names = new ComponentNames() {
            @Override
            public String spanish(ComponentType type) {
                return type.id();
            }

            @Override
            public String english(ComponentType type) {
                return type.id();
            }
        };
        ComponentType described = SubcircuitTypes.typeOf(ExampleDefinitions.inverter()
                .withDescription("Invierte la entrada."));

        assertThat(names.description(described, Map.of())).isEqualTo("Invierte la entrada.");
        assertThat(names.description(fixture.registry.require(StandardComponentTypes.AND), Map.of())).isEmpty();
    }

    @Test
    void aParameterSaysItsKindRangeAndDefault() {
        ComponentTypeView splitter = CatalogReader.describe(fixture.source(), StandardComponentTypes.BUS_SPLITTER,
                Map.of());

        assertThat(splitter.parameters()).containsExactly(
                new ParameterView("width", "INTEGER", 8, 2, 32, java.util.List.of(), false));
        assertThat(splitter.parameterValues()).containsEntry("width", 8);
    }

    @Test
    void theWidthParameterChangesThePinsDescribed() {
        ComponentTypeView eight = CatalogReader.describe(fixture.source(), StandardComponentTypes.BUS_SPLITTER,
                Map.of());
        ComponentTypeView sixteen = CatalogReader.describe(fixture.source(), StandardComponentTypes.BUS_SPLITTER,
                Map.of("width", 16));

        assertThat(eight.pins()).hasSize(9);
        assertThat(eight.pins().get(0).width()).isEqualTo(8);
        assertThat(sixteen.pins()).hasSize(17);
        assertThat(sixteen.pins().get(0)).isEqualTo(new PinSpecView(0, "bus", "INPUT", 16, false, GridPoint.ORIGIN));
        assertThat(sixteen.pins().get(16).name()).isEqualTo("b15");
        assertThat(sixteen.height()).isEqualTo(16);
        assertThat(sixteen.parameterValues()).containsEntry("width", 16);
    }

    @Test
    void theCircuitsOwnUserComponentsComeLastWithTheirPins() {
        fixture.document.putDefinition(ExampleDefinitions.byteBuffer());

        CatalogView catalog = CatalogReader.describeAll(fixture.source(), Optional.empty());

        ComponentTypeView last = catalog.types().get(catalog.types().size() - 1);
        assertThat(last.typeId()).isEqualTo(SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER));
        assertThat(last.userComponent()).isTrue();
        assertThat(last.definitionName()).isEqualTo("Búfer de 8 bits");
        assertThat(last.pins()).containsExactly(
                new PinSpecView(0, "a", "INPUT", 8, false, new GridPoint(0, 2)),
                new PinSpecView(1, "y", "OUTPUT", 8, false, new GridPoint(4, 2)));
    }

    @Test
    void theLibrarysComponentsTheCircuitLacksComeAfterItsOwn() {
        fixture.document.putDefinition(ExampleDefinitions.inverter());
        fixture.useLibrary(List.of(new DefinitionBundle(ExampleDefinitions.inverter(), List.of()),
                new DefinitionBundle(ExampleDefinitions.byteBuffer(), List.of())));

        CatalogView catalog = CatalogReader.describeAll(fixture.source(), Optional.empty());

        List<ComponentTypeView> users = catalog.types().stream().filter(ComponentTypeView::userComponent).toList();
        assertThat(users).extracting(ComponentTypeView::typeId).containsExactly(
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER));
        assertThat(users).extracting(ComponentTypeView::origin).containsExactly(
                ComponentTypeView.Origin.CIRCUIT, ComponentTypeView.Origin.LIBRARY);
        assertThat(type(catalog, StandardComponentTypes.FLIP_FLOP_DATA).origin())
                .isEqualTo(ComponentTypeView.Origin.BUILT_IN);
        ComponentTypeView described = CatalogReader.describe(fixture.source(),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), Map.of());
        assertThat(described.origin()).isEqualTo(ComponentTypeView.Origin.LIBRARY);
        assertThat(described.definitionName()).isEqualTo("Búfer de 8 bits");
        assertThat(described.pins()).extracting(PinSpecView::width).containsExactly(8, 8);
    }

    @Test
    void theFilterMatchesTheIdTheNamesOrTheCategory() {
        fixture.document.putDefinition(ExampleDefinitions.inverter());

        assertThat(CatalogReader.describeAll(fixture.source(), Optional.of("FLIP")).types())
                .extracting(ComponentTypeView::typeId).containsExactly(StandardComponentTypes.FLIP_FLOP_DATA);
        assertThat(CatalogReader.describeAll(fixture.source(), Optional.of("inversor")).types())
                .extracting(ComponentTypeView::typeId)
                .containsExactly(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER));
        assertThat(CatalogReader.describeAll(fixture.source(), Optional.of("sequential")).types())
                .extracting(ComponentTypeView::category).containsOnly("SEQUENTIAL").isNotEmpty();
    }

    @Test
    void anUnknownTypeOrAWrongParameterIsRefusedWithWhatToDo() {
        assertThatThrownBy(() -> CatalogReader.describe(fixture.source(), "flux-capacitor", Map.of()))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
        assertThatThrownBy(() -> CatalogReader.describe(fixture.source(), StandardComponentTypes.BUS_SPLITTER,
                Map.of("width", 99)))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        assertThatThrownBy(() -> CatalogReader.describe(fixture.source(), StandardComponentTypes.BUS_SPLITTER,
                Map.of("colour", 3)))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
    }

    private static ComponentTypeView type(CatalogView catalog, String typeId) {
        return catalog.types().stream().filter(type -> type.typeId().equals(typeId)).findFirst().orElseThrow();
    }

    @Test
    void aBooleanParameterIsExportedAsKindBooleanWithoutARange() {
        fixture.registry.register(new ComponentType("test-flagged", ComponentCategory.GATES, new GridSize(2, 2),
                List.of(PinSpec.output("out", 2, 1)), List.of(ParameterSpec.bool("negate", false)),
                parameters -> (context, pinIndex) -> {
                }));

        ComponentTypeView flagged = CatalogReader.describe(fixture.source(), "test-flagged", Map.of("negate", "TRUE"));

        assertThat(flagged.parameters()).containsExactly(
                new ParameterView("negate", "BOOLEAN", false, null, null, List.of(), false));
        assertThat(flagged.parameterValues()).containsEntry("negate", true);
    }

    @Test
    void aGateWithEightInputsListsNineAndItsWidthAndNegateAreParameters() {
        ComponentTypeView gate = CatalogReader.describe(fixture.source(), StandardComponentTypes.AND,
                Map.of("inputs", 8));

        assertThat(gate.pins()).hasSize(9);
        assertThat(gate.pins().getLast().name()).isEqualTo("out");
        assertThat(gate.parameters()).extracting(ParameterView::name).contains("inputs", "width", "negate");
    }
}
