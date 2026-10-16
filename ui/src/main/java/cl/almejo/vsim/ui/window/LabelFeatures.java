package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.actions.LabelActions;
import cl.almejo.vsim.ui.actions.LabelMenuContributor;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

/** Label editing (F2, Edit menu, context menu) and the Properties tab of one editor view. */
public final class LabelFeatures {

    private final LabelActions actions;
    private final PropertiesPanel properties;

    private LabelFeatures(LabelActions actions, PropertiesPanel properties) {
        this.actions = actions;
        this.properties = properties;
    }

    public static LabelFeatures install(EditorView view) {
        CircuitCanvas canvas = view.canvas();
        LabelActions actions = new LabelActions(view.openDocument(), canvas.selection(), view.inlineEditor());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "editLabel", actions.editLabel());
        view.menu(MenuId.EDIT).add(actions.editLabel());
        view.addContextMenuContributor(new LabelMenuContributor(actions));
        PropertiesPanel properties = new PropertiesPanel(view.openDocument(), canvas.selection());
        view.inspector().addTab(Messages.text("inspector.properties"), properties);
        return new LabelFeatures(actions, properties);
    }

    public LabelActions actions() {
        return actions;
    }

    public PropertiesPanel properties() {
        return properties;
    }

    public void dispose() {
        properties.dispose();
    }
}
