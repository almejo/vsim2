package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JSpinner;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** Profiles in Propiedades: choosing one, each «↺» and «Volver al perfil» are one undo step each (spec §7). */
class PropertiesPanelProfilesTest {

    private OpenDocument openDocument;
    private final Selection selection = new Selection();
    private PropertiesPanel panel;
    private Component screen;

    @BeforeEach
    void create() {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        registry.register(ParameterFormProfilesTest.profiledType());
        openDocument = OpenDocument.createEmpty(registry);
        panel = new PropertiesPanel(openDocument, selection);
        screen = place(openDocument, "screen", 0, 0, Map.of("history", 50, "glow", false));
        selection.select(screen.id());
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    private Map<String, Object> parameters() {
        return openDocument.document().component(screen.id()).orElseThrow().parameters();
    }

    @Test
    void eachRestoreIsOneUndoStepNamedAsSuch() {
        panel.parameterForm().restoreButton("history").doClick();

        assertThat(parameters()).containsEntry("history", 0).containsEntry("glow", false);
        assertThat(openDocument.history().undoDescription()).contains("command.restoreParameter");

        panel.parameterForm().restoreButton("glow").doClick();
        assertThat(parameters()).containsEntry("glow", true);

        openDocument.history().undo();
        assertThat(parameters()).containsEntry("history", 0).containsEntry("glow", false);
        openDocument.history().undo();
        assertThat(parameters()).containsEntry("history", 50);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void choosingAProfileIsOneUndoStep() {
        ((JComboBox<?>) panel.parameterForm().editor(ParameterFormProfilesTest.PROFILE)).setSelectedItem("modern");

        assertThat(parameters()).containsEntry(ParameterFormProfilesTest.PROFILE, "modern")
                .containsEntry("history", 1000).containsEntry("width", 8).containsEntry("glow", false);
        assertThat(openDocument.history().undoDescription()).contains("command.applyProfile");

        openDocument.history().undo();
        assertThat(parameters()).containsEntry(ParameterFormProfilesTest.PROFILE, "classic")
                .containsEntry("history", 50).containsEntry("width", 7);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void backToTheProfileIsOneUndoStepAndTheUndoShowsTheDifferencesAgain() {
        panel.parameterForm().restoreProfileButton().doClick();

        assertThat(parameters()).containsEntry("history", 0).containsEntry("glow", true);
        assertThat(openDocument.history().undoDescription()).contains("command.applyProfile");
        assertThat(panel.parameterForm().profileSummary().getText()).isEqualTo("Igual al perfil");

        openDocument.history().undo();
        assertThat(panel.parameterForm().profileSummary().getText()).isEqualTo("2 propiedades distintas del perfil");
    }

    @Test
    void anEditByHandIsStillAConfigureStep() {
        ((JSpinner) panel.parameterForm().editor("history")).setValue(0);

        assertThat(parameters()).containsEntry("history", 0);
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
    }
}
