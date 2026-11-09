package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.icons.Icons;

import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.Tool;

import cl.almejo.vsim.ui.tools.ZoomTool;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.Component;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The toolbar of an editor window: its buttons, the tool group and the buttons extensions add. */
final class EditorToolBar {

    private final EditorActions actions;
    private final JToolBar toolBar = new JToolBar();
    private final ButtonGroup toolButtons = new ButtonGroup();
    private final JToggleButton selectButton;

    private final JToggleButton panButton;
    private final JToggleButton zoomButton;
    private final Map<Class<? extends Tool>, JToggleButton> extraToolButtons = new LinkedHashMap<>();
    private final Map<ToolbarSlot, List<SlotEntry>> slots = new EnumMap<>(ToolbarSlot.class);
    private final Component glue = Box.createHorizontalGlue();
    private Component saveButton;
    private Component speedSelector;

    private record SlotEntry(JComponent component, int order) {
    }

    EditorToolBar(EditorActions actions) {
        this.actions = actions;
        selectButton = new JToggleButton(actions.selectTool());
        panButton = new JToggleButton(actions.panTool());
        zoomButton = new JToggleButton(actions.zoomTool());
        for (ToolbarSlot slot : ToolbarSlot.values()) {
            slots.put(slot, new ArrayList<>());
        }
        build();
    }

    JToolBar toolBar() {
        return toolBar;
    }

    /** See {@link EditorView#addToolButton}. */
    Runnable addToolButton(Action action, Class<? extends Tool> toolType) {
        JToggleButton button = new JToggleButton(action);
        toolButtons.add(button);
        extraToolButtons.put(toolType, button);
        toolBar.add(button, indexOfToolButton(selectButton) + 1);
        toolBar.revalidate();
        return () -> {
            extraToolButtons.remove(toolType, button);
            toolButtons.remove(button);
            toolBar.remove(button);
            toolBar.revalidate();
        };
    }

    /** See {@link EditorView#addToolbarToggleAfterTools}. */
    Runnable addToolbarToggleAfterTools(JToggleButton button) {
        toolBar.add(button, indexOfToolButton(zoomButton) + 1);
        toolBar.revalidate();
        return () -> {
            toolBar.remove(button);
            toolBar.revalidate();
        };
    }

    /** See {@link EditorView#addToolbarComponent}. */
    Runnable addComponent(JComponent component, ToolbarSlot slot, int order) {
        List<SlotEntry> entries = slots.get(slot);
        int position = 0;
        while (position < entries.size() && entries.get(position).order <= order) {
            position++;
        }
        entries.add(position, new SlotEntry(component, order));
        toolBar.add(component, indexAfter(slot) + position);
        toolBar.revalidate();
        return () -> {
            entries.removeIf(entry -> entry.component == component);
            toolBar.remove(component);
            toolBar.revalidate();
            toolBar.repaint();
        };
    }

    /** The toolbar index right after the component the slot follows (the slot's own components come next). */
    private int indexAfter(ToolbarSlot slot) {
        Component anchor = switch (slot) {
            case AFTER_FILE -> saveButton;
            case AFTER_SIMULATION -> speedSelector;
            case RIGHT -> glue;
        };
        for (int index = 0; index < toolBar.getComponentCount(); index++) {
            if (toolBar.getComponent(index) == anchor) {
                return index + 1;
            }
        }
        return toolBar.getComponentCount();
    }

    /** The toolbar's button for tools of {@code toolType} added with {@link #addToolButton}, if any. */
    Optional<JToggleButton> toolButton(Class<? extends Tool> toolType) {
        return Optional.ofNullable(extraToolButtons.get(toolType));
    }

    /** Shows {@code tool} as the pressed tool button (none when it has no button). */
    void showTool(Tool tool) {
        switch (tool) {
            case SelectTool select -> selectButton.setSelected(true);

            case PanTool pan -> panButton.setSelected(true);
            case ZoomTool zoom -> zoomButton.setSelected(true);
            default -> {
                JToggleButton extra = extraToolButtons.get(tool.getClass());
                if (extra != null) {
                    extra.setSelected(true);
                } else {
                    toolButtons.clearSelection();
                }
            }
        }
    }

    private int indexOfToolButton(JToggleButton button) {
        for (int index = 0; index < toolBar.getComponentCount(); index++) {
            if (toolBar.getComponent(index) == button) {
                return index;
            }
        }
        return toolBar.getComponentCount() - 1;
    }

    private void build() {
        toolBar.setFloatable(false);
        toolBar.add(actions.newDocument());
        toolBar.add(actions.open());
        saveButton = toolBar.add(actions.save());
        toolBar.addSeparator();
        toolBar.add(actions.undo());
        toolBar.add(actions.redo());
        toolBar.addSeparator();
        for (JToggleButton button : new JToggleButton[]{selectButton, panButton, zoomButton}) {
            toolButtons.add(button);
            toolBar.add(button);
        }
        toolBar.addSeparator();
        toolBar.add(actions.start());
        toolBar.add(actions.pause());
        toolBar.add(actions.restart());
        toolBar.add(actions.step());
        JComboBox<Double> speed = actions.createSpeedSelector();
        JLabel speedIcon = new JLabel(Icons.get(EditorActions.SPEED_ICON, Icons.MENU_SIZE));
        speedIcon.setToolTipText(speed.getToolTipText());
        speedIcon.setLabelFor(speed);
        toolBar.add(speedIcon);
        toolBar.add(speed);
        speedSelector = speed;
        toolBar.add(glue);
    }
}
