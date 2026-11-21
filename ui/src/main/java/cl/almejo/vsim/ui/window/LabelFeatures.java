package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.actions.LabelActions;
import cl.almejo.vsim.ui.actions.LabelMenuContributor;
import cl.almejo.vsim.ui.actions.ShownAcceleratorItem;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.panels.PropertiesPanel;

import javax.swing.JMenu;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

/** Label editing (F2, Edit menu, context menu) and the Properties tab of one editor view. */
public final class LabelFeatures {

    private static final String EDIT_LABEL = "editLabel";

    private final LabelActions actions;
    private final PropertiesPanel properties;
    private final Appearance appearance;
    private final Runnable repaintProperties;
    private final Runnable paneBindings;

    private LabelFeatures(LabelActions actions, PropertiesPanel properties, Appearance appearance,
                          Runnable paneBindings) {
        this.actions = actions;
        this.properties = properties;
        this.appearance = appearance;
        this.paneBindings = paneBindings;
        this.repaintProperties = properties::repaint;
        appearance.addListener(repaintProperties);
    }

    public static LabelFeatures install(EditorView view) {
        LabelActions actions = new LabelActions(view.openDocument(), view.selection(), view.inlineEditor());
        KeyStroke editKey = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
        Runnable paneBindings = view.forEachPane(pane -> bindEditKey(pane, editKey, actions));
        // right after «Configurar…», as in the context menu (I13); F2 shown, the canvas binding fires it
        JMenu edit = view.menu(MenuId.EDIT);
        edit.insert(new ShownAcceleratorItem(actions.editLabel()), Menus.indexOf(edit, view.actions().configure()) + 1);
        view.addContextMenuContributor(new LabelMenuContributor(actions));
        PropertiesPanel properties = new PropertiesPanel(view.openDocument(), view.selection(),
                view.selectionMoves(), () -> view.canvas().scheme(), view.services().painters());
        properties.onNotice(view.statusBar()::showNotice);
        view.inspector().addTab(InspectorPanel.PROPERTIES, Messages.text("inspector.properties"),
                InspectorPanel.PROPERTIES_ICON, PropertiesPanel.inScrollPane(properties));
        view.inspector().showShortcut(InspectorPanel.PROPERTIES, view.inspectorToggleAction(InspectorPanel.PROPERTIES));
        // the colour swatches paint with the canvas's scheme: a new scheme in Preferencias repaints them
        return new LabelFeatures(actions, properties, view.services().appearance(), paneBindings);
    }

    private static Runnable bindEditKey(CircuitCanvas pane, KeyStroke editKey, LabelActions actions) {
        pane.bindKey(editKey, EDIT_LABEL, actions.editLabel());
        return () -> pane.unbindKey(editKey, EDIT_LABEL);
    }

    public LabelActions actions() {
        return actions;
    }

    public PropertiesPanel properties() {
        return properties;
    }

    public void dispose() {
        paneBindings.run();
        appearance.removeListener(repaintProperties);
        properties.dispose();
    }
}
