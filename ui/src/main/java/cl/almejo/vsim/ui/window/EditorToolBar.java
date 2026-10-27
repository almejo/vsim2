package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.WireTool;
import cl.almejo.vsim.ui.tools.ZoomTool;

import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The toolbar of an editor window: its buttons, the tool group and the buttons extensions add. */
final class EditorToolBar {

    private final EditorActions actions;
    private final JToolBar toolBar = new JToolBar();
    private final ButtonGroup toolButtons = new ButtonGroup();
    private final JToggleButton selectButton;
    private final JToggleButton wireButton;
    private final JToggleButton panButton;
    private final JToggleButton zoomButton;
    private final Map<Class<? extends Tool>, JToggleButton> extraToolButtons = new LinkedHashMap<>();

    EditorToolBar(EditorActions actions) {
        this.actions = actions;
        selectButton = new JToggleButton(actions.selectTool());
        wireButton = new JToggleButton(actions.wireTool());
        panButton = new JToggleButton(actions.panTool());
        zoomButton = new JToggleButton(actions.zoomTool());
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
        toolBar.add(button, indexOfToolButton(wireButton) + 1);
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

    /** The toolbar's button for tools of {@code toolType} added with {@link #addToolButton}, if any. */
    Optional<JToggleButton> toolButton(Class<? extends Tool> toolType) {
        return Optional.ofNullable(extraToolButtons.get(toolType));
    }

    /** Shows {@code tool} as the pressed tool button (none when it has no button). */
    void showTool(Tool tool) {
        switch (tool) {
            case SelectTool select -> selectButton.setSelected(true);
            case WireTool wire -> wireButton.setSelected(true);
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
        toolBar.add(actions.save());
        toolBar.addSeparator();
        toolBar.add(actions.undo());
        toolBar.add(actions.redo());
        toolBar.addSeparator();
        for (JToggleButton button : new JToggleButton[]{selectButton, wireButton, panButton, zoomButton}) {
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
    }
}
