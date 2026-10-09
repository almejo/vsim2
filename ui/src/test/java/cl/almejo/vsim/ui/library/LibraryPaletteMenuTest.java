package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static cl.almejo.vsim.ui.library.TemplateActionsTest.texts;
import static org.assertj.core.api.Assertions.assertThat;

class LibraryPaletteMenuTest {

    @TempDir
    Path directory;

    private final DefinitionBundle inverter = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
    private final DefinitionBundle doubleInverter = DefinitionBundle.of(ExampleDefinitions.doubleInverter(),
            id -> Optional.of(ExampleDefinitions.inverter()));
    private TemplateFixture fixture;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private JPopupMenu menuFor(DefinitionBundle bundle, boolean inLibrary) {
        return fixture.features.palette().menuFor(Optional.of(PalettePanel.UserComponent.of(bundle, inLibrary)))
                .orElseThrow();
    }

    private static void click(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && item.getText().equals(text)) {
                item.getAction().actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, ""));
                return;
            }
        }
        throw new AssertionError("no item " + text + " in " + texts(menu));
    }

    private Path libraryFile(String id) {
        return directory.resolve("library").resolve(id + ".vsimcomponent.json").toAbsolutePath().normalize();
    }

    @Test
    void eachKindOfEntryGetsItsOwnMenu() {
        assertThat(texts(menuFor(inverter, true))).containsExactly("Renombrar…", "Exportar…", "Mostrar en carpeta",
                "Eliminar de la librería", "---", "Importar componente…");
        assertThat(texts(menuFor(inverter, false)))
                .containsExactly("Agregar a mi librería", "---", "Importar componente…");
        assertThat(texts(fixture.features.palette().menuFor(Optional.empty()).orElseThrow()))
                .containsExactly("Importar componente…");
    }

    @Test
    void renameRewritesTheFileAndRefusesTakenNames() throws Exception {
        fixture.service.save(inverter);
        fixture.service.save(doubleInverter);

        fixture.dialogs.names.add(Optional.of("doble INVERSOR"));
        click(menuFor(inverter, true), "Renombrar…");
        assertThat(fixture.dialogs.errors).containsExactly("Ya existe un componente llamado «doble INVERSOR».");
        assertThat(fixture.library.find("inverter").orElseThrow().root().name()).isEqualTo("Inversor");

        fixture.dialogs.names.add(Optional.of(" Negador "));
        click(menuFor(inverter, true), "Renombrar…");
        assertThat(fixture.library.find("inverter").orElseThrow().root().name()).isEqualTo("Negador");
        assertThat(fixture.service.read(libraryFile("inverter")).root().name()).isEqualTo("Negador");
    }

    @Test
    void exportAndShowInFolderUseTheLibraryFile() throws Exception {
        fixture.service.save(inverter);
        Path target = directory.resolve("exported.vsimcomponent.json");
        fixture.dialogs.exportTargets.add(Optional.of(target));

        click(menuFor(inverter, true), "Exportar…");
        click(menuFor(inverter, true), "Mostrar en carpeta");

        assertThat(fixture.service.read(target)).isEqualTo(inverter);
        assertThat(fixture.dialogs.shownInFolder).containsExactly(libraryFile("inverter"));
    }

    @Test
    void removingALibraryComponentKeepsCircuitsThatUseIt() throws Exception {
        fixture.service.save(inverter);
        fixture.view.palette().choose("subcircuit:inverter");
        fixture.view.canvas().tool().mousePressed(fixture.view.canvas(), TestToolContext.at(4, 4));
        Component source = fixture.place(StandardComponentTypes.SWITCH, 0, 5, Map.of("value", "1")); // out on a
        Component led = fixture.place(StandardComponentTypes.LED, 7, 3, Map.of()); // in on y
        waitUntil(() -> fixture.stateOf(led).equals(new VisibleState.LedState(Value.ZERO)));

        fixture.dialogs.confirmations.add(false);
        click(menuFor(inverter, true), "Eliminar de la librería");
        assertThat(fixture.library.find("inverter")).isPresent();

        fixture.dialogs.confirmations.add(true);
        click(menuFor(inverter, true), "Eliminar de la librería");

        assertThat(fixture.library.find("inverter")).isEmpty();
        assertThat(libraryFile("inverter")).doesNotExist();
        assertThat(fixture.openDocument.document().definition("inverter")).contains(ExampleDefinitions.inverter());
        assertThat(fixture.openDocument.document().components()).hasSize(3);
        assertThat(fixture.openDocument.registry().find("subcircuit:inverter")).isPresent();
        assertThat(fixture.view.palette().visibleSections()).endsWith("Componentes de este circuito");
        assertThat(fixture.view.palette().visibleTypeIds()).contains("subcircuit:inverter");
        fixture.openDocument.execute(new ChangeParametersCommand(source.id(),
                fixture.openDocument.registry().require(StandardComponentTypes.SWITCH), Map.of("value", "0")));
        waitUntil(() -> fixture.stateOf(led).equals(new VisibleState.LedState(Value.ONE))); // still simulates
    }

    @Test
    void addingThisCircuitsComponentPutsItInTheLibrary() {
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        fixture.place("subcircuit:inverter", 30, 30, ""); // only used definitions are listed (preflight S5)
        assertThat(fixture.view.palette().visibleSections()).endsWith("Componentes de este circuito");

        click(menuFor(inverter, false), "Agregar a mi librería");

        assertThat(fixture.library.find("inverter")).contains(inverter);
        assertThat(libraryFile("inverter")).isRegularFile();
        assertThat(fixture.view.palette().visibleSections()).doesNotContain("Componentes de este circuito");
    }

    @Test
    void addingAComponentTheLibraryGainedMeanwhileAsksFirst() throws Exception {
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        fixture.place("subcircuit:inverter", 30, 30, "");
        JPopupMenu menu = menuFor(inverter, false);
        DefinitionBundle renamed = new DefinitionBundle(ExampleDefinitions.inverter().withName("Negador"), List.of());
        fixture.service.save(renamed); // another window added it after this menu was built

        fixture.dialogs.collisions.add(LibraryDialogs.ImportChoice.CANCEL);
        click(menu, "Agregar a mi librería");
        assertThat(fixture.dialogs.collisionNames).containsExactly("Inversor");
        assertThat(fixture.library.find("inverter")).contains(renamed);

        fixture.dialogs.collisions.add(LibraryDialogs.ImportChoice.REPLACE);
        click(menu, "Agregar a mi librería");
        assertThat(fixture.library.find("inverter")).contains(inverter);
        assertThat(fixture.dialogs.errors).isEmpty();
    }
}
