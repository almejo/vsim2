package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class TemplatesEndToEndTest {

    private static final VisibleState LED_ZERO = new VisibleState.LedState(Value.ZERO);
    private static final VisibleState LED_ONE = new VisibleState.LedState(Value.ONE);

    @TempDir
    Path directory;

    @Test
    void createPlaceSaveReopenOnAnEmptyLibraryUpdateAndUndo() throws Exception {
        Path file = directory.resolve("negador.vsim");
        try (TemplateFixture author = new TemplateFixture(directory.resolve("library-a"))) {
            author.drawInverter(0, 0);
            author.dialogs.acceptForm(form -> form.setName("Negador"));
            perform(author.features.actions().createComponent());
            assertThat(author.view.palette().visibleTypeIds()).contains("subcircuit:negador");

            author.view.palette().choose("subcircuit:negador");
            author.view.canvas().tool().mousePressed(author.view.canvas(), TestToolContext.at(20, 0));
            author.place(StandardComponentTypes.SWITCH, -4, 1, Map.of("value", "1"));
            Component led = author.place(StandardComponentTypes.LED, 5, -1, Map.of());
            waitUntil(() -> author.stateOf(led).equals(LED_ZERO));

            author.openDocument.save(file);
        }

        OpenDocument reopened = OpenDocument.open(file, StandardComponentTypes.registry());
        try (TemplateFixture reader = new TemplateFixture(directory.resolve("library-b"), reopened)) {
            reader.service.reload();
            assertThat(reader.library.entries()).isEmpty();
            assertThat(reader.view.palette().visibleSections()).endsWith("Componentes de este circuito");
            assertThat(reader.view.palette().visibleTypeIds()).contains("subcircuit:negador");
            Component led = reader.single(StandardComponentTypes.LED);
            waitUntil(() -> reader.stateOf(led).equals(LED_ZERO));

            reader.drawPassThrough(40, 0);
            reader.dialogs.acceptForm(form -> {
            });
            perform(reader.features.actions().updateComponent());
            waitUntil(() -> reader.stateOf(led).equals(LED_ONE));
            assertThat(reader.library.entries()).isEmpty();

            reader.openDocument.history().undo();
            waitUntil(() -> reader.stateOf(led).equals(LED_ZERO));
            assertThat(reader.openDocument.document().components())
                    .filteredOn(component -> component.typeId().equals("subcircuit:negador"))
                    .hasSize(2);

            ComponentDefinition embedded = reader.openDocument.document().definition("negador").orElseThrow();
            JPopupMenu menu = reader.features.palette().menuFor(Optional.of(PalettePanel.UserComponent.of(
                    new DefinitionBundle(embedded, List.of()), false))).orElseThrow();
            JMenuItem addToLibrary = (JMenuItem) menu.getComponent(0);
            addToLibrary.getAction().actionPerformed(new ActionEvent(addToLibrary, ActionEvent.ACTION_PERFORMED, ""));
            assertThat(reader.library.find("negador")).contains(new DefinitionBundle(embedded, List.of()));
            assertThat(reader.view.palette().visibleSections()).endsWith("Mis componentes");
            waitUntil(() -> reader.stateOf(led).equals(LED_ZERO));
        }
    }
}
