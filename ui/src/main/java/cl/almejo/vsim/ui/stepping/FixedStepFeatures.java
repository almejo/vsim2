package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The configurable fixed step in one editor view (M6.1-B, spec §4.4): «Avanzar N t» follows the preference
 * {@code simulation.stepUnits}; the toolbar gets the arrow of {@link StepSizeButton} right after its button and
 * Simulación gets «Tamaño del avance…» right after its item. {@link #dispose()} takes it all back. EDT only.
 */
public final class FixedStepFeatures {

    /** Client property on the arrow and the menu item: they belong to the fixed step, B's controls go after them. */
    static final String COMPANION = "vsim.fixedStepCompanion";
    private static final KeyStroke F6 = KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0);

    private final EditorView view;
    private final AppPreferences preferences;
    private final StepSizeChooser chooser;
    private final Action stepSizeAction;
    private final JMenuItem stepSizeItem;
    private final StepSizeButton arrow;
    private final Runnable preferenceListener = this::followPreference;
    private final Runnable shortcutRemover;
    private final List<Runnable> keyRestorers = new ArrayList<>();
    private boolean disposed;

    public FixedStepFeatures(EditorView view, StepSizeChooser chooser) {
        this.view = view;
        this.preferences = view.services().preferences();
        this.chooser = chooser;
        this.stepSizeAction = new AbstractAction(Messages.text("action.stepSize")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                askForASize();
            }
        };
        this.stepSizeItem = new JMenuItem(stepSizeAction);
        stepSizeItem.putClientProperty(COMPANION, Boolean.TRUE);
        JMenu simulation = view.menu(MenuId.SIMULATION);
        simulation.insert(stepSizeItem, indexAfter(simulation.getMenuComponents(), view.actions().step()));
        this.arrow = new StepSizeButton(preferences::setStepUnits, this::askForASize);
        arrow.putClientProperty(COMPANION, Boolean.TRUE);
        JToolBar toolBar = view.toolBar();
        toolBar.add(arrow, indexAfter(toolBar.getComponents(), view.actions().step()));
        this.shortcutRemover = view.registerShortcutAction("simulation.stepSize", stepSizeAction);
        releaseF6(view);
        preferences.addListener(preferenceListener);
        followPreference();
    }

    StepSizeButton arrow() {
        return arrow;
    }

    /** Right after the component whose action is {@code step}; at the end when there is none. */
    private static int indexAfter(Component[] children, Action step) {
        for (int index = 0; index < children.length; index++) {
            if (children[index] instanceof AbstractButton button && button.getAction() == step) {
                return index + 1;
            }
        }
        return children.length;
    }

    /** «Otro…» and «Tamaño del avance…»: the dialog starts at the current size; a cancel changes nothing. */
    private void askForASize() {
        chooser.choose(view, preferences.stepUnits()).ifPresent(preferences::setStepUnits);
    }

    private void followPreference() {
        if (!disposed) {
            view.actions().setStepUnits(preferences.stepUnits());
        }
    }

    /**
     * The split panes around the canvas bind F6 to "toggleFocus" for the focused component's ancestors, which comes
     * before menu accelerators: «none» lets F6 reach «Avanzar N t». {@link #dispose()} gives F6 back.
     */
    private void releaseF6(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JSplitPane split) {
                InputMap keys = split.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
                keys.put(F6, "none");
                keyRestorers.add(() -> keys.remove(F6));
            }
            if (child instanceof Container nested) {
                releaseF6(nested);
            }
        }
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        preferences.removeListener(preferenceListener);
        shortcutRemover.run();
        keyRestorers.forEach(Runnable::run);
        view.menu(MenuId.SIMULATION).remove(stepSizeItem);
        JToolBar toolBar = view.toolBar();
        toolBar.remove(arrow);
        toolBar.revalidate();
        toolBar.repaint();
        view.actions().setStepUnits(AppPreferences.DEFAULT_STEP_UNITS);
    }
}
