package cl.almejo.vsim.ui.project;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
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
    private final List<String> shown = new ArrayList<>();
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

        @Override
        public boolean goToPart(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath,
                                ElementId part) {
            shown.add(definitionId.orElse("-") + "@" + contextPath.orElse("-") + ":" + part.value());
            return true;
        }
    };

    private EditorView view() {
        return viewOf(openDocument);
    }

    private EditorView viewOf(OpenDocument place) {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new ProjectPanelExtension()));
        return new EditorView(place, services, window);
    }

    /** An inverter on the circuit (c1) and a double inverter (c2) holding two more (c2/c2, c2/c3). */
    private void inverterInThreePlaces() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0, Map.of());
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 30, 0, Map.of());
    }

    private static JMenuItem menuItem(ProjectPanel panel, String path, String text) {
        ProjectRows.Row row = panel.rows().stream()
                .filter(candidate -> candidate.instance().map(use -> use.path().equals(path)).orElse(false))
                .findFirst().orElseThrow();
        for (java.awt.Component item : panel.menuFor(row).orElseThrow().getComponents()) {
            if (item instanceof JMenuItem menuItem && menuItem.getText().equals(text)) {
                return menuItem;
            }
        }
        throw new AssertionError("no item " + text);
    }

    @Test
    void seePieceShowsEveryPartWhereItIsEvenFromAComponentTab() {
        inverterInThreePlaces();
        OpenDocument body = openDocument.openBody(ExampleDefinitions.INVERTER, Optional.of("c2/c3"));
        EditorView inside = viewOf(body);
        try {
            ProjectPanel panel = panelIn(inside);
            String seePiece = Messages.text("project.seePiece");

            JMenuItem topLevel = menuItem(panel, "c1", seePiece);
            JMenuItem nested = menuItem(panel, "c2/c3", seePiece);
            assertThat(topLevel.isEnabled()).isTrue();
            assertThat(nested.isEnabled()).isTrue();
            topLevel.doClick(0);
            nested.doClick(0);

            assertThat(shown).containsExactly("-@-:c1", ExampleDefinitions.DOUBLE_INVERTER + "@c2:c3");
        } finally {
            inside.dispose();
            body.close();
        }
    }

    @Test
    void changeShapeOpensTheShapeEditorOfThatPiece() {
        inverterInThreePlaces();
        EditorView view = view();
        List<ElementId> shaped = new ArrayList<>();
        Runnable removal = view.setInstanceShapeEditor(shaped::add);
        try {
            menuItem(panelIn(view), "c1", Messages.text("shape.change")).doClick(0);

            assertThat(shaped).containsExactly(new ElementId("c1"));
        } finally {
            removal.run();
            view.dispose();
        }
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
