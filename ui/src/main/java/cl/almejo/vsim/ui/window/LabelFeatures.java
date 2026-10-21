package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.actions.LabelActions;
import cl.almejo.vsim.ui.actions.LabelMenuContributor;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.panels.PropertiesPanel;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

/** Label editing (F2, Edit menu, context menu) and the Properties tab of one editor view. */
public final class LabelFeatures {

    private final LabelActions actions;
    private final PropertiesPanel properties;
    private final Appearance appearance;
    private final Runnable repaintProperties;

    private LabelFeatures(LabelActions actions, PropertiesPanel properties, Appearance appearance) {
        this.actions = actions;
        this.properties = properties;
        this.appearance = appearance;
        this.repaintProperties = properties::repaint;
        appearance.addListener(repaintProperties);
    }

    public static LabelFeatures install(EditorView view) {
        CircuitCanvas canvas = view.canvas();
        LabelActions actions = new LabelActions(view.openDocument(), canvas.selection(), view.inlineEditor());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "editLabel", actions.editLabel());
        view.menu(MenuId.EDIT).add(actions.editLabel());
        view.addContextMenuContributor(new LabelMenuContributor(actions));
        PropertiesPanel properties = new PropertiesPanel(view.openDocument(), canvas.selection(),
                view.selectionMoves(), canvas::scheme);
        view.inspector().addTab(Messages.text("inspector.properties"), properties);
        // the colour swatches paint with the canvas's scheme: a new scheme in Preferencias repaints them
        return new LabelFeatures(actions, properties, view.services().appearance());
    }

    public LabelActions actions() {
        return actions;
    }

    public PropertiesPanel properties() {
        return properties;
    }

    public void dispose() {
        appearance.removeListener(repaintProperties);
        properties.dispose();
    }
}
