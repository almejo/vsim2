package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.panels.PalettePanelAccess;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryPaletteTest {

    @TempDir
    Path directory;

    private final DefinitionBundle inverter = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
    private TemplateFixture fixture;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void sectionsComeInTheAgreedOrder() throws Exception {
        ComponentDefinition example = ExampleDefinitions.inverter();
        ComponentDefinition spare = new ComponentDefinition("spare", "Sobrante", "", example.size(), example.pinout(),
                example.body());
        fixture.service.save(inverter);
        putAndPlace(spare);

        assertThat(fixture.view.palette().visibleSections()).containsExactly("Compuertas", "Cableado",
                "Aritmética", "Multiplexores", "Secuenciales", "Memoria", "Entrada/Salida", "Depuración",
                "Mis componentes", "Componentes de este circuito");
        assertThat(fixture.view.palette().visibleTypeIds())
                .endsWith("subcircuit:inverter", "subcircuit:spare");
    }

    @Test
    void placingALibraryComponentIsOneUndoStep() throws Exception {
        fixture.service.save(inverter);

        fixture.view.palette().choose("subcircuit:inverter");
        assertThat(fixture.view.canvas().tool()).isInstanceOf(PlaceDefinitionTool.class);
        fixture.view.canvas().tool().mousePressed(fixture.view.canvas(), TestToolContext.at(4, 4));

        CircuitDocument document = fixture.openDocument.document();
        assertThat(document.definitions()).containsExactly(ExampleDefinitions.inverter());
        assertThat(document.components()).singleElement()
                .extracting(Component::position).isEqualTo(new GridPoint(4, 4));
        assertThat(fixture.dialogs.messages).isEmpty();
        fixture.openDocument.history().undo();
        assertThat(document.definitions()).isEmpty();
        assertThat(document.components()).isEmpty();
    }

    /** Integration ruling (preflight C8): placing a library component keeps its palette entry highlighted. */
    @Test
    void thePaletteKeepsTheLibraryComponentHighlightedWhilePlacing() throws Exception {
        fixture.service.save(inverter);

        fixture.view.palette().choose("subcircuit:inverter");

        assertThat(fixture.view.canvas().tool()).isInstanceOf(PlaceDefinitionTool.class);
        assertThat(PalettePanelAccess.hasSelection(fixture.view.palette())).isTrue();
        fixture.view.canvas().useDefaultTool();
        assertThat(PalettePanelAccess.hasSelection(fixture.view.palette())).isFalse();
    }

    /** Bug 2026-10-18: after a click in the palette the focus stays in its tree, and Escape must still cancel. */
    @Test
    void escapeWithTheFocusInThePaletteCancelsPlacingALibraryComponent() throws Exception {
        fixture.service.save(inverter);
        fixture.view.palette().choose("subcircuit:inverter");

        boolean consumed = javax.swing.SwingUtilities.processKeyBindings(new java.awt.event.KeyEvent(
                PalettePanelAccess.tree(fixture.view.palette()), java.awt.event.KeyEvent.KEY_PRESSED, 0, 0,
                java.awt.event.KeyEvent.VK_ESCAPE, java.awt.event.KeyEvent.CHAR_UNDEFINED));

        assertThat(consumed).isTrue();
        assertThat(fixture.view.canvas().tool()).isSameAs(fixture.view.canvas().defaultTool());
        assertThat(PalettePanelAccess.hasSelection(fixture.view.palette())).isFalse();
        assertThat(fixture.openDocument.document().components()).isEmpty();
        assertThat(fixture.openDocument.document().definitions()).isEmpty();
        assertThat(fixture.openDocument.history().canUndo()).isFalse();
    }

    /** Preflight S3: the circuit keeps its own version of a nested definition; the user is told so. */
    @Test
    void placingWarnsWhenTheCircuitHasAnotherVersionOfANestedComponent() throws Exception {
        ComponentDefinition original = ExampleDefinitions.inverter();
        ComponentDefinition otherVersion = new ComponentDefinition(original.id(), original.name(), "otra versión",
                original.size(), original.pinout(), original.body());
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(otherVersion)));
        fixture.service.save(DefinitionBundle.of(ExampleDefinitions.doubleInverter(),
                id -> Optional.of(ExampleDefinitions.inverter())));

        fixture.view.palette().choose("subcircuit:double-inverter");

        assertThat(fixture.dialogs.messages).containsExactly(
                "En este circuito ya hay otra versión de «Inversor»: se mantiene la del circuito.");
        fixture.view.canvas().tool().mousePressed(fixture.view.canvas(), TestToolContext.at(4, 4));
        assertThat(fixture.openDocument.document().definition(original.id())).contains(otherVersion);
    }

    /** Undoing the placement removes what it added and nothing else: the circuit's own version stays. */
    @Test
    void undoingAPlacementOverAnotherVersionKeepsTheCircuitsVersion() throws Exception {
        ComponentDefinition original = ExampleDefinitions.inverter();
        ComponentDefinition otherVersion = new ComponentDefinition(original.id(), original.name(), "otra versión",
                original.size(), original.pinout(), original.body());
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(otherVersion)));
        fixture.service.save(DefinitionBundle.of(ExampleDefinitions.doubleInverter(),
                id -> Optional.of(ExampleDefinitions.inverter())));
        fixture.view.palette().choose("subcircuit:double-inverter");
        fixture.view.canvas().tool().mousePressed(fixture.view.canvas(), TestToolContext.at(4, 4));

        fixture.openDocument.history().undo();

        CircuitDocument document = fixture.openDocument.document();
        assertThat(document.components()).isEmpty();
        assertThat(document.definitions()).containsExactly(otherVersion);
    }

    /** Removing from the library never touches a circuit: the placed component becomes this circuit's own. */
    @Test
    void removingAUsedLibraryComponentListsItAsThisCircuitsOwn() throws Exception {
        fixture.service.save(inverter);
        fixture.view.palette().choose("subcircuit:inverter");
        fixture.view.canvas().tool().mousePressed(fixture.view.canvas(), TestToolContext.at(4, 4));
        assertThat(fixture.view.palette().visibleSections()).endsWith("Mis componentes");

        fixture.service.remove(ExampleDefinitions.INVERTER);

        assertThat(fixture.openDocument.document().definitions()).containsExactly(ExampleDefinitions.inverter());
        assertThat(fixture.view.palette().visibleSections()).doesNotContain("Mis componentes")
                .endsWith("Componentes de este circuito");
        assertThat(fixture.view.palette().visibleTypeIds()).contains("subcircuit:inverter");
    }

    @Test
    void everyWindowFollowsTheLibrary() throws Exception {
        try (TemplateFixture second = new TemplateFixture(fixture,
                OpenDocument.createEmpty(StandardComponentTypes.registry()))) {
            fixture.service.save(inverter);
            assertThat(fixture.view.palette().visibleTypeIds()).contains("subcircuit:inverter");
            assertThat(second.view.palette().visibleTypeIds()).contains("subcircuit:inverter");

            fixture.service.remove(ExampleDefinitions.INVERTER);
            assertThat(second.view.palette().visibleTypeIds()).doesNotContain("subcircuit:inverter");
        }
    }

    @Test
    void definitionsTheLibraryLacksAreListedAsThisCircuitsOwn() {
        putAndPlace(ExampleDefinitions.inverter());
        assertThat(fixture.view.palette().visibleSections()).endsWith("Componentes de este circuito");
        assertThat(fixture.view.palette().visibleTypeIds()).contains("subcircuit:inverter");

        fixture.openDocument.history().undo();

        assertThat(fixture.view.palette().visibleTypeIds()).doesNotContain("subcircuit:inverter");
    }

    /** Preflight S5: an unused embedded definition vanishes on save, so the palette does not offer it. */
    @Test
    void unusedDefinitionsOfTheCircuitAreNotListed() {
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));

        assertThat(fixture.view.palette().visibleTypeIds()).doesNotContain("subcircuit:inverter");
    }

    /** Preflight S6: an edit that touches no definition leaves the palette's tree (and what the user folded) alone. */
    @Test
    void anEditThatTouchesNoDefinitionKeepsThePaletteTree() throws Exception {
        fixture.service.save(inverter);
        putAndPlace(ExampleDefinitions.doubleInverter(), ExampleDefinitions.inverter());
        Object model = PalettePanelAccess.treeModel(fixture.view.palette());

        fixture.place(StandardComponentTypes.BUFFER, 20, 20, "");

        assertThat(PalettePanelAccess.treeModel(fixture.view.palette())).isSameAs(model);
    }

    @Test
    void aClosedWindowNoLongerFollowsTheLibrary() throws Exception {
        TemplateFixture second = new TemplateFixture(fixture, OpenDocument.createEmpty(StandardComponentTypes.registry()));
        second.close();

        fixture.service.save(inverter);

        assertThat(second.view.palette().visibleTypeIds()).doesNotContain("subcircuit:inverter");
    }

    /** Puts {@code root} (with {@code dependencies}) and places one instance of it: one undo step. */
    private void putAndPlace(ComponentDefinition root, ComponentDefinition... dependencies) {
        List<ComponentDefinition> definitions = new ArrayList<>(List.of(dependencies));
        definitions.add(root);
        Component instance = SubcircuitTypes.typeOf(root).newComponent(
                fixture.openDocument.document().newComponentId(), new GridPoint(30, 30), Rotation.R0, Map.of(), "");
        fixture.openDocument.execute(new CompositeCommand("command.add", List.of(
                new PutDefinitionsCommand(definitions), new AddElementsCommand(List.of(instance)))));
    }
}
