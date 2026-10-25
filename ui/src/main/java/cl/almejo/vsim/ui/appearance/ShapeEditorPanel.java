package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.DraftHistory;
import cl.almejo.vsim.core.component.appearance.DraftProblem;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Window-free shape editor content; dialog lifetime and circuit application belong to its caller. */
@SuppressWarnings("serial")
public final class ShapeEditorPanel extends JPanel {
    private static final String MESSAGE_PREFIX = "shape.";
    private final ShapeEditRequest request;
    private final AppPreferences preferences;
    private final Supplier<ColorScheme> scheme;
    private final DraftHistory history;
    private SymbolDraft draft;
    private ShapeEditScope scope;
    private final ShapeCanvas canvas;
    private final JToolBar toolbar = new JToolBar();
    private final Map<String, AbstractButton> buttons = new LinkedHashMap<>();
    private final JLabel message = new JLabel(" ");
    private final JLabel scopeDescription = new JLabel();
    private final JButton accept = new JButton(Messages.text("shape.accept"));
    private final JComboBox<String> scopes;
    private final JList<String> pins;
    private final JSpinner width;
    private final JSpinner height;
    private final JCheckBox pinNames = new JCheckBox(Messages.text("shape.pinNames"));
    private final JPanel previews = new JPanel(new GridLayout(1, 2, 8, 0));
    private final List<Runnable> refreshers = new ArrayList<>();
    private boolean refreshing;
    private Runnable onAccept = () -> { };
    private Runnable onClose = () -> { };

    public ShapeEditorPanel(ShapeEditRequest request, AppPreferences preferences, Supplier<ColorScheme> scheme) {
        super(new BorderLayout(8, 8));
        this.request = request;
        this.preferences = preferences;
        this.scheme = scheme;
        this.scope = request.initialScope();
        this.draft = SymbolDraft.of(request.appearance(), request.definition().pinout(), locked(scope))
                .withDefinitionName(request.definition().name());
        this.history = new DraftHistory(draft);
        this.canvas = new ShapeCanvas(request.definition(), () -> draft, this::apply, this::preview, scheme, this::refresh);
        canvas.setSnap(preferences.shapeEditorSnap());
        scopes = new JComboBox<>(new String[]{Messages.format("shape.scope.instance", request.instanceName()),
                Messages.format("shape.scope.all", request.affectedInstances())});
        scopes.setSelectedIndex(scope == ShapeEditScope.INSTANCE ? 0 : 1);
        scopes.setVisible(request.switchable());
        scopes.addActionListener(event -> {
            if (!refreshing) { switchScope(scopes.getSelectedIndex() == 0
                    ? ShapeEditScope.INSTANCE : ShapeEditScope.CIRCUIT_DEFINITION); }
        });
        JPanel heading = new JPanel(new BorderLayout(8, 4));
        heading.add(scopes, BorderLayout.WEST); heading.add(scopeDescription, BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(heading, BorderLayout.NORTH); north.add(toolbar, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        JPanel side = new JPanel(new BorderLayout(4, 8));
        pins = new JList<>(request.definition().pinout().stream().map(this::pinDescription).toArray(String[]::new));
        pins.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && pins.getSelectedIndex() >= 0) {
                canvas.selectPin(request.definition().pinout().get(pins.getSelectedIndex()).source());
            }
        });
        side.add(new JScrollPane(pins), BorderLayout.CENTER);
        JPanel properties = new JPanel(new GridLayout(0, 2, 4, 6));
        width = new JSpinner(new SpinnerNumberModel(draft.appearance().size().width(), 1, 4096, 1));
        height = new JSpinner(new SpinnerNumberModel(draft.appearance().size().height(), 1, 4096, 1));
        properties.add(new JLabel(Messages.text("shape.width"))); properties.add(width);
        properties.add(new JLabel(Messages.text("shape.height"))); properties.add(height);
        properties.add(pinNames); properties.add(new JLabel());
        JComboBox<String> textSizes = new JComboBox<>(new String[]{Messages.text("shape.text.small"),
                Messages.text("shape.text.medium"), Messages.text("shape.text.large")});
        JComboBox<String> textAlignments = new JComboBox<>(new String[]{Messages.text("shape.text.left"),
                Messages.text("shape.text.center")});
        properties.add(new JLabel(Messages.text("shape.text.size"))); properties.add(textSizes);
        properties.add(new JLabel(Messages.text("shape.text.alignment"))); properties.add(textAlignments);
        JCheckBox dynamicName = new JCheckBox(Messages.text("shape.text.componentName"));
        properties.add(dynamicName); properties.add(new JLabel());
        textSizes.addActionListener(event -> editSelectedText(text -> new SymbolText(text.anchor(), text.text(),
                TextSize.values()[textSizes.getSelectedIndex()], text.alignment())));
        textAlignments.addActionListener(event -> editSelectedText(text -> new SymbolText(text.anchor(), text.text(),
                text.size(), TextAlignment.values()[textAlignments.getSelectedIndex()])));
        dynamicName.addActionListener(event -> editSelectedText(text -> new SymbolText(text.anchor(),
                dynamicName.isSelected() ? Optional.empty() : Optional.of(request.definition().name()),
                text.size(), text.alignment())));
        side.add(properties, BorderLayout.SOUTH);
        side.setPreferredSize(new Dimension(240, 240));
        add(side, BorderLayout.EAST);
        width.addChangeListener(event -> resize()); height.addChangeListener(event -> resize());
        pinNames.addActionListener(event -> { if (!refreshing) { apply(draft.showPinNames(pinNames.isSelected())); } });
        previews.add(new Preview(false)); previews.add(new Preview(true));
        JPanel footer = new JPanel(new BorderLayout(4, 4));
        footer.add(previews, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(8, 4));
        bottom.add(message, BorderLayout.CENTER);
        JPanel choices = new JPanel(); choices.add(accept);
        JButton cancel = new JButton(Messages.text("shape.cancel")); choices.add(cancel);
        accept.addActionListener(event -> acceptEdits());
        cancel.addActionListener(event -> onClose.run());
        bottom.add(choices, BorderLayout.EAST); footer.add(bottom, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildToolbar(); bindKeys(); refresh();
    }

    public ComponentAppearance appearance() { return draft.appearance(); }
    public ShapeEditResult result() { return new ShapeEditResult(scope, draft.appearance()); }
    public boolean hasChanges() { return canvas.operationActive() || !draft.appearance().equals(request.appearance()); }
    public boolean canAccept() { return draft.canAccept() && (!locked(scope) || compatibleGeometry()); }
    public JButton acceptButton() { return accept; }
    public JToolBar toolbar() { return toolbar; }
    public void onAccept(Runnable action) { onAccept = action; }
    public void onClose(Runnable action) { onClose = action; }

    private void acceptEdits() {
        if (canvas.operationActive()) { canvas.finishOperation(); }
        if (!canvas.operationActive() && canAccept()) { onAccept.run(); }
    }

    public void apply(SymbolDraft changed) {
        history.update(changed.withPinLock(locked(scope)));
        draft = history.current(); canvas.draftChanged(); refresh();
    }

    public void switchScope(ShapeEditScope next) {
        if (!request.switchable() || next == ShapeEditScope.LIBRARY_DEFINITION) { return; }
        if (locked(next) && !compatibleGeometry()) {
            message.setText(Messages.text("shape.scope.incompatible"));
            refreshing = true; scopes.setSelectedIndex(scope == ShapeEditScope.INSTANCE ? 0 : 1); refreshing = false;
            return;
        }
        scope = next;
        draft = draft.withPinLock(locked(scope));
        refresh();
    }

    public void undo() { history.undo(); draft = history.current().withPinLock(locked(scope)); canvas.draftChanged(); refresh(); }
    public void redo() { history.redo(); draft = history.current().withPinLock(locked(scope)); canvas.draftChanged(); refresh(); }

    private boolean compatibleGeometry() {
        if (!draft.appearance().size().equals(request.definition().size())) { return false; }
        return request.definition().pinout().stream().allMatch(pin -> draft.appearance().pins().stream().anyMatch(
                placement -> placement.source().equals(pin.source()) && placement.offset().equals(pin.spec().position())));
    }

    private boolean locked(ShapeEditScope candidate) {
        return candidate != ShapeEditScope.INSTANCE && request.definitionPinsLocked();
    }

    private void preview(SymbolDraft changed) { draft = changed; refresh(); }

    private String pinDescription(DefinitionPin pin) {
        return Messages.format("shape.pin.row", pin.spec().name(),
                Messages.text(MESSAGE_PREFIX + "direction." + pin.spec().direction().name().toLowerCase(java.util.Locale.ROOT)),
                pin.spec().width());
    }

    private void resize() {
        if (refreshing || draft.pinsLocked()) { return; }
        try { apply(draft.resizeBox(new GridSize((Integer) width.getValue(), (Integer) height.getValue()))); }
        catch (IllegalArgumentException exception) { refresh(); message.setText(Messages.text("shape.resize.blocked")); }
    }

    private void editSelectedText(java.util.function.UnaryOperator<SymbolText> change) {
        if (refreshing) { return; }
        SymbolDraft edited = draft;
        for (int index : draft.selection()) {
            if (draft.elements().get(index) instanceof SymbolText text) { edited = edited.editText(index, change.apply(text)); }
        }
        apply(edited);
    }

    private void buildToolbar() {
        toolbar.setFloatable(false);
        ButtonGroup tools = new ButtonGroup();
        for (ShapeTool tool : ShapeTool.values()) {
            if (tool == ShapeTool.ADD_NODE) { continue; }
            JToggleButton button = new JToggleButton(tool == ShapeTool.PAN ? Icons.get("pan", 24)
                    : new ShapeToolIcon(tool.name().toLowerCase(java.util.Locale.ROOT)));
            button.setToolTipText(Messages.text(tool.messageKey()) + " (" + tool.key() + ")"
                    + (tool == ShapeTool.RECTANGLE || tool == ShapeTool.ELLIPSE || tool == ShapeTool.LINE
                    || tool == ShapeTool.TRIANGLE ? " — " + Messages.text("shape.shift.hint") : ""));
            button.getAccessibleContext().setAccessibleName(Messages.text(tool.messageKey()));
            button.addActionListener(event -> { canvas.setTool(tool); canvas.requestFocusInWindow(); });
            button.setSelected(tool == ShapeTool.SELECT); tools.add(button); toolbar.add(button);
            bindCanvas(tool.key(), () -> button.doClick());
        }
        toolbar.addSeparator();
        JToggleButton addNode = new JToggleButton(new ShapeToolIcon("nodeAdd"));
        addNode.setToolTipText(Messages.text("shape.nodeAdd") + " (A)");
        addNode.getAccessibleContext().setAccessibleName(Messages.text("shape.nodeAdd"));
        addNode.addActionListener(event -> { canvas.setTool(ShapeTool.ADD_NODE); canvas.requestFocusInWindow(); });
        tools.add(addNode); toolbar.add(addNode); bindCanvas("A", addNode::doClick);
        toolButton("nodeRemove", new ShapeToolIcon("nodeRemove"), canvas::deleteSelection);
        toolButton("curve", new ShapeToolIcon("curve"), canvas::toggleCurve);
        toolButton("fill", new ShapeToolIcon("fill"), () -> {
            SymbolDraft edited = draft;
            for (int index : draft.selection()) {
                if (draft.elements().get(index) instanceof SymbolPath path && path.closed()) {
                    edited = edited.setFilled(index, !path.filled());
                }
            }
            apply(edited);
        });
        toolButton("shapes", new ShapeToolIcon("shapes"), this::templates);
        toolButton("front", new ShapeToolIcon("front"), () -> apply(draft.bringToFront()));
        toolButton("back", new ShapeToolIcon("back"), () -> apply(draft.sendToBack()));
        toolbar.addSeparator();
        JToggleButton magnet = new JToggleButton(Icons.get("snap", 24));
        magnet.setSelected(preferences.shapeEditorSnap());
        magnet.setToolTipText(Messages.text("shape.snap") + " (M)");
        magnet.getAccessibleContext().setAccessibleName(Messages.text("shape.snap"));
        magnet.addActionListener(event -> {
            preferences.setShapeEditorSnap(magnet.isSelected()); canvas.setSnap(magnet.isSelected());
        });
        toolbar.add(magnet); bindCanvas("M", magnet::doClick);
        toolButton("undo", Icons.get("undo", 24), this::undo);
        toolButton("redo", Icons.get("redo", 24), this::redo);
        toolbar.addSeparator();
        toolButton("zoomOut", Icons.get("zoom-out", 24), () -> canvas.setZoom(canvas.zoom() / 1.25));
        toolButton("zoomReset", Icons.get("zoom-reset", 24), () -> canvas.setZoom(1));
        toolButton("zoomIn", Icons.get("zoom-in", 24), () -> canvas.setZoom(canvas.zoom() * 1.25));
    }

    private void toolButton(String name, javax.swing.Icon icon, Runnable action) {
        JButton button = new JButton(icon);
        button.setToolTipText(Messages.text(MESSAGE_PREFIX + name));
        button.getAccessibleContext().setAccessibleName(Messages.text(MESSAGE_PREFIX + name));
        button.addActionListener(event -> { action.run(); canvas.requestFocusInWindow(); });
        buttons.put(name, button); toolbar.add(button);
    }

    private void templates() {
        JPopupMenu popup = ShapeTemplatesPopup.create(draft.appearance().size(), scheme,
                template -> apply(draft.applyTemplate(template)));
        popup.show(buttons.get("shapes"), 0, buttons.get("shapes").getHeight());
    }

    private void bindKeys() {
        bind("ESCAPE", () -> {
            switch (ShapeKeys.escape(canvas.operationActive(), canvas.hasSelection())) {
                case CANCEL_OPERATION -> canvas.cancelOperation();
                case CLEAR_SELECTION -> canvas.clearSelection();
                default -> onClose.run();
            }
        });
        bind("ENTER", () -> { if (canvas.operationActive()) { canvas.finishOperation(); }
            else if (draft.canAccept()) { onAccept.run(); } });
        bind("DELETE", canvas::deleteSelection);
        bind("LEFT", () -> canvas.moveSelection(-1, 0)); bind("RIGHT", () -> canvas.moveSelection(1, 0));
        bind("UP", () -> canvas.moveSelection(0, -1)); bind("DOWN", () -> canvas.moveSelection(0, 1));
        String modifier = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac") ? "meta " : "ctrl ";
        bind(modifier + "Z", this::undo); bind(modifier + "Y", this::redo);
        bind(modifier + "D", () -> apply(draft.duplicateSelection()));
        bind(modifier + "A", () -> apply(draft.selectAll()));
    }

    private void bind(String key, Runnable action) {
        KeyStroke stroke = KeyStroke.getKeyStroke(key);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, "shape." + key);
        getActionMap().put("shape." + key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void bindCanvas(String key, Runnable action) {
        canvas.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), "shape.canvas." + key);
        canvas.getActionMap().put("shape.canvas." + key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void refresh() {
        if (width == null || refreshing) { return; }
        refreshing = true;
        width.setValue(draft.appearance().size().width()); height.setValue(draft.appearance().size().height());
        width.setEnabled(!draft.pinsLocked()); height.setEnabled(!draft.pinsLocked());
        pinNames.setSelected(draft.appearance().drawing().map(drawing -> drawing.showPinNames()).orElse(true));
        scopeDescription.setText(scope == ShapeEditScope.LIBRARY_DEFINITION
                ? Messages.text("shape.scope.library") : draft.pinsLocked()
                ? Messages.format("shape.locked", request.lockReason()) : scope == ShapeEditScope.INSTANCE
                ? Messages.format("shape.scope.instance", request.instanceName())
                : Messages.format("shape.scope.all", request.affectedInstances()));
        accept.setEnabled(canAccept());
        List<DraftProblem> problems = draft.problems();
        message.setText(problems.isEmpty() ? " " : Messages.format(MESSAGE_PREFIX + "problem."
                + problems.getFirst().kind().name().toLowerCase(java.util.Locale.ROOT),
                String.join(", ", problems.getFirst().pins())));
        if (!buttons.isEmpty()) {
            buttons.get("undo").setEnabled(history.canUndo()); buttons.get("redo").setEnabled(history.canRedo());
            boolean segment = canvas.segmentSelected();
            boolean node = canvas.nodeSelected();
            buttons.get("nodeRemove").setEnabled(node);
            buttons.get("nodeRemove").setToolTipText(Messages.text(MESSAGE_PREFIX + "nodeRemove")
                    + (node ? "" : " — " + Messages.text("shape.node.required")));
            buttons.get("curve").setEnabled(segment);
            buttons.get("curve").setToolTipText(Messages.text(MESSAGE_PREFIX + "curve")
                    + (segment ? "" : " — " + Messages.text("shape.segment.required")));
            for (String name : List.of("fill", "front", "back")) {
                buttons.get(name).setEnabled(!draft.selection().isEmpty());
            }
            buttons.get("zoomReset").setToolTipText(Messages.format("shape.zoom.value", (int) Math.round(canvas.zoom() * 100)));
        }
        canvas.repaint(); previews.repaint(); refreshing = false;
    }

    private final class Preview extends JPanel {
        private final boolean alternate;
        Preview(boolean alternate) { this.alternate = alternate; setPreferredSize(new Dimension(200, 150)); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D surface = (Graphics2D) graphics.create();
            try {
                ColorScheme current = scheme.get();
                ColorScheme used = alternate ? (current.background().getRed() < 128 ? ColorSchemes.LIGHT : ColorSchemes.DARK) : current;
                surface.setColor(used.background()); surface.fillRect(0, 0, getWidth(), getHeight());
                surface.setColor(used.text());
                surface.drawString(Messages.text(alternate ? "shape.preview.other" : "shape.preview.current"), 8, 16);
                surface.translate(20, 28);
                ShapeCanvas.paintAppearance(surface, request.definition(), draft.appearance(), used, false);
            } finally { surface.dispose(); }
        }
    }
}
