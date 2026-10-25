package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.DraftHistory;
import cl.almejo.vsim.core.component.appearance.DraftProblem;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.theme.SegmentedSwitch;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The «Cambiar forma» editor's content, laid out as mockup screens 2–3: the scope line (a switch from a placed piece,
 * fixed text from the palette), the icon toolbar in the spec's groups, the canvas with its hint or warnings line, the
 * side panel (Pines, Tamaño, the pin-names switch, the Texto group while a text is chosen, Vista previa) and the
 * footer with the status and Cancelar · Aceptar. Window-free: the dialog's lifetime and applying the result belong to
 * its caller.
 */
@SuppressWarnings("serial")
public final class ShapeEditorPanel extends JPanel {
    private static final String MESSAGE_PREFIX = "shape.";
    /** The toolbar glyph size of the mockups (16 px in 28 px buttons), smaller than the main window's 24 px. */
    private static final int TOOL_ICON_SIZE = 16;
    /** The side panel's width in the mockups. */
    private static final int SIDE_WIDTH = 270;
    private static final int PREVIEW_HEIGHT = 150;
    private static final int LARGEST_BOX = 4096;
    private static final int SPINNER_COLUMNS = 3;
    private static final double ZOOM_STEP = 1.25;
    /** A closed figure needs three nodes: a line of two cannot be closed. */
    private static final int CLOSABLE_NODES = 3;

    private final ShapeEditRequest request;
    private final AppPreferences preferences;
    private final Supplier<ColorScheme> scheme;
    private final DraftHistory history;
    private SymbolDraft draft;
    private ShapeEditScope scope;
    private ShapeTool activeTool = ShapeTool.SELECT;
    private Optional<String> notice = Optional.empty();
    private final ShapeCanvas canvas;
    private final JToolBar toolbar = new JToolBar();
    private final Map<String, AbstractButton> buttons = new LinkedHashMap<>();
    private final Map<ShapeTool, JToggleButton> toolButtons = new LinkedHashMap<>();
    private final JButton zoomValue = new JButton();
    private final JButton accept = new JButton(Messages.text("shape.accept"));
    private final ToneLabel status = new ToneLabel(ToneLabel.Tone.PLAIN);
    private final List<JToggleButton> scopeSegments = new ArrayList<>();
    private final ToneLabel scopeTitle = new ToneLabel(ToneLabel.Tone.PLAIN);
    private final ToneLabel scopeDetail = new ToneLabel(ToneLabel.Tone.INFO);
    private final JPanel messages = new Surface(new MigLayout("wrap 1, insets 6 10 6 10, gap 2", "[grow, fill]"));
    private List<Line> shownLines = List.of();
    private final PinTable pins = new PinTable();
    private Supplier<Component> focusOwner = () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    private int canvasFocusRequests;
    private final JSpinner width;
    private final JSpinner height;
    private final JCheckBox pinNames = new JCheckBox(Messages.text("shape.pinNames"));
    private final JPanel textGroup = new JPanel(new MigLayout("wrap 2, insets 0 10 4 10, gap 6 4", "[][grow, fill]"));
    private final JComboBox<String> textSizes = new JComboBox<>(new String[]{Messages.text("shape.text.small"),
            Messages.text("shape.text.medium"), Messages.text("shape.text.large")});
    private final JComboBox<String> textAlignments = new JComboBox<>(new String[]{Messages.text("shape.text.left"),
            Messages.text("shape.text.center")});
    private final JCheckBox componentName = new JCheckBox(Messages.text("shape.text.componentName"));
    private final JPanel previews = new JPanel(new MigLayout("insets 4 10 10 10, gap 8",
            "[grow, fill, sg card][grow, fill, sg card]", "[" + PREVIEW_HEIGHT + "!]"));
    private boolean refreshing;
    private Runnable onAccept = () -> { };
    private Runnable onClose = () -> { };

    public ShapeEditorPanel(ShapeEditRequest request, AppPreferences preferences, Supplier<ColorScheme> scheme) {
        super(new BorderLayout());
        this.request = request;
        this.preferences = preferences;
        this.scheme = scheme;
        this.scope = request.initialScope();
        this.draft = SymbolDraft.of(request.appearance(), request.definition().pinout(), locked(scope))
                .withDefinitionName(request.definition().name());
        this.history = new DraftHistory(draft);
        this.canvas = new ShapeCanvas(request.definition(), () -> draft, this::apply, this::preview, scheme,
                this::refresh);
        canvas.setSnap(preferences.shapeEditorSnap());
        // The in-place text field counts as a change while it is open, so «Aceptar» must follow it.
        canvas.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent event) {
                refresh();
            }

            @Override
            public void componentRemoved(ContainerEvent event) {
                refresh();
            }
        });
        width = new JSpinner(new SpinnerNumberModel(draft.appearance().size().width(), 1, LARGEST_BOX, 1));
        height = new JSpinner(new SpinnerNumberModel(draft.appearance().size().height(), 1, LARGEST_BOX, 1));
        JPanel top = new JPanel(new BorderLayout());
        top.add(scopeBar(), BorderLayout.NORTH);
        top.add(toolbar, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        buildToolbar();
        bindKeys();
        refresh();
    }

    public ComponentAppearance appearance() {
        return draft.appearance();
    }

    public ShapeEditResult result() {
        return new ShapeEditResult(scope, draft.appearance());
    }

    /** A gesture in progress, another drawing or another scope than the one it opened with. */
    public boolean hasChanges() {
        return canvas.operationActive() || scope != request.initialScope()
                || !draft.appearance().equals(request.appearance());
    }

    public boolean canAccept() {
        return draft.canAccept() && (!locked(scope) || compatibleGeometry());
    }

    public JButton acceptButton() {
        return accept;
    }

    public JToolBar toolbar() {
        return toolbar;
    }

    public void onAccept(Runnable action) {
        onAccept = action;
    }

    public void onClose(Runnable action) {
        onClose = action;
    }

    public void apply(SymbolDraft changed) {
        notice = Optional.empty();
        history.update(changed.withPinLock(locked(scope)));
        draft = history.current();
        canvas.draftChanged();
        refresh();
    }

    public void switchScope(ShapeEditScope next) {
        if (!request.switchable() || next == ShapeEditScope.LIBRARY_DEFINITION) {
            return;
        }
        if (locked(next) && !compatibleGeometry()) {
            notice = Optional.of(Messages.text("shape.scope.incompatible"));
            refresh();
            return;
        }
        scope = next;
        notice = Optional.empty();
        draft = draft.withPinLock(locked(scope));
        refresh();
    }

    /** Undo; while a multiline or polygon is being drawn, takes back its last node instead (one click, one undo). */
    public void undo() {
        if (canvas.drawingPath()) {
            canvas.removeLastNode();
            return;
        }
        history.undo();
        draft = history.current().withPinLock(locked(scope));
        canvas.draftChanged();
        refresh();
    }

    public void redo() {
        if (canvas.drawingPath()) {
            return;
        }
        history.redo();
        draft = history.current().withPinLock(locked(scope));
        canvas.draftChanged();
        refresh();
    }

    // --- Layout -------------------------------------------------------------------------------------------------

    private JComponent scopeBar() {
        JPanel bar = new Surface(new MigLayout("insets 8 12 8 12, gap 12, hidemode 3", "[][grow]", "[]"));
        bar.setBorder(new EdgeBorder(Set.of(EdgeBorder.Side.BOTTOM), new Insets(0, 0, 0, 0)));
        if (request.switchable()) {
            JToggleButton instance = new SegmentedSwitch.Segment(
                    Messages.format("shape.scope.instance", request.instanceName()));
            JToggleButton all = new SegmentedSwitch.Segment(
                    Messages.format("shape.scope.all", request.affectedInstances()));
            instance.addActionListener(event -> switchScope(ShapeEditScope.INSTANCE));
            all.addActionListener(event -> switchScope(ShapeEditScope.CIRCUIT_DEFINITION));
            scopeSegments.add(instance);
            scopeSegments.add(all);
            bar.add(SegmentedSwitch.withNaturalWidths(instance, all));
        } else {
            scopeTitle.setText(ShapeEditorTexts.scopeTitle(scope));
            scopeTitle.putClientProperty("FlatLaf.style", "font: bold");
            bar.add(scopeTitle);
        }
        bar.add(scopeDetail);
        return bar;
    }

    private JComponent body() {
        JPanel drawing = new JPanel(new BorderLayout());
        drawing.add(canvas, BorderLayout.CENTER);
        messages.setBorder(new EdgeBorder(Set.of(EdgeBorder.Side.TOP), new Insets(0, 0, 0, 0)));
        drawing.add(messages, BorderLayout.SOUTH);
        JPanel body = new JPanel(new MigLayout("insets 0, gap 0, fill", "[grow, fill][" + SIDE_WIDTH + "!, fill]",
                "[grow, fill]"));
        body.add(drawing);
        body.add(sidePanel());
        return body;
    }

    private JComponent sidePanel() {
        JPanel side = new Surface(new MigLayout("wrap 1, insets 0, gap 0, fillx, hidemode 3", "[grow, fill]"));
        side.setBorder(new EdgeBorder(Set.of(EdgeBorder.Side.LEFT), new Insets(0, 0, 0, 0)));
        side.add(sectionTitle(Messages.text("shape.pins")), "gap 10 10 8 4");
        JScrollPane pinScroll = new JScrollPane(pins);
        pinScroll.setBorder(BorderFactory.createEmptyBorder());
        pinScroll.setColumnHeaderView(pins.getTableHeader());
        side.add(pinScroll, "hmin 60, shrinky 100");
        pins.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !refreshing && pins.getSelectedRow() >= 0) {
                canvas.selectPin(request.definition().pinout().get(pins.getSelectedRow()).source());
                refresh();
                // The arrows move the pin that was just chosen: the keys must reach the canvas, not the table.
                SwingUtilities.invokeLater(this::focusCanvas);
            }
        });
        side.add(sectionTitle(Messages.text("shape.size")), "gap 10 10 10 2");
        side.add(sizeRow());
        pinNames.setIcon(new SwitchIcon());
        pinNames.setOpaque(false);
        pinNames.setIconTextGap(8);
        pinNames.addActionListener(event -> {
            if (!refreshing) {
                apply(draft.showPinNames(pinNames.isSelected()));
            }
        });
        side.add(pinNames, "gap 10 10 6 2");
        side.add(textGroup());
        side.add(sectionTitle(Messages.text("shape.preview")), "gap 10 10 10 2");
        previews.setOpaque(false);
        previews.add(new PreviewCard(false));
        previews.add(new PreviewCard(true));
        side.add(previews);
        side.add(new Spacer(), "pushy, growy");
        return side;
    }

    private JComponent sizeRow() {
        JPanel row = new JPanel(new MigLayout("insets 2 10 2 10, gap 6", "[][]12[][]"));
        row.setOpaque(false);
        row.add(plainLabel(Messages.text("shape.width")));
        row.add(compact(width));
        row.add(plainLabel(Messages.text("shape.height")));
        row.add(compact(height));
        width.addChangeListener(event -> resize());
        height.addChangeListener(event -> resize());
        return row;
    }

    /** A spinner as narrow as the mockups' («Ancho [6] Alto [8]» on one line): three digits wide. */
    private static JSpinner compact(JSpinner spinner) {
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setColumns(SPINNER_COLUMNS);
        }
        spinner.putClientProperty("JComponent.minimumWidth", 0);
        return spinner;
    }

    private JComponent textGroup() {
        textGroup.setOpaque(false);
        textGroup.add(sectionTitle(Messages.text("shape.textGroup")), "span 2, gap 0 0 10 2");
        textGroup.add(plainLabel(Messages.text("shape.text.size")));
        textGroup.add(textSizes);
        textGroup.add(plainLabel(Messages.text("shape.text.alignment")));
        textGroup.add(textAlignments);
        componentName.setOpaque(false);
        textGroup.add(componentName, "span 2");
        textSizes.addActionListener(event -> editSelectedText(text -> new SymbolText(text.anchor(), text.text(),
                TextSize.values()[textSizes.getSelectedIndex()], text.alignment())));
        textAlignments.addActionListener(event -> editSelectedText(text -> new SymbolText(text.anchor(), text.text(),
                text.size(), TextAlignment.values()[textAlignments.getSelectedIndex()])));
        componentName.addActionListener(event -> editSelectedText(text -> new SymbolText(text.anchor(),
                componentName.isSelected() ? Optional.empty() : Optional.of(request.definition().name()),
                text.size(), text.alignment())));
        return textGroup;
    }

    private JComponent footer() {
        JPanel footer = new JPanel(new MigLayout("insets 8 12 8 12, gap 8", "[grow][][]", "[]"));
        footer.setBorder(new EdgeBorder(Set.of(EdgeBorder.Side.TOP), new Insets(0, 0, 0, 0)));
        footer.add(status, "growx, wmin 0");
        JButton cancel = new JButton(Messages.text("shape.cancel"));
        cancel.addActionListener(event -> onClose.run());
        accept.addActionListener(event -> acceptEdits());
        footer.add(cancel, "sg choice");
        footer.add(accept, "sg choice");
        return footer;
    }

    private static ToneLabel sectionTitle(String text) {
        ToneLabel title = new ToneLabel(ToneLabel.Tone.INFO);
        title.setText(text.toUpperCase(Messages.locale()));
        title.putClientProperty("FlatLaf.style", "font: bold -1");
        return title;
    }

    private static ToneLabel plainLabel(String text) {
        ToneLabel label = new ToneLabel(ToneLabel.Tone.PLAIN);
        label.setText(text);
        return label;
    }

    // --- Toolbar ------------------------------------------------------------------------------------------------

    private void buildToolbar() {
        toolbar.setFloatable(false);
        toolbar.setBorder(new EdgeBorder(Set.of(EdgeBorder.Side.BOTTOM), new Insets(3, 8, 3, 8)));
        ButtonGroup tools = new ButtonGroup();
        addTools(tools, ShapeTool.SELECT, ShapeTool.NODES, ShapeTool.PAN);
        toolbar.addSeparator();
        addTools(tools, ShapeTool.LINE, ShapeTool.MULTILINE, ShapeTool.RECTANGLE, ShapeTool.ELLIPSE,
                ShapeTool.TRIANGLE, ShapeTool.POLYGON, ShapeTool.TEXT);
        toolbar.addSeparator();
        addTools(tools, ShapeTool.ADD_NODE, ShapeTool.REMOVE_NODE, ShapeTool.CURVE);
        actionButton("closePath", icon(ShapeEditorIcons.CLOSE_PATH), this::toggleClosed);
        actionButton("fill", icon(ShapeEditorIcons.FILL), this::toggleFill);
        toolbar.addSeparator();
        actionButton("shapes", icon(ShapeEditorIcons.TEMPLATES), this::showTemplates);
        toolbar.addSeparator();
        actionButton("front", icon(ShapeEditorIcons.FRONT), () -> apply(draft.bringToFront()));
        actionButton("back", icon(ShapeEditorIcons.BACK), () -> apply(draft.sendToBack()));
        toolbar.addSeparator();
        addMagnet();
        toolbar.addSeparator();
        actionButton("undo", icon("undo"), this::undo);
        actionButton("redo", icon("redo"), this::redo);
        toolbar.addSeparator();
        actionButton("zoomOut", icon("zoom-out"), () -> canvas.setZoom(canvas.zoom() / ZOOM_STEP));
        addZoomValue();
        actionButton("zoomIn", icon("zoom-in"), () -> canvas.setZoom(canvas.zoom() * ZOOM_STEP));
    }

    private void addTools(ButtonGroup group, ShapeTool... tools) {
        for (ShapeTool tool : tools) {
            JToggleButton button = new JToggleButton(toolIcon(tool));
            button.setToolTipText(ShapeEditorTexts.toolTip(tool));
            button.getAccessibleContext().setAccessibleName(ShapeEditorTexts.toolName(tool));
            button.setFocusable(false);
            button.addActionListener(event -> chooseTool(tool));
            button.setSelected(tool == activeTool);
            group.add(button);
            toolbar.add(button);
            toolButtons.put(tool, button);
            bindCanvas(KeyStroke.getKeyStroke(tool.key()), button::doClick);
        }
    }

    private void chooseTool(ShapeTool tool) {
        activeTool = tool;
        canvas.setTool(tool);
        focusCanvas();
        refresh();
    }

    private void addMagnet() {
        JToggleButton magnet = new JToggleButton(icon("snap"));
        magnet.setSelected(preferences.shapeEditorSnap());
        magnet.setToolTipText(ShapeEditorTexts.twoLines(Messages.format("shape.tool.tooltip",
                Messages.text("shape.snap"), "M"), Messages.text("shape.snap.hint")));
        magnet.getAccessibleContext().setAccessibleName(Messages.text("shape.snap"));
        magnet.setFocusable(false);
        magnet.addActionListener(event -> {
            preferences.setShapeEditorSnap(magnet.isSelected());
            canvas.setSnap(magnet.isSelected());
            canvas.requestFocusInWindow();
        });
        toolbar.add(magnet);
        bindCanvas(KeyStroke.getKeyStroke("M"), magnet::doClick);
    }

    private void addZoomValue() {
        zoomValue.putClientProperty("JButton.buttonType", "toolBarButton");
        zoomValue.putClientProperty("FlatLaf.style", "font: -1");
        zoomValue.getAccessibleContext().setAccessibleName(Messages.text("shape.zoomReset"));
        zoomValue.setFocusable(false);
        zoomValue.addActionListener(event -> {
            canvas.setZoom(1);
            canvas.requestFocusInWindow();
        });
        toolbar.add(zoomValue);
    }

    private void actionButton(String name, Icon icon, Runnable action) {
        JButton button = new JButton(icon);
        button.getAccessibleContext().setAccessibleName(Messages.text(MESSAGE_PREFIX + name));
        button.setFocusable(false);
        button.addActionListener(event -> {
            action.run();
            canvas.requestFocusInWindow();
        });
        buttons.put(name, button);
        toolbar.add(button);
    }

    private static Icon toolIcon(ShapeTool tool) {
        return icon(switch (tool) {
            case SELECT -> "select";
            case NODES -> ShapeEditorIcons.NODES;
            case PAN -> "pan";
            case LINE -> ShapeEditorIcons.LINE;
            case MULTILINE -> ShapeEditorIcons.MULTILINE;
            case RECTANGLE -> ShapeEditorIcons.RECTANGLE;
            case ELLIPSE -> ShapeEditorIcons.ELLIPSE;
            case TRIANGLE -> ShapeEditorIcons.TRIANGLE;
            case POLYGON -> ShapeEditorIcons.POLYGON;
            case TEXT -> ShapeEditorIcons.TEXT;
            case ADD_NODE -> ShapeEditorIcons.NODE_ADD;
            case REMOVE_NODE -> ShapeEditorIcons.NODE_REMOVE;
            case CURVE -> ShapeEditorIcons.CURVE_SEGMENT;
        });
    }

    private static Icon icon(String name) {
        return Icons.get(name, TOOL_ICON_SIZE);
    }

    private void showTemplates() {
        JPopupMenu popup = ShapeTemplatesPopup.create(draft.appearance().size(), scheme,
                template -> apply(draft.applyTemplate(template)));
        popup.show(buttons.get("shapes"), 0, buttons.get("shapes").getHeight());
    }

    /** «Cerrar / Abrir figura»: every chosen figure of three nodes or more, closed if it was open and the other way. */
    private void toggleClosed() {
        SymbolDraft edited = draft;
        for (int index : draft.selection()) {
            if (draft.elements().get(index) instanceof SymbolPath path && path.nodes().size() >= CLOSABLE_NODES) {
                edited = path.closed() ? edited.openPath(index) : edited.closePath(index);
            }
        }
        apply(edited);
    }

    private void toggleFill() {
        SymbolDraft edited = draft;
        for (int index : draft.selection()) {
            if (draft.elements().get(index) instanceof SymbolPath path && path.closed()) {
                edited = edited.setFilled(index, !path.filled());
            }
        }
        apply(edited);
    }

    // --- Keys ---------------------------------------------------------------------------------------------------

    private void acceptEdits() {
        if (canvas.operationActive()) {
            canvas.finishOperation();
        }
        if (!canvas.operationActive() && canAccept()) {
            onAccept.run();
        }
    }

    private boolean acceptable() {
        return canAccept() && hasChanges();
    }

    private void bindKeys() {
        int menu = ShortcutCatalog.menuMask();
        bind(KeyStroke.getKeyStroke("ESCAPE"), "escape", () -> {
            switch (ShapeKeys.escape(canvas.operationActive(), canvas.hasSelection())) {
                case CANCEL_OPERATION -> canvas.escapeOperation();
                case CLEAR_SELECTION -> canvas.clearSelection();
                default -> onClose.run();
            }
        });
        bind(KeyStroke.getKeyStroke("ENTER"), "enter", () -> {
            if (ShapeKeys.enter(canvas.operationActive()) == ShapeKeys.Intent.FINISH_OPERATION) {
                canvas.finishOperation();
            } else if (acceptable()) {
                onAccept.run();
            }
        });
        // The selection keys belong to the canvas: a text, a spinner or a combo box that has the focus keeps them.
        bindSelectionKey(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", canvas::deleteSelection);
        bindSelectionKey(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "backspace", canvas::deleteSelection);
        int[][] arrows = {{KeyEvent.VK_LEFT, -1, 0}, {KeyEvent.VK_RIGHT, 1, 0}, {KeyEvent.VK_UP, 0, -1},
                {KeyEvent.VK_DOWN, 0, 1}};
        for (int[] arrow : arrows) {
            bindSelectionKey(KeyStroke.getKeyStroke(arrow[0], 0), "arrow" + arrow[0],
                    () -> canvas.moveSelection(arrow[1], arrow[2]));
            bindSelectionKey(KeyStroke.getKeyStroke(arrow[0], InputEvent.SHIFT_DOWN_MASK), "farArrow" + arrow[0],
                    () -> canvas.moveSelectionFar(arrow[1], arrow[2]));
        }
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu), "undo", this::undo);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu), "redo", this::redo);
        // The main window's redo stroke works here too, so the hand does not have to switch habits.
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu | InputEvent.SHIFT_DOWN_MASK), "redoAgain", this::redo);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_D, menu), "duplicate", () -> apply(draft.duplicateSelection()));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_A, menu), "selectAll", () -> apply(draft.selectAll()));
    }

    private void bindSelectionKey(KeyStroke stroke, String name, Runnable action) {
        bind(stroke, name, () -> {
            if (canvasOwnsSelectionKeys()) {
                action.run();
            }
        });
    }

    /** False while a text field, spinner or combo box has the focus: their arrows and deletes are theirs. */
    private boolean canvasOwnsSelectionKeys() {
        Component owner = focusOwner.get();
        return owner == null || !(owner instanceof JTextComponent || owner instanceof JComboBox<?>
                || SwingUtilities.getAncestorOfClass(JSpinner.class, owner) != null);
    }

    /** Test seam: who has the keyboard focus (the focus manager does not work on a window that is not showing). */
    void focusOwner(Supplier<Component> owner) {
        focusOwner = owner;
    }

    private void focusCanvas() {
        canvasFocusRequests++;
        canvas.requestFocusInWindow();
    }

    int canvasFocusRequests() {
        return canvasFocusRequests;
    }

    private void bind(KeyStroke stroke, String name, Runnable action) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, "shape." + name);
        getActionMap().put("shape." + name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void bindCanvas(KeyStroke stroke, Runnable action) {
        String name = "shape.canvas." + stroke;
        canvas.getInputMap(WHEN_FOCUSED).put(stroke, name);
        canvas.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    // --- Edits --------------------------------------------------------------------------------------------------

    private boolean compatibleGeometry() {
        if (!draft.appearance().size().equals(request.definition().size())) {
            return false;
        }
        return request.definition().pinout().stream().allMatch(pin -> draft.appearance().pins().stream().anyMatch(
                placement -> placement.source().equals(pin.source()) && placement.offset().equals(pin.spec().position())));
    }

    private boolean locked(ShapeEditScope candidate) {
        return candidate != ShapeEditScope.INSTANCE && request.definitionPinsLocked();
    }

    private void preview(SymbolDraft changed) {
        draft = changed;
        refresh();
    }

    private void resize() {
        if (refreshing || draft.pinsLocked()) {
            return;
        }
        try {
            apply(draft.resizeBox(new GridSize((Integer) width.getValue(), (Integer) height.getValue())));
        } catch (IllegalArgumentException exception) {
            notice = Optional.of(Messages.text("shape.resize.blocked"));
            refresh();
        }
    }

    private void editSelectedText(UnaryOperator<SymbolText> change) {
        if (refreshing) {
            return;
        }
        SymbolDraft edited = draft;
        for (int index : draft.selection()) {
            if (draft.elements().get(index) instanceof SymbolText text) {
                edited = edited.editText(index, change.apply(text));
            }
        }
        apply(edited);
    }

    private Optional<SymbolText> selectedText() {
        return draft.selection().stream().sorted().map(index -> draft.elements().get(index))
                .filter(SymbolText.class::isInstance).map(SymbolText.class::cast).findFirst();
    }

    // --- Refresh ------------------------------------------------------------------------------------------------

    private void refresh() {
        if (width == null || refreshing) {
            return;
        }
        refreshing = true;
        try {
            refreshScope();
            refreshSize();
            refreshTextGroup();
            pins.show(pinRows());
            syncPinSelection();
            refreshMessages();
            refreshFooter();
            refreshToolbar();
        } finally {
            refreshing = false;
        }
        canvas.repaint();
        previews.repaint();
    }

    private void refreshScope() {
        for (int index = 0; index < scopeSegments.size(); index++) {
            scopeSegments.get(index).setSelected((index == 0) == (scope == ShapeEditScope.INSTANCE));
        }
        scopeDetail.setText(ShapeEditorTexts.scopeDetail(request, scope));
    }

    private void refreshSize() {
        width.setValue(draft.appearance().size().width());
        height.setValue(draft.appearance().size().height());
        if (!draft.pinsLocked()) {
            GridSize minimum = draft.minimumBoxSize();
            ((SpinnerNumberModel) width.getModel()).setMinimum(minimum.width());
            ((SpinnerNumberModel) height.getModel()).setMinimum(minimum.height());
        }
        width.setEnabled(!draft.pinsLocked());
        height.setEnabled(!draft.pinsLocked());
        pinNames.setSelected(draft.appearance().drawing().map(drawing -> drawing.showPinNames()).orElse(true));
    }

    private void refreshTextGroup() {
        Optional<SymbolText> text = selectedText();
        if (textGroup.isVisible() != text.isPresent()) {
            textGroup.setVisible(text.isPresent());
            revalidate();
        }
        text.ifPresent(chosen -> {
            textSizes.setSelectedIndex(chosen.size().ordinal());
            textAlignments.setSelectedIndex(chosen.alignment().ordinal());
            componentName.setSelected(chosen.text().isEmpty());
        });
    }

    private List<PinTable.Row> pinRows() {
        List<DraftProblem> problems = draft.problems();
        List<PinTable.Row> rows = new ArrayList<>();
        for (DefinitionPin pin : request.definition().pinout()) {
            String name = pin.spec().name();
            String direction = Messages.text(MESSAGE_PREFIX + "direction."
                    + pin.spec().direction().name().toLowerCase(Locale.ROOT));
            ToneLabel.Tone tone = ToneLabel.Tone.PLAIN;
            String problem = "";
            for (DraftProblem candidate : problems) {
                if (!candidate.pins().contains(name)) {
                    continue;
                }
                if (candidate.kind() == DraftProblem.Kind.COINCIDING_PINS) {
                    List<String> others = candidate.pins().stream().filter(other -> !other.equals(name)).toList();
                    tone = ToneLabel.Tone.BLOCKING;
                    problem = Messages.format("shape.pin.coinciding", ShapeEditorTexts.names(others));
                    break;
                }
                tone = ToneLabel.Tone.WARNING;
                problem = Messages.text("shape.pin.offEdge");
            }
            rows.add(new PinTable.Row(name, direction, pin.spec().width(), tone, problem, draft.pinsLocked()));
        }
        return rows;
    }

    private void syncPinSelection() {
        Optional<Integer> row = canvas.selectedPin().flatMap(source -> {
            List<DefinitionPin> pinout = request.definition().pinout();
            for (int index = 0; index < pinout.size(); index++) {
                if (pinout.get(index).source().equals(source)) {
                    return Optional.of(index);
                }
            }
            return Optional.empty();
        });
        if (row.isPresent()) {
            if (pins.getSelectedRow() != row.get()) {
                pins.setRowSelectionInterval(row.get(), row.get());
            }
        } else if (pins.getSelectedRow() >= 0) {
            pins.clearSelection();
        }
    }

    /** The lines under the canvas: a notice, the lock, the warnings; the tool's hint when there is nothing to warn. */
    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        notice.ifPresent(text -> lines.add(new Line(ToneLabel.Tone.WARNING, text)));
        canvas.refusal().ifPresent(text -> lines.add(new Line(ToneLabel.Tone.WARNING, text)));
        if (draft.pinsLocked()) {
            lines.add(new Line(ToneLabel.Tone.WARNING, Messages.format("shape.locked", request.lockReason())));
        }
        List<String> offEdge = draft.problems().stream()
                .filter(problem -> problem.kind() == DraftProblem.Kind.PIN_OFF_EDGE)
                .flatMap(problem -> problem.pins().stream()).toList();
        if (!offEdge.isEmpty()) {
            lines.add(new Line(ToneLabel.Tone.WARNING,
                    ShapeEditorTexts.problem(DraftProblem.Kind.PIN_OFF_EDGE, offEdge)));
        }
        draft.problems().stream().filter(problem -> problem.kind() == DraftProblem.Kind.EMPTY_DRAWING).findFirst()
                .ifPresent(problem -> lines.add(new Line(ToneLabel.Tone.WARNING,
                        ShapeEditorTexts.problem(problem.kind(), problem.pins()))));
        if (lines.isEmpty()) {
            lines.add(new Line(ToneLabel.Tone.INFO, hintText()));
        }
        return lines;
    }

    private void refreshMessages() {
        List<Line> lines = lines();
        if (lines.equals(shownLines)) {
            return;
        }
        shownLines = lines;
        messages.removeAll();
        for (Line line : lines) {
            ToneLabel label = new ToneLabel(line.tone());
            label.show(line.tone(), line.text());
            label.setIconTextGap(6);
            messages.add(label, "wmin 0");
        }
        messages.revalidate();
        messages.repaint();
    }

    private void refreshFooter() {
        List<DraftProblem> problems = draft.problems();
        boolean blocked = problems.stream().anyMatch(DraftProblem::blocksAccept);
        status.show(blocked ? ToneLabel.Tone.BLOCKING : ToneLabel.Tone.INFO,
                ShapeEditorTexts.status(problems, hasChanges()));
        if (!blocked) {
            status.setIcon(null);
        }
        accept.setEnabled(acceptable());
    }

    private void refreshToolbar() {
        if (buttons.isEmpty()) {
            return;
        }
        String undoName = Messages.format("shape.tool.tooltip", Messages.text("shape.undo"),
                ShapeEditorTexts.shortcut(KeyEvent.VK_Z));
        String redoName = Messages.format("shape.tool.tooltip", Messages.text("shape.redo"),
                ShapeEditorTexts.shortcut(KeyEvent.VK_Y));
        contextual("undo", undoName, history.canUndo(), "shape.undo.none");
        contextual("redo", redoName, history.canRedo(), "shape.redo.none");


        boolean closedFigure = draft.selection().stream().map(index -> draft.elements().get(index))
                .anyMatch(element -> element instanceof SymbolPath path && path.closed());
        contextual("fill", Messages.text("shape.fill"), closedFigure, "shape.closedFigure.required");
        boolean closable = draft.selection().stream().map(index -> draft.elements().get(index))
                .anyMatch(element -> element instanceof SymbolPath path && path.nodes().size() >= CLOSABLE_NODES);
        contextual("closePath", Messages.text("shape.closePath"), closable, "shape.closable.required");
        boolean anyFigure = !draft.selection().isEmpty();
        contextual("front", Messages.text("shape.front"), anyFigure, "shape.figure.required");
        contextual("back", Messages.text("shape.back"), anyFigure, "shape.figure.required");
        plain("shapes", Messages.text("shape.shapes"));
        plain("zoomOut", Messages.text("shape.zoomOut"));
        plain("zoomIn", Messages.text("shape.zoomIn"));
        int percent = (int) Math.round(canvas.zoom() * 100);
        zoomValue.setText(Messages.format("shape.zoom.text", percent));
        zoomValue.setToolTipText(Messages.format("shape.zoom.value", percent));
        toolButtons.get(activeTool).setSelected(true);
    }

    private void contextual(String name, String tooltip, boolean enabled, String reasonKey) {
        AbstractButton button = buttons.get(name);
        button.setEnabled(enabled);
        button.setToolTipText(enabled ? tooltip : Messages.format("shape.disabled", tooltip, Messages.text(reasonKey)));
    }

    private void plain(String name, String tooltip) {
        buttons.get(name).setToolTipText(tooltip);
    }

    // --- Accessors for tests ------------------------------------------------------------------------------------

    ShapeCanvas canvas() {
        return canvas;
    }

    PinTable pinTable() {
        return pins;
    }

    JButton zoomValueButton() {
        return zoomValue;
    }

    JSpinner widthSpinner() {
        return width;
    }

    JSpinner heightSpinner() {
        return height;
    }

    List<String> scopeSwitchTexts() {
        return scopeSegments.stream().map(AbstractButton::getText).toList();
    }

    String scopeTitle() {
        return scopeTitle.getText();
    }

    String scopeDetail() {
        return scopeDetail.getText();
    }

    String statusText() {
        return status.getText();
    }

    Icon statusIcon() {
        return status.getIcon();
    }

    List<String> messageLines() {
        return shownLines.stream().map(Line::text).toList();
    }

    boolean textGroupVisible() {
        return textGroup.isVisible();
    }

    TextSize selectedTextSize() {
        return TextSize.values()[textSizes.getSelectedIndex()];
    }

    TextAlignment selectedTextAlignment() {
        return TextAlignment.values()[textAlignments.getSelectedIndex()];
    }

    String pinRowProblem(int row) {
        return pins.row(row).problem();
    }

    boolean pinRowLocked(int row) {
        return pins.row(row).locked();
    }

    String hintText() {
        return ShapeEditorTexts.hint(new ShapeEditorTexts.HintState(activeTool, canvas.selectedPin().isPresent(),
                draft.pinsLocked(), draft.selection().size(), canvas.nodeSelected(),
                draft.appearance().drawing().isPresent(), canvas.boxHandleActive()));
    }

    /** «Actual: Claro» and the other theme's default scheme («Oscuro»), as the two preview cards say. */
    static List<String> previewCaptions(ColorScheme current) {
        String currentCaption = currentSchemeId(current)
                .map(id -> Messages.format("shape.preview.current", ColorSchemes.displayName(id)))
                .orElseGet(() -> Messages.text("shape.preview.currentUnnamed"));
        return List.of(currentCaption, ColorSchemes.displayName(otherThemeId(current)));
    }

    private static Optional<String> currentSchemeId(ColorScheme current) {
        return ColorSchemes.ids().stream()
                .filter(id -> ColorSchemes.byId(id).filter(current::equals).isPresent()).findFirst();
    }

    private static String otherThemeId(ColorScheme current) {
        return current.isDark() ? ColorSchemes.LIGHT_ID : ColorSchemes.DARK_ID;
    }

    /** A line under the canvas. */
    private record Line(ToneLabel.Tone tone, String text) { }

    /** A band in the theme's surface colour (white on FlatLaf Light), like the mockups' side panel and scope line. */
    private static class Surface extends JPanel {
        Surface(MigLayout layout) {
            super(layout);
        }

        @Override
        public Color getBackground() {
            Color surface = UIManager.getColor("Table.background");
            return surface != null ? surface : super.getBackground();
        }
    }

    /** Takes the side panel's spare height, so the sections stay at the top. */
    private static final class Spacer extends JPanel {
        Spacer() {
            setOpaque(false);
        }
    }

    /**
     * A preview card: a caption strip («Actual: Claro» / «Oscuro») over the component at real size (100 %), or
     * smaller when it does not fit, painted with the same renderer as the canvas on that scheme's background.
     */
    private final class PreviewCard extends JPanel {
        private static final int CAPTION_HEIGHT = 18;
        private static final int ARC = 8;
        private static final int MARGIN = 14;
        private final boolean otherTheme;

        PreviewCard(boolean otherTheme) {
            this.otherTheme = otherTheme;
            setOpaque(false);
            setPreferredSize(new Dimension(100, PREVIEW_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            try {
                surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                ColorScheme current = scheme.get();
                ColorScheme shown = otherTheme
                        ? ColorSchemes.byId(otherThemeId(current)).orElseThrow() : current;
                RoundRectangle2D card = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0,
                        ARC, ARC);
                surface.setClip(card);
                surface.setColor(ReadableColors.panelBackground());
                surface.fillRect(0, 0, getWidth(), CAPTION_HEIGHT);
                surface.setColor(shown.background());
                surface.fillRect(0, CAPTION_HEIGHT, getWidth(), getHeight() - CAPTION_HEIGHT);
                paintCaption(surface, previewCaptions(current).get(otherTheme ? 1 : 0));
                paintComponentShape(surface, shown);
                surface.setClip(null);
                Color border = UIManager.getColor("Component.borderColor");
                surface.setColor(border != null ? border : Color.GRAY);
                surface.draw(card);
            } finally {
                surface.dispose();
            }
        }

        private void paintCaption(Graphics2D surface, String caption) {
            surface.setFont(getFont().deriveFont(getFont().getSize2D() - 2));
            surface.setColor(ReadableColors.mutedText());
            FontMetrics metrics = surface.getFontMetrics();
            surface.drawString(caption, 6, (CAPTION_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2);
        }

        private void paintComponentShape(Graphics2D surface, ColorScheme shown) {
            ComponentAppearance appearance = draft.appearance();
            double boxWidth = appearance.size().width() * GridPoint.PIXELS_PER_UNIT;
            double boxHeight = appearance.size().height() * GridPoint.PIXELS_PER_UNIT;
            double room = getHeight() - CAPTION_HEIGHT;
            double scale = Math.min(1, Math.min((getWidth() - 2.0 * MARGIN) / boxWidth,
                    (room - 2.0 * MARGIN) / boxHeight));
            Graphics2D shape = (Graphics2D) surface.create();
            try {
                shape.translate((getWidth() - boxWidth * scale) / 2, CAPTION_HEIGHT + (room - boxHeight * scale) / 2);
                shape.scale(scale, scale);
                ShapeCanvas.paintAppearance(shape, request.definition(), appearance, shown, false);
            } finally {
                shape.dispose();
            }
        }
    }
}
