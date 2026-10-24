package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppearanceModelTest {

    private final GridSize size = new GridSize(4, 4);

    @Test
    void anOrderedDrawingKeepsTextsBetweenPathsAndCopiesItsElements() {
        SymbolText text = new SymbolText(new SymbolPoint(8, 8), Optional.of("A"), TextSize.SMALL,
                TextAlignment.CENTER);
        SymbolPath path = line();
        List<SymbolElement> elements = new ArrayList<>(List.of(path, text, path));
        SymbolDrawing drawing = new SymbolDrawing(size, elements, true);
        elements.clear();
        assertThat(drawing.elements()).containsExactly(path, text, path);
        assertThat(drawing.paths()).containsExactly(path, path);
        assertThat(drawing.texts()).containsExactly(text);
        assertThatThrownBy(() -> drawing.elements().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void drawingsRejectControlsOutsideTheirBoxAndFilledOpenPaths() {
        SymbolPath outside = new SymbolPath(List.of(new SymbolNode(new SymbolPoint(0, 0),
                Optional.of(new SymbolPoint(33, 0))), new SymbolNode(new SymbolPoint(8, 8), Optional.empty())),
                false, false);
        assertThatThrownBy(() -> new SymbolDrawing(size, List.of(outside), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SymbolPath(line().nodes(), false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDefaultBoxWithCustomPinsDiffersFromAnIntentionallyEmptySymbol() {
        ComponentAppearance defaultBox = new ComponentAppearance(size, Optional.empty(), List.of());
        ComponentAppearance emptySymbol = new ComponentAppearance(size, Optional.of(SymbolDrawing.empty(size)),
                List.of());
        assertThat(defaultBox).isNotEqualTo(emptySymbol);
    }

    @Test
    void canonicalPinOrderFollowsTheDefinitionAndRejectsMissingOrCoincidentPins() {
        ComponentDefinition definition = ExampleDefinitions.inverter();
        PinPlacement first = new PinPlacement(definition.pinout().get(0).source(), new GridPoint(0, 1));
        PinPlacement last = new PinPlacement(definition.pinout().get(1).source(), new GridPoint(4, 1));
        ComponentAppearance appearance = new ComponentAppearance(size, Optional.empty(), List.of(last, first));
        assertThat(appearance.canonical(definition.pinout()).pins()).containsExactly(first, last);
        assertThatThrownBy(() -> new ComponentAppearance(size, Optional.empty(), List.of(first))
                .canonical(definition.pinout())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComponentAppearance(size, Optional.empty(), List.of(first,
                new PinPlacement(last.source(), first.offset()))).canonical(definition.pinout()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void componentEditsPreserveTheirAppearanceWithoutChangingTheirDefinitionRevision() {
        ComponentAppearance appearance = new ComponentAppearance(size, Optional.empty(), List.of());
        Component component = new Component(new ElementId("c1"), "subcircuit:box", new GridPoint(0, 0),
                Rotation.R0, Map.of(), "").withAppearance(Optional.of(appearance));
        assertThat(component.withPosition(new GridPoint(3, 4)).withRotation(Rotation.R90).withLabel("A")
                .withParameters(Map.of()).withLabelOffset(Optional.empty()).appearance()).contains(appearance);
    }

    @Test
    void onlyChangedDefinitionContentAdvancesItsRevision() {
        ComponentDefinition definition = ExampleDefinitions.inverter();
        assertThat(definition.revision()).isEqualTo(1);
        assertThat(definition.withName(definition.name()).revision()).isEqualTo(1);
        ComponentDefinition changed = definition.withAppearance(Optional.of(SymbolDrawing.empty(size)));
        assertThat(changed.revision()).isEqualTo(2);
        assertThat(changed.withAppearance(changed.appearance()).revision()).isEqualTo(2);
        assertThat(changed.withGeometry(size, changed.pinout(), changed.appearance())).isEqualTo(changed);
        assertThat(changed.withName("New name").revision()).isEqualTo(3);
        assertThatThrownBy(() -> new ComponentDefinition(definition.id(), definition.name(), "", size,
                definition.pinout(), definition.body(), Optional.empty(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SymbolPath line() {
        return new SymbolPath(List.of(new SymbolNode(new SymbolPoint(0, 0), Optional.empty()),
                new SymbolNode(new SymbolPoint(8, 8), Optional.empty())), false, false);
    }
}
