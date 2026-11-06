package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.appearance.AppearanceExtension;
import cl.almejo.vsim.ui.appearance.AppearancePrompts;
import cl.almejo.vsim.ui.appearance.ConnectionsQuestion;
import cl.almejo.vsim.ui.appearance.ShapeEditResult;
import cl.almejo.vsim.ui.appearance.ShapeEditScope;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Commands that change a definition (its description, its shape for every piece, its version from the library), run
 * on a nested piece from a component tab, change the circuit's definition as one undo step, or are refused with the
 * same explanation as in the circuit's tab: never only the tab's working copy (final review C1).
 */
class DefinitionEditsFromATabTest {

    private static final ElementId NESTED = new ElementId("c2");

    @TempDir
    Path directory;

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private TemplateFixture fixture;

    @AfterEach
    void close() {
        if (fixture != null) {
            fixture.close();
        }
        circuit.close();
    }

    @Test
    void editDescriptionOfANestedPieceChangesTheCircuitsDefinitionAsOneUndoStep() {
        openTheDoubleInverterTab();
        fixture.dialogs.descriptions.add(Optional.of("nueva"));

        fixture.features.actions().editDescription(nested());

        assertThat(circuitInverter().description()).isEqualTo("nueva");
        assertThat(tabInverter().description()).isEqualTo("nueva");
        circuit.history().undo();
        assertThat(circuitInverter().description()).isEmpty();
        assertThat(tabInverter().description()).isEmpty();
    }

    @Test
    void updateFromTheLibraryOfANestedPieceReachesTheCircuit() throws Exception {
        openTheDoubleInverterTab();
        ComponentDefinition before = circuitInverter();
        ComponentDefinition newer = before.withDescription("de la librería").revisedFrom(before);
        fixture.service.save(new DefinitionBundle(newer, List.of()));

        fixture.features.actions().updateFromLibrary(nested());

        assertThat(circuitInverter().description()).isEqualTo("de la librería");
        assertThat(tabInverter().description()).isEqualTo("de la librería");
        assertThat(fixture.features.actions().isOutdated(nested())).isFalse();
        circuit.history().undo();
        assertThat(circuitInverter()).isEqualTo(before);
    }

    @Test
    void aShapeForEveryPieceOfANestedPieceChangesTheCircuitsDefinitionAsOneUndoStep() {
        // the ALU drawing keeps the inverter's size and pins, so the circuit takes it even though it is nested
        openTheDoubleInverterTab();
        ComponentDefinition before = circuitInverter();
        Runnable remove = new AppearanceExtension(fixture.service, fixture.dialogs, Optional.of((owner, request) ->
                Optional.of(new ShapeEditResult(ShapeEditScope.CIRCUIT_DEFINITION, SymbolDraft.of(
                        request.appearance(), request.definition().pinout(), false)
                        .applyTemplate(SymbolTemplates.Template.ALU).appearance()))), new AcceptingPrompts())
                .install(fixture.view);
        try {
            clickItem(menu(nested()), Messages.text("shape.change"));

            assertThat(circuitInverter().appearance()).isPresent();
            assertThat(tabInverter()).isEqualTo(circuitInverter());
            circuit.history().undo();
            assertThat(circuitInverter()).isEqualTo(before);
            assertThat(tabInverter()).isEqualTo(before);
        } finally {
            remove.run();
        }
    }

    @Test
    void aComponentCreatedInATabWithoutReplacingTheSelectionGoesToTheCircuit() {
        openTheDoubleInverterTab();
        fixture.select(fixture.openDocument.document().components().toArray(Component[]::new));
        fixture.dialogs.acceptForm(form -> {
            form.setName("Envoltura");
            form.setReplaceSelection(false);
        });

        fixture.features.actions().createFromSelection();

        assertThat(circuit.document().definitions()).extracting(ComponentDefinition::name).contains("Envoltura");
        circuit.history().undo();
        assertThat(circuit.document().definitions()).extracting(ComponentDefinition::name)
                .doesNotContain("Envoltura");
    }

    /** The inverter and the double inverter in the circuit, a double inverter c1 placed; its tab open on c1. */
    private void openTheDoubleInverterTab() {
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c1"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), new GridPoint(0, 0), Rotation.R0,
                Map.of(), ""))));
        fixture = new TemplateFixture(directory.resolve("library"),
                circuit.openBody(ExampleDefinitions.DOUBLE_INVERTER, Optional.of("c1")));
    }

    private Component nested() {
        return fixture.openDocument.document().component(NESTED).orElseThrow();
    }

    private ComponentDefinition circuitInverter() {
        return circuit.document().definition(ExampleDefinitions.INVERTER).orElseThrow();
    }

    private ComponentDefinition tabInverter() {
        return fixture.openDocument.document().definition(ExampleDefinitions.INVERTER).orElseThrow();
    }

    private JPopupMenu menu(Component instance) {
        fixture.select(instance);
        return fixture.view.contextMenu(Optional.of(instance), new Point2D.Double());
    }

    private static void clickItem(JPopupMenu menu, String text) {
        for (java.awt.Component component : menu.getComponents()) {
            if (component instanceof JMenuItem item && item.getText().equals(text)) {
                item.doClick();
                return;
            }
        }
        throw new AssertionError("Missing menu item " + text);
    }

    private static final class AcceptingPrompts implements AppearancePrompts {
        @Override
        public boolean confirmConnections(java.awt.Component owner, ConnectionsQuestion question) {
            return true;
        }

        @Override
        public boolean retryLibraryWrite(java.awt.Component owner, boolean applied) {
            return false;
        }

        @Override
        public boolean reopenStale(java.awt.Component owner) {
            return false;
        }
    }
}
