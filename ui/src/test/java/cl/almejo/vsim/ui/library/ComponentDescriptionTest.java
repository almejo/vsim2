package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/** The description of a user component: written when it is created or updated, edited later from Propiedades. */
class ComponentDescriptionTest {

    @TempDir
    Path directory;

    private TemplateFixture fixture;
    private CircuitDocument document;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
        document = fixture.openDocument.document();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private Component createNegador(String description) {
        fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> {
            form.setName("Negador");
            form.setDescription(description);
        });
        perform(fixture.features.actions().createComponent());
        return fixture.single("subcircuit:negador");
    }

    private PropertiesPanel properties() {
        return fixture.view.labelFeatures().properties();
    }

    private static List<AbstractButton> buttons(Container container, String text) {
        List<AbstractButton> found = new ArrayList<>();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) {
                found.add(button);
            }
            if (child instanceof Container inner) {
                found.addAll(buttons(inner, text));
            }
        }
        return found;
    }

    private List<AbstractButton> editButtons() {
        return buttons(properties(), Messages.text("action.editDescription"));
    }

    @Test
    void theDescriptionWrittenWhenCreatingGoesInTheDefinitionAndTheLibrary() {
        createNegador("Invierte la entrada.");

        assertThat(document.definition("negador")).get().extracting(ComponentDefinition::description)
                .isEqualTo("Invierte la entrada.");
        assertThat(fixture.library.find("negador")).get()
                .satisfies(bundle -> assertThat(bundle.root().description()).isEqualTo("Invierte la entrada."));
    }

    @Test
    void updatingStartsFromTheOldDescriptionAndKeepsWhatTheUserWrites() {
        createNegador("Invierte la entrada.");
        fixture.drawPassThrough(20, 0);
        List<String> shown = new ArrayList<>();
        fixture.dialogs.acceptForm(form -> {
            shown.add(form.description());
            form.setDescription("Deja pasar la entrada.");
        });

        perform(fixture.features.actions().updateComponent());

        assertThat(shown).containsExactly("Invierte la entrada.");
        assertThat(document.definition("negador")).get().extracting(ComponentDefinition::description)
                .isEqualTo("Deja pasar la entrada.");
    }

    @Test
    void editingTheDescriptionFromPropertiesIsOneUndoStepAndRewritesTheLibrary() {
        Component instance = createNegador("");
        fixture.view.canvas().selection().select(instance.id());
        assertThat(editButtons()).hasSize(1);
        fixture.dialogs.descriptions.add(Optional.of("  Invierte la entrada.  "));
        long changes = fixture.openDocument.changeCount();

        editButtons().get(0).doClick();

        assertThat(fixture.dialogs.describedNames).containsExactly("Negador");
        assertThat(document.definition("negador")).get().extracting(ComponentDefinition::description)
                .isEqualTo("Invierte la entrada.");
        assertThat(fixture.openDocument.changeCount()).isEqualTo(changes + 1);
        assertThat(fixture.openDocument.history().undoDescription()).contains("command.changeDescription");
        assertThat(fixture.library.find("negador")).get()
                .satisfies(bundle -> assertThat(bundle.root().description()).isEqualTo("Invierte la entrada."));

        fixture.openDocument.history().undo();
        assertThat(document.definition("negador")).get().extracting(ComponentDefinition::description)
                .isEqualTo("");
    }

    @Test
    void cancellingOrKeepingTheSameTextChangesNothing() {
        Component instance = createNegador("Invierte.");
        fixture.view.canvas().selection().select(instance.id());
        long changes = fixture.openDocument.changeCount();

        editButtons().get(0).doClick();
        fixture.dialogs.descriptions.add(Optional.of("Invierte."));
        editButtons().get(0).doClick();

        assertThat(fixture.openDocument.changeCount()).isEqualTo(changes);
    }

    @Test
    void aBuiltInPartHasNoDescriptionToEdit() {
        Component gate = fixture.place(StandardComponentTypes.AND, 0, 0, "");
        fixture.view.canvas().selection().select(gate.id());

        assertThat(editButtons()).isEmpty();
    }
}
