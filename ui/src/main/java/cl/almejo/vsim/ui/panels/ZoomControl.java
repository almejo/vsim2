package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.DoubleUnaryOperator;

/**
 * The zoom control at the right end of the status bar (zoom spec §6): Alejar · a logarithmic slider · Acercar · the
 * percentage (type it or pick a preset) · «100 %» · Ver todo el circuito · Zoom a la selección. It shows and drives the
 * window's active pane through a {@link Target}; {@link #showZoom} never calls the target back (no feedback loop).
 */
public final class ZoomControl extends JPanel {

    /** The pane the control drives: the window's active pane, resolved on every call. */
    public interface Target {
        /** The active pane's zoom now. */
        double zoom();

        /** Zooms the active pane around its centre at once (the slider, its keys and the wheel). */
        void zoomAroundCenter(double zoom);

        /** Zooms the active pane around its centre, travelling when animations are on (a typed or picked value). */
        void zoomTo(double zoom);

        /** Gives the keyboard focus back to the active pane. */
        void returnFocus();
    }

    /** The window's zoom actions the small buttons run (and whose enabled state they follow). */
    public record Actions(Action zoomOut, Action zoomIn, Action zoomReset, Action zoomToFit,
                          Action zoomToSelection) {
        public Actions {
            Objects.requireNonNull(zoomOut, "zoomOut");
            Objects.requireNonNull(zoomIn, "zoomIn");
            Objects.requireNonNull(zoomReset, "zoomReset");
            Objects.requireNonNull(zoomToFit, "zoomToFit");
            Objects.requireNonNull(zoomToSelection, "zoomToSelection");
        }
    }

    /** The slider's widths: it is the part that gives way first in a narrow window. */
    public static final int SLIDER_MINIMUM_WIDTH = 60;
    public static final int SLIDER_PREFERRED_WIDTH = 120;
    private static final int ICON_SIZE = Icons.MENU_SIZE;
    private static final String OUTLINE = "JComponent.outline";
    private static final String EDITED_COMMAND = "comboBoxEdited";

    private final Target target;
    private final JButton zoomOutButton;
    private final JSlider slider = new CentreMarkedSlider();
    private final JButton zoomInButton;
    private final JComboBox<String> field = new JComboBox<>();
    private final JButton resetButton;
    private final JButton fitButton;
    private final JButton selectionButton;
    private final Action typeZoom;
    /** Set while the control moves its own slider or field, so their listeners do not zoom the pane. */
    private boolean updating;
    /** The user is typing in the field: the canvas does not overwrite the text meanwhile. */
    private boolean editing;
    /** The zoom the field shows (what a typed value is compared with). */
    private double shownZoom = 1.0;
    private JTextField editor;
    private final ActionListener enterListener = event -> applyTyped();
    private final FocusListener editorFocus = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent event) {
            leaveField();
        }
    };
    private final DocumentListener typing = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent event) {
            typed();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            typed();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            typed();
        }
    };

    public ZoomControl(Target target, Actions actions) {
        super(new MigLayout("insets 0, gap 2, novisualpadding", "", "[center]"));
        this.target = Objects.requireNonNull(target, "target");
        setOpaque(false);
        zoomOutButton = iconButton(actions.zoomOut(), "zoom-out");
        zoomInButton = iconButton(actions.zoomIn(), "zoom-in");
        resetButton = smallButton(actions.zoomReset());
        resetButton.setText(Messages.text("zoom.reset"));
        resetButton.setToolTipText(Messages.text("zoom.reset.tip"));
        resetButton.getAccessibleContext().setAccessibleName(Messages.text("zoom.reset.tip"));
        fitButton = iconButton(actions.zoomToFit(), "zoom-fit");
        selectionButton = iconButton(actions.zoomToSelection(), "zoom-selection");
        buildSlider();
        buildField();
        typeZoom = new AbstractAction(Messages.text("action.zoomField")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                focusField();
            }
        };
        add(zoomOutButton);
        add(slider, "w " + SLIDER_MINIMUM_WIDTH + ":" + SLIDER_PREFERRED_WIDTH + ":" + SLIDER_PREFERRED_WIDTH);
        add(zoomInButton);
        add(field, "wmin pref");
        add(resetButton);
        add(fitButton);
        add(selectionButton);
        showZoom(1.0);
    }

    private void buildSlider() {
        slider.setFocusable(true);
        slider.setOpaque(false);
        slider.setToolTipText(Messages.text("zoom.slider.tip"));
        slider.getAccessibleContext().setAccessibleName(Messages.text("zoom.slider.name"));
        slider.addChangeListener(event -> sliderMoved());
        // own keys instead of BasicSliderUI's unit scroll of 1 (spec §6.2)
        bindSliderKey(KeyEvent.VK_LEFT, "zoomStepOut", zoom -> zoom / Viewport.ZOOM_STEP);
        bindSliderKey(KeyEvent.VK_DOWN, "zoomStepOut", zoom -> zoom / Viewport.ZOOM_STEP);
        bindSliderKey(KeyEvent.VK_KP_LEFT, "zoomStepOut", zoom -> zoom / Viewport.ZOOM_STEP);
        bindSliderKey(KeyEvent.VK_KP_DOWN, "zoomStepOut", zoom -> zoom / Viewport.ZOOM_STEP);
        bindSliderKey(KeyEvent.VK_RIGHT, "zoomStepIn", zoom -> zoom * Viewport.ZOOM_STEP);
        bindSliderKey(KeyEvent.VK_UP, "zoomStepIn", zoom -> zoom * Viewport.ZOOM_STEP);
        bindSliderKey(KeyEvent.VK_KP_RIGHT, "zoomStepIn", zoom -> zoom * Viewport.ZOOM_STEP);
        bindSliderKey(KeyEvent.VK_KP_UP, "zoomStepIn", zoom -> zoom * Viewport.ZOOM_STEP);
        bindSliderKey(KeyEvent.VK_PAGE_UP, "zoomDouble", zoom -> zoom * 2);
        bindSliderKey(KeyEvent.VK_PAGE_DOWN, "zoomHalve", zoom -> zoom / 2);
        bindSliderKey(KeyEvent.VK_HOME, "zoomMinimum", zoom -> Viewport.MINIMUM_ZOOM);
        bindSliderKey(KeyEvent.VK_END, "zoomMaximum", zoom -> Viewport.MAXIMUM_ZOOM);
        slider.addMouseWheelListener(event -> {
            int notches = event.getWheelRotation();
            if (notches != 0) {
                zoomInstantly(target.zoom() * Math.pow(Viewport.ZOOM_STEP, -notches));
            }
        });
    }

    private void bindSliderKey(int keyCode, String name, DoubleUnaryOperator change) {
        slider.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        slider.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                zoomInstantly(change.applyAsDouble(target.zoom()));
            }
        });
    }

    private void buildField() {
        DefaultComboBoxModel<String> presets = new DefaultComboBoxModel<>();
        ZoomScale.presets().forEach(percent -> presets.addElement(Messages.format("zoom.percent", percent)));
        field.setModel(presets);
        field.setEditable(true);
        field.setPrototypeDisplayValue(Messages.format("zoom.percent", 4000));
        field.setMaximumRowCount(presets.getSize());
        field.setToolTipText(Messages.text("zoom.field.tip"));
        field.getAccessibleContext().setAccessibleName(Messages.text("zoom.field.name"));
        field.addActionListener(event -> {
            if (!updating && !EDITED_COMMAND.equals(event.getActionCommand())) {
                applyChosen(); // a preset picked, or the text committed when the field lost the focus
            }
        });
        // the look and feel replaces its editor on a theme change: follow it
        PropertyChangeListener editorFollower = event -> attachEditor();
        field.addPropertyChangeListener("editor", editorFollower);
        attachEditor();
    }

    private void attachEditor() {
        if (editor != null) {
            editor.removeActionListener(enterListener);
            editor.removeFocusListener(editorFocus);
            editor.getDocument().removeDocumentListener(typing);
        }
        editor = field.getEditor().getEditorComponent() instanceof JTextField textField ? textField : null;
        if (editor == null) {
            return;
        }
        editor.addActionListener(enterListener); // added after the combo's own: runs first
        editor.addFocusListener(editorFocus);
        editor.getDocument().addDocumentListener(typing);
        editor.getAccessibleContext().setAccessibleName(Messages.text("zoom.field.name"));
        editor.setToolTipText(field.getToolTipText());
        // Escape is the field's (it never reaches the tool, EditorView.bindEscapeToTheTool)
        String cancel = "zoomFieldCancel";
        editor.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancel);
        editor.getActionMap().put(cancel, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                cancelTyping();
            }
        });
    }

    private JButton iconButton(Action action, String iconName) {
        JButton button = smallButton(action);
        button.setIcon(Icons.get(iconName, ICON_SIZE));
        Object description = action.getValue(Action.SHORT_DESCRIPTION);
        Object name = action.getValue(Action.NAME);
        button.setToolTipText(String.valueOf(description != null ? description : name));
        button.getAccessibleContext().setAccessibleName(String.valueOf(name));
        return button;
    }

    /**
     * A flat button that runs {@code action} and follows its enabled state, without taking the action's text or
     * toolbar icon (the status bar is small). Not focusable, like the toolbar's: each action has a menu item and keys.
     */
    private static JButton smallButton(Action action) {
        JButton button = new JButton();
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFocusable(false);
        button.setMargin(new Insets(1, 3, 1, 3));
        button.setEnabled(action.isEnabled());
        button.addActionListener(action);
        action.addPropertyChangeListener(event -> {
            if ("enabled".equals(event.getPropertyName())) {
                button.setEnabled(action.isEnabled());
            }
        });
        return button;
    }

    /**
     * Shows {@code zoom} (the active pane changed it, or another pane became active) in the slider and the field
     * without zooming anything. While the user drags the thumb it stays under the pointer; while the user types, the
     * field keeps the text.
     */
    public void showZoom(double zoom) {
        updating = true;
        try {
            if (!slider.getValueIsAdjusting()) {
                slider.setValue(ZoomScale.sliderValue(zoom));
            }
            if (!editing) {
                showInField(zoom);
            }
        } finally {
            updating = false;
        }
    }

    private void showInField(double zoom) {
        boolean wasUpdating = updating;
        updating = true;
        try {
            shownZoom = zoom;
            String text = Messages.format("zoom.percent", ZoomScale.percent(zoom));
            field.setSelectedItem(text);
            if (!text.equals(field.getEditor().getItem())) {
                field.getEditor().setItem(text); // the model already held it while other text was typed
            }
            editing = false;
        } finally {
            updating = wasUpdating;
        }
    }

    private void sliderMoved() {
        if (updating) {
            return;
        }
        double zoom = ZoomScale.zoomOf(slider.getValue());
        int snapped = ZoomScale.sliderValue(zoom);
        if (snapped != slider.getValue()) {
            updating = true;
            try {
                slider.setValue(snapped); // the detent: the thumb sits on 100 %
            } finally {
                updating = false;
            }
        }
        showInField(zoom);
        target.zoomAroundCenter(zoom);
    }

    private void zoomInstantly(double zoom) {
        double clamped = ZoomScale.clamp(zoom);
        target.zoomAroundCenter(clamped);
        showZoom(clamped);
    }

    private void typed() {
        if (!updating) {
            editing = true;
        }
    }

    /** Enter in the field: a number travels there and the focus goes back to the pane; anything else is marked. */
    private void applyTyped() {
        if (apply(editor.getText())) {
            target.returnFocus();
        }
    }

    /** A preset picked from the list (or the text committed on leaving the field): travels there. */
    private void applyChosen() {
        apply(String.valueOf(field.getSelectedItem()));
    }

    /** Travels to the zoom {@code text} says; false (and the error outline) when it is not a number. */
    private boolean apply(String text) {
        OptionalDouble parsed = ZoomScale.parsePercent(text);
        if (parsed.isEmpty()) {
            markInvalid(true);
            return false;
        }
        markInvalid(false);
        double zoom = parsed.getAsDouble();
        boolean changed = ZoomScale.percent(zoom) != ZoomScale.percent(shownZoom);
        showInField(zoom); // a clamped value shows clamped (1000 → «400 %»)
        if (changed) {
            target.zoomTo(zoom);
        }
        return true;
    }

    /** Escape: the field shows the current zoom again and the focus goes back to the pane (or the list closes). */
    private void cancelTyping() {
        if (field.isPopupVisible()) {
            field.setPopupVisible(false);
            return;
        }
        markInvalid(false);
        showInField(target.zoom());
        target.returnFocus();
    }

    /** The focus left the field: what was typed applies when it is a number; otherwise the current zoom comes back. */
    private void leaveField() {
        if (editing && ZoomScale.parsePercent(editor.getText()).isPresent()) {
            apply(editor.getText());
        } else if (editing || field.getClientProperty(OUTLINE) != null) {
            markInvalid(false);
            showInField(target.zoom());
        }
    }

    private void markInvalid(boolean invalid) {
        field.putClientProperty(OUTLINE, invalid ? "error" : null);
        String tip = Messages.text(invalid ? "zoom.field.invalid" : "zoom.field.tip");
        field.setToolTipText(tip);
        if (editor != null) {
            editor.setToolTipText(tip);
        }
        field.repaint();
    }

    private void focusField() {
        if (editor == null) {
            field.requestFocusInWindow();
            return;
        }
        editor.requestFocusInWindow();
        editor.selectAll();
    }

    /** The percentage the field shows («125 %»): what {@link StatusBar#text()} reports for the zoom. */
    public String text() {
        return editor != null ? editor.getText() : String.valueOf(field.getSelectedItem());
    }

    /** «Escribir el zoom…» (Ver menu): the keyboard reaches the percentage, selected to be typed over. */
    public Action typeZoomAction() {
        return typeZoom;
    }

    public JSlider slider() {
        return slider;
    }

    public JComboBox<String> field() {
        return field;
    }

    public JButton zoomOutButton() {
        return zoomOutButton;
    }

    public JButton zoomInButton() {
        return zoomInButton;
    }

    public JButton resetButton() {
        return resetButton;
    }

    public JButton fitButton() {
        return fitButton;
    }

    public JButton selectionButton() {
        return selectionButton;
    }

    /** A slider with a small, low-contrast mark at 100 % (its middle), coloured by the theme shown now. */
    private static final class CentreMarkedSlider extends JSlider {

        private static final int MARK_HEIGHT = 3;

        CentreMarkedSlider() {
            super(ZoomScale.MINIMUM_VALUE, ZoomScale.MAXIMUM_VALUE, 0);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Color mark = UIManager.getColor("Slider.tickColor");
            if (mark == null) {
                mark = UIManager.getColor("Label.disabledForeground");
            }
            if (mark == null) {
                return;
            }
            Insets insets = getInsets();
            int centre = insets.left + (getWidth() - insets.left - insets.right) / 2;
            int bottom = getHeight() - insets.bottom - 1;
            graphics.setColor(mark);
            graphics.drawLine(centre, bottom - MARK_HEIGHT + 1, centre, bottom);
        }
    }
}
