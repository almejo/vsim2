package cl.almejo.vsim.ui.project;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The extension puts the panel above the palette of a view and takes it back when the view is disposed. */
class ProjectPanelExtensionTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<String> entered = new ArrayList<>();
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public void enter(OpenDocument document, String definitionId, Optional<String> contextPath,
                          Optional<ViewState> from) {
            entered.add(definitionId + "@" + contextPath.orElse("-"));
        }
    };

    private EditorView view() {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new ProjectPanelExtension()));
        return new EditorView(openDocument, services, window);
    }

    private static ProjectPanel panelIn(Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof ProjectPanel panel) {
                return panel;
            }
            if (child instanceof Container nested) {
                ProjectPanel found = panelIn(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void theViewHasTheProjectPanelAboveItsPaletteAndEnteringGoesToTheWindow() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        String instanceId = place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 4, -1,
                Map.of()).id().value();
        EditorView view = view();
        try {
            view.setSize(900, 600);
            layOut(view);
            ProjectPanel panel = panelIn(view);

            assertThat(panel).isNotNull();
            assertThat(SwingUtilities.convertPoint(panel, 0, 0, view).y)
                    .isLessThan(SwingUtilities.convertPoint(view.palette(), 0, 0, view).y);

            panel.rowList().setSelectedIndex(1);
            panel.rowList().getActionMap().get("project.enter").actionPerformed(null);

            assertThat(entered).containsExactly(ExampleDefinitions.INVERTER + "@" + instanceId);
        } finally {
            view.dispose();
        }
    }

    @Test
    void disposingTheViewTakesTheListenerAndThePanelBack() {
        int before = openDocument.changeListenerCount();
        EditorView view = view();
        ProjectPanel panel = panelIn(view);

        view.dispose();

        assertThat(openDocument.changeListenerCount()).isEqualTo(before);
        assertThat(SwingUtilities.getAncestorOfClass(EditorView.class, panel)).isNull();
    }
}
