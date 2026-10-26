package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** The catalog defaults are the strokes the editor really binds today (they are the same keys, in two places). */
class CatalogMatchesEditorTest {

    /** Defaults of actions an extension registers (M6.1-B's two advances); that front's own test checks the stroke. */
    private static final Set<String> REGISTERED_BY_EXTENSIONS = Set.of("simulation.advance",
            "simulation.advanceToClockEdge", "edit.tidyWires", "edit.tidyWiresOptions", "edit.frameSelection",
            "edit.wiresFollow", "edit.find", "edit.findNext", "edit.findPrevious", "edit.quickInsert", "view.snap");

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private Map<String, Action> windowActions() {
        Map<String, Action> actions = new HashMap<>();
        actions.put("file.new", view.actions().newDocument());
        actions.put("file.open", view.actions().open());
        actions.put("file.save", view.actions().save());
        actions.put("file.saveAs", view.actions().saveAs());
        actions.put("file.close", view.actions().closeWindow());
        actions.put("file.exit", view.actions().exit());
        actions.put("edit.undo", view.actions().undo());
        actions.put("edit.redo", view.actions().redo());
        actions.put("edit.cut", view.selectionActions().cut());
        actions.put("edit.copy", view.selectionActions().copy());
        actions.put("edit.paste", view.selectionActions().paste());
        actions.put("edit.duplicate", view.selectionActions().duplicate());
        actions.put("edit.selectAll", view.selectionActions().selectAll());
        actions.put("view.zoomIn", view.actions().zoomIn());
        actions.put("view.zoomOut", view.actions().zoomOut());
        actions.put("view.zoomReset", view.actions().zoomReset());
        actions.put("simulation.startOrPause", view.actions().startOrPause());
        actions.put("simulation.restart", view.actions().restart());
        actions.put("simulation.step", view.actions().step());
        return actions;
    }

    private Map<String, Action> canvasActions() {
        Map<String, Action> actions = new HashMap<>();
        actions.put("edit.delete", view.actions().delete());
        actions.put("edit.rotateClockwise", view.actions().rotateClockwise());
        actions.put("edit.rotateCounterClockwise", view.actions().rotateCounterClockwise());
        actions.put("edit.label", view.labelFeatures().actions().editLabel());
        actions.putAll(view.actions().moveActions());
        actions.put("view.zoomTool", view.actions().zoomTool());
        actions.put("view.zoomToFit", view.actions().zoomToFit());
        actions.put("view.zoomToSelection", view.actions().zoomToSelection());
        actions.put("view.nextProblem", view.problems().nextProblem());
        actions.put("view.previousProblem", view.problems().previousProblem());
        return actions;
    }

    @Test
    void everyWindowDefaultIsTheAcceleratorOfItsAction() {
        Map<String, Action> actions = windowActions();
        for (ShortcutEntry entry : ShortcutCatalog.entries()) {
            if (entry.scope() == Scope.WINDOW && entry.defaultStroke().isPresent()
                    && !REGISTERED_BY_EXTENSIONS.contains(entry.id())) {
                assertThat(actions).as("an action for " + entry.id()).containsKey(entry.id());
                assertThat(Optional.ofNullable((KeyStroke) actions.get(entry.id()).getValue(Action.ACCELERATOR_KEY)))
                        .as(entry.id()).isEqualTo(entry.defaultStroke());
            }
        }
    }

    @Test
    void everyCanvasDefaultIsBoundOnTheCanvasToItsAction() {
        Map<String, Action> actions = canvasActions();
        JComponent canvas = view.canvas();
        for (ShortcutEntry entry : ShortcutCatalog.entries()) {
            if (entry.scope() == Scope.CANVAS && entry.defaultStroke().isPresent()
                    && !REGISTERED_BY_EXTENSIONS.contains(entry.id())) {
                assertThat(actions).as("an action for " + entry.id()).containsKey(entry.id());
                Object name = canvas.getInputMap(JComponent.WHEN_FOCUSED).get(entry.defaultStroke().get());
                assertThat(name).as("binding of " + entry.id()).isNotNull();
                assertThat(canvas.getActionMap().get(name)).as(entry.id()).isSameAs(actions.get(entry.id()));
            }
        }
    }
}
